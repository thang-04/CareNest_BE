package com.carenest.service.meals.impl;

import com.carenest.dto.meals.MealPlanResponse.Issue;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.Norm;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import com.carenest.service.meals.impl.MealPlanEvaluator.Range;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Dựng prompt gợi ý thực đơn (bước 4, 7b): chỉ dữ liệu danh mục công khai (món, định mức, giá), không dữ liệu trẻ
 * (AI-04). Món gửi dưới bí danh {@code D1..Dn} theo id tăng dần ⇒ model không bịa được id thật, ít token.
 */
final class MenuAiPromptBuilder {

    static final int WISHES_MAX = 300;
    static final int SEASON_MAX = 50;
    private static final int NAME_MAX = 40;
    private static final int ISSUE_MAX = 160;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Bí danh hai chiều của các món ứng viên. */
    record Aliases(Map<String, Long> toDish, Map<Long, String> toAlias) {}

    private MenuAiPromptBuilder() {}

    static Aliases aliases(MenuContext ctx) {
        Set<Long> ids = new TreeSet<>();
        ctx.candidates().values().forEach(ids::addAll);
        Map<String, Long> toDish = new LinkedHashMap<>();
        Map<Long, String> toAlias = new LinkedHashMap<>();
        int n = 1;
        for (Long id : ids) {
            String alias = "D" + n++;
            toDish.put(alias, id);
            toAlias.put(id, alias);
        }
        return new Aliases(Map.copyOf(toDish), Map.copyOf(toAlias));
    }

    static String weeklyUserPrompt(MenuContext ctx, Aliases aliases) {
        StringBuilder sb = new StringBuilder();
        sb.append("Khối: ").append(blockLabel(ctx.block())).append('\n');
        sb.append("Ngày cần lập: ")
                .append(ctx.servedDates().stream().map(LocalDate::toString).collect(Collectors.joining(", ")))
                .append('\n');
        appendCatalog(sb, ctx, aliases);
        String recent = ctx.recentDishIds().stream()
                .filter(aliases.toAlias()::containsKey)
                .sorted()
                .map(aliases.toAlias()::get)
                .collect(Collectors.joining(", "));
        if (!recent.isEmpty()) {
            sb.append("Món vừa dùng gần đây: ").append(recent).append('\n');
        }
        appendConstraints(sb, ctx, aliases);
        String season = clean(ctx.season(), SEASON_MAX);
        if (!season.isEmpty()) {
            sb.append("Mùa: ").append(season);
            if (ctx.signals().seasonTemplate() != null) {
                sb.append(" (món ghi \"hợp mùa\" lấy từ bộ thực đơn mẫu mùa này của trường)");
            }
            sb.append('\n');
        }
        String wishes = clean(ctx.wishes(), WISHES_MAX);
        if (!wishes.isEmpty()) {
            // Mong muốn là chữ người dùng nhập: bọc riêng để model coi là dữ liệu, không phải lệnh
            sb.append("Mong muốn của người lập (chỉ là ưu tiên về món):\n<<<\n")
                    .append(wishes)
                    .append("\n>>>\n");
        }
        return sb.toString();
    }

    /** Lần trước sai cấu trúc ⇒ gửi lại đủ đề kèm lỗi gọn. */
    static String retryUserPrompt(MenuContext ctx, Aliases aliases, String error) {
        return weeklyUserPrompt(ctx, aliases) + "Lần trả lời trước không dùng được: " + clean(error, ISSUE_MAX)
                + ". Trả lại đủ các ngày, đúng dạng JSON.\n";
    }

    /** Vòng sửa: phương án hiện tại + lỗi gọn theo ngày, chỉ yêu cầu sửa các ngày đó. */
    static String repairUserPrompt(
            MenuContext ctx, Aliases aliases, MenuGrid grid, Map<LocalDate, List<Issue>> blockingByDay) {
        StringBuilder sb = new StringBuilder();
        sb.append("Khối: ").append(blockLabel(ctx.block())).append('\n');
        appendCatalog(sb, ctx, aliases);
        sb.append("Phương án hiện tại:\n");
        for (LocalDate date : ctx.servedDates()) {
            sb.append(date).append(": ");
            sb.append(ctx.slots().stream()
                    .map(s -> s.key() + "=" + aliases.toAlias().getOrDefault(grid.dish(date, s.key()), "?"))
                    .collect(Collectors.joining(", ")));
            sb.append('\n');
        }
        sb.append("Lỗi cần sửa:\n");
        blockingByDay.forEach((date, issues) -> issues.forEach(i -> sb.append("- ")
                .append(date)
                .append(": ")
                .append(clean(i.message(), ISSUE_MAX))
                .append('\n')));
        sb.append("Chỉ trả các ngày: ")
                .append(blockingByDay.keySet().stream().map(LocalDate::toString).collect(Collectors.joining(", ")))
                .append('\n');
        return sb.toString();
    }

    /** Thay một món: món đang có trong ngày (tên, không bí danh) + món cùng loại được phép dưới bí danh. */
    static String dayFixUserPrompt(MenuContext ctx, Aliases aliases, MenuGrid grid, LocalDate date, MenuSlot slot) {
        StringBuilder sb = new StringBuilder();
        sb.append("Khối: ").append(blockLabel(ctx.block())).append('\n');
        sb.append("Ngày: ").append(date).append('\n');
        appendCatalog(sb, ctx, aliases);
        sb.append("Món đang có trong ngày:\n");
        for (MenuSlot s : ctx.slots()) {
            Long dishId = grid.dish(date, s.key());
            if (dishId != null && ctx.dishes().containsKey(dishId)) {
                sb.append("- bữa ")
                        .append(s.session())
                        .append(": ")
                        .append(clean(ctx.dishes().get(dishId).name(), NAME_MAX))
                        .append(s.key().equals(slot.key()) ? "  ← cần thay" : "")
                        .append('\n');
            }
        }
        sb.append("Chọn MỘT món trong ô ").append(slot.key()).append(" để thay.\n");
        return sb.toString();
    }

    // Ràng buộc của người lập: code tự áp lại sau khi đọc output, nêu ra để model chọn các ô còn lại cho hợp
    private static void appendConstraints(StringBuilder sb, MenuContext ctx, Aliases aliases) {
        String required = ctx.requiredDishIds().stream()
                .filter(aliases.toAlias()::containsKey)
                .sorted()
                .map(aliases.toAlias()::get)
                .collect(Collectors.joining(", "));
        if (!required.isEmpty()) {
            sb.append("Món bắt buộc có ít nhất một lần trong tuần: ")
                    .append(required)
                    .append('\n');
        }
        if (!ctx.keepTemplate()) {
            return;
        }
        sb.append("Giữ nguyên theo thực đơn mẫu của trường, chỉ chọn món cho các ô còn lại:\n");
        for (LocalDate date : ctx.servedDates()) {
            String kept = ctx.slots().stream()
                    .filter(s -> MenuPlanConstraints.pinned(ctx, date, s) != null)
                    .map(s -> s.key() + "=" + aliases.toAlias().get(MenuPlanConstraints.pinned(ctx, date, s)))
                    .collect(Collectors.joining(", "));
            if (!kept.isEmpty()) {
                sb.append("- ").append(date).append(": ").append(kept).append('\n');
            }
        }
    }

    private static void appendCatalog(StringBuilder sb, MenuContext ctx, Aliases aliases) {
        AgeGroupInfo reference = ctx.groups().getFirst();
        LocalDate firstDay = ctx.servedDates().isEmpty()
                ? ctx.dates().getFirst()
                : ctx.servedDates().getFirst();
        sb.append("Định mức mỗi trẻ mỗi ngày (tổng các bữa ở trường):\n");
        for (AgeGroupInfo group : ctx.groups()) {
            sb.append("- ").append(clean(group.name(), NAME_MAX)).append(": ");
            sb.append(normLine(ctx.normOf().apply(group.id(), firstDay))).append('\n');
        }
        sb.append(
                        "Món (bí danh|loại|tên|kcal|đạm ĐV g|đạm TV g|béo ĐV g|béo TV g|bột đường g|giá đ|ghi chú), tính cho ")
                .append(clean(reference.name(), NAME_MAX))
                .append(":\n");
        aliases.toDish().entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> sb.append(dishLine(
                        e.getKey(),
                        ctx.dishes().get(e.getValue()),
                        reference.id(),
                        tags(e.getValue(), ctx),
                        ctx.priceOf(),
                        firstDay)));
        sb.append("Ô món mỗi ngày và món được phép:\n");
        for (MenuSlot slot : ctx.slots()) {
            if (ctx.candidatesOf(slot).isEmpty()) {
                continue;
            }
            sb.append(slot.key())
                    .append(" (")
                    .append(slot.types().stream().map(Enum::name).sorted().collect(Collectors.joining("/")))
                    .append("): ")
                    .append(ctx.candidatesOf(slot).stream()
                            .map(aliases.toAlias()::get)
                            .collect(Collectors.joining(",")))
                    .append('\n');
        }
    }

    private static String normLine(Norm norm) {
        if (norm == null) {
            return "chưa đặt";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("năng lượng ").append(range(norm.kcal())).append(" kcal");
        sb.append("; đạm ").append(range(norm.proteinPct())).append("%");
        sb.append(", béo ").append(range(norm.lipidPct())).append("%");
        sb.append(", bột đường ").append(range(norm.glucidPct())).append("% năng lượng");
        if (norm.mealPrice() != null) {
            sb.append("; giá suất ").append(plain(norm.mealPrice())).append("đ");
        }
        return sb.toString();
    }

    private static String dishLine(
            String alias,
            DishInfo dish,
            Long groupId,
            String tags,
            BiFunction<Long, LocalDate, BigDecimal> priceOf,
            LocalDate day) {
        List<Portion> portions = dish.byAgeGroup().getOrDefault(groupId, List.of());
        BigDecimal kcal = BigDecimal.ZERO;
        BigDecimal pA = BigDecimal.ZERO;
        BigDecimal pP = BigDecimal.ZERO;
        BigDecimal lA = BigDecimal.ZERO;
        BigDecimal lP = BigDecimal.ZERO;
        BigDecimal g = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (Portion p : portions) {
            BigDecimal ratio = p.grams().divide(HUNDRED, 10, RoundingMode.HALF_UP);
            kcal = kcal.add(p.food().kcal().multiply(ratio));
            g = g.add(p.food().glucid().multiply(ratio));
            if (p.food().origin() == FoodOrigin.ANIMAL) {
                pA = pA.add(p.food().protein().multiply(ratio));
                lA = lA.add(p.food().lipid().multiply(ratio));
            } else {
                pP = pP.add(p.food().protein().multiply(ratio));
                lP = lP.add(p.food().lipid().multiply(ratio));
            }
            BigDecimal unitPrice = priceOf.apply(p.food().id(), day);
            if (unitPrice == null) {
                unitPrice = p.food().unitPrice();
            }
            if (unitPrice != null
                    && p.food().purchaseUnitSize() != null
                    && p.food().purchaseUnitSize().signum() > 0) {
                cost = cost.add(unitPrice
                        .multiply(MealDemandCalculator.purchased(
                                p.grams(), p.food().wastePct()))
                        .divide(p.food().purchaseUnitSize(), 6, RoundingMode.HALF_UP));
            }
        }
        return String.join(
                        "|",
                        alias,
                        dish.type().name(),
                        clean(dish.name(), NAME_MAX),
                        round1(kcal),
                        round1(pA),
                        round1(pP),
                        round1(lA),
                        round1(lP),
                        round1(g),
                        cost.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                        tags)
                + "\n";
    }

    // Nhãn lấy từ dữ liệu của trường để model ưu tiên đúng: hợp mùa, quen thuộc, vừa dùng các tuần liền trước,
    // kho khô đang hết
    private static String tags(Long dishId, MenuContext ctx) {
        List<String> tags = new ArrayList<>();
        if (ctx.signals().seasonal().contains(dishId)) {
            tags.add("hợp mùa");
        }
        if (ctx.signals().familiar().contains(dishId)) {
            tags.add("trường đã dùng");
        }
        if (ctx.recentDishIds().contains(dishId)) {
            tags.add("vừa dùng gần đây");
        }
        if (ctx.signals().outOfStock().contains(dishId)) {
            tags.add("kho khô đang hết");
        }
        return String.join(", ", tags);
    }

    private static String range(Range range) {
        return range == null || !range.set() ? "?" : plain(range.min()) + "–" + plain(range.max());
    }

    private static String round1(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Bỏ ký tự điều khiển và ký hiệu khung prompt, gộp khoảng trắng, cắt độ dài. */
    static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("[\\p{Cntrl}<>{}|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max).trim();
    }

    static String blockLabel(MenuBlock block) {
        return block == MenuBlock.NURSERY ? "Nhà trẻ" : "Mẫu giáo";
    }
}
