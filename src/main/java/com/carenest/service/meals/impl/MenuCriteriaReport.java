package com.carenest.service.meals.impl;

import com.carenest.dto.meals.MealPlanResponse.Issue;
import com.carenest.dto.meals.MenuAiDraftResponse.Check;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiFallbackReason;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bảng kiểm tiêu chí của phương án được chọn: mỗi dòng nói tiêu chí gì, căn cứ rule nào, kết quả đo bằng code (không
 * lấy từ model). PHT xem để biết vì sao dùng được; đo giá trị AI theo cùng tiêu chí với phương án theo quy tắc.
 */
final class MenuCriteriaReport {

    static final String PASS = "PASS";
    static final String WARN = "WARN";
    static final String INFO = "INFO";
    static final String FAIL = "FAIL";
    static final String DRY_STOCK_OUT = "DRY_STOCK_OUT";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private MenuCriteriaReport() {}

    /** Cảnh báo món dùng thực phẩm kho khô đang hết ở mọi điểm trường (NUT-11): không chặn, trừ điểm cảnh báo. */
    static List<Issue> stockIssues(MenuGrid grid, MenuContext ctx) {
        List<Issue> issues = new ArrayList<>();
        Set<Long> reported = new LinkedHashSet<>();
        for (LocalDate date : ctx.servedDates()) {
            for (Long dishId : grid.cells().getOrDefault(date, Map.of()).values()) {
                if (ctx.signals().outOfStock().contains(dishId) && reported.add(dishId)) {
                    issues.add(new Issue(
                            MealPlanEvaluator.WARNING,
                            DRY_STOCK_OUT,
                            date,
                            null,
                            ctx.block(),
                            dishId,
                            MenuAiPromptBuilder.blockLabel(ctx.block()) + ": món "
                                    + ctx.dishes().get(dishId).name()
                                    + " dùng thực phẩm kho khô đang hết ở mọi điểm trường — nhập thêm trước "
                                    + date.format(DAY)));
                }
            }
        }
        return issues;
    }

    static List<Check> build(
            MenuGrid grid,
            List<Issue> issues,
            MenuContext ctx,
            MenuAiDraftSource source,
            MenuAiFallbackReason fallback,
            Integer aiScore,
            Integer baselineScore) {
        List<Check> checks = new ArrayList<>();
        List<LocalDate> served = ctx.servedDates();
        if (source == null) {
            checks.add(new Check(
                    "NORM_BALANCE",
                    FAIL,
                    "Cân đối năng lượng, đạm–béo–bột đường theo định mức nhóm tuổi",
                    "Không có phương án đạt: " + count(issues, MealPlanEvaluator.BLOCKING)
                            + " lỗi chặn (xem danh sách lỗi)",
                    List.of("NUT-09", "NUT-18", "NUT-24")));
            return checks;
        }
        Set<LocalDate> blockedDays = new TreeSet<>();
        issues.stream()
                .filter(i -> MealPlanEvaluator.BLOCKING.equals(i.severity()) && i.date() != null)
                .forEach(i -> blockedDays.add(i.date()));
        checks.add(new Check(
                "NORM_BALANCE",
                blockedDays.isEmpty() ? PASS : FAIL,
                "Cân đối năng lượng, đạm–béo–bột đường theo định mức nhóm tuổi (chặn nếu lệch)",
                (served.size() - blockedDays.size()) + "/" + served.size()
                        + " ngày đạt định mức mọi nhóm tuổi của khối; số liệu tính từ định lượng món và bảng thành phần"
                        + " thực phẩm",
                List.of("NUT-06", "NUT-18", "NUT-24")));
        checks.add(countCheck(
                issues, "SESSION_KCAL_OFF", "Tỷ lệ năng lượng từng bữa so nhu cầu cả ngày", List.of("NUT-25")));
        checks.add(countCheck(
                issues, "MICRONUTRIENT_OFF", "Vi chất (canxi, sắt, vitamin…) so định mức", List.of("NUT-26")));
        checks.add(countCheck(issues, "OVER_BUDGET", "Chi phí thực phẩm/trẻ trong giá suất", List.of("NUT-19")));
        checks.add(countCheck(issues, "DISH_REPEATED", "Món chính không lặp trong tuần", List.of("NUT-20")));
        checks.add(countCheck(issues, DRY_STOCK_OUT, "Thực phẩm kho khô còn hàng còn hạn", List.of("NUT-11")));

        List<Long> cells = new ArrayList<>();
        served.forEach(d -> cells.addAll(grid.cells().getOrDefault(d, Map.of()).values()));
        Set<Long> dishes = new TreeSet<>(cells);
        long recent = cells.stream().filter(ctx.recentDishIds()::contains).count();
        checks.add(new Check(
                "HISTORY",
                recent == 0 ? PASS : INFO,
                "Không lặp thực đơn 2 tuần trước",
                recent + "/" + cells.size() + " ô dùng lại món của 2 tuần trước (bị trừ điểm đa dạng)",
                List.of("NUT-20")));
        long familiar =
                dishes.stream().filter(ctx.signals().familiar()::contains).count();
        checks.add(new Check(
                "FAMILIAR",
                INFO,
                "Món trường đã nấu",
                familiar + "/" + dishes.size() + " món đã có trong thực đơn tuần trường công bố gần đây",
                List.of("NUT-13")));
        String seasonTemplate = ctx.signals().seasonTemplate();
        long seasonal =
                cells.stream().filter(ctx.signals().seasonal()::contains).count();
        checks.add(new Check(
                "SEASON",
                INFO,
                "Hợp mùa",
                seasonTemplate == null
                        ? "Chưa có bộ thực đơn mẫu trùng mùa đã chọn nên chưa đối chiếu được"
                        : seasonal + "/" + cells.size() + " ô dùng món có trong bộ thực đơn mẫu \"" + seasonTemplate
                                + "\" của trường",
                List.of("NUT-13")));
        checks.add(new Check(
                "CATALOG",
                PASS,
                "Món lấy từ danh mục của trường, đủ dữ liệu",
                "Mọi món đang dùng, có định lượng cho mọi nhóm tuổi của khối, thực phẩm đang dùng và có nguồn số liệu;"
                        + " AI chỉ chọn trong danh sách này, không tạo món hay số liệu",
                List.of("NUT-05", "NUT-09", "NUT-18")));
        Set<String> allergens = allergens(dishes, ctx.dishes());
        checks.add(new Check(
                "ALLERGENS",
                INFO,
                "Chất gây dị ứng trong tuần",
                allergens.isEmpty()
                        ? "Không có chất gây dị ứng đã khai báo"
                        : "Có " + String.join(", ", allergens)
                                + "; trẻ dị ứng nặng dùng món thay thế, thực đơn chung không đổi",
                List.of("NUT-09", "NUT-23")));
        checks.add(new Check(
                "VALUE_GATE",
                source == MenuAiDraftSource.AI ? PASS : INFO,
                "Cổng giá trị AI",
                gateDetail(source, fallback, aiScore, baselineScore),
                List.of("AI-03", "AI-07")));
        return checks;
    }

    private static Check countCheck(List<Issue> issues, String code, String label, List<String> rules) {
        long n = issues.stream().filter(i -> code.equals(i.code())).count();
        return new Check(
                code, n == 0 ? PASS : WARN, label, n == 0 ? "Đạt" : n + " cảnh báo (xem danh sách lỗi)", rules);
    }

    private static long count(List<Issue> issues, String severity) {
        return issues.stream().filter(i -> severity.equals(i.severity())).count();
    }

    private static Set<String> allergens(Set<Long> dishIds, Map<Long, DishInfo> dishes) {
        Set<String> names = new TreeSet<>();
        for (Long id : dishIds) {
            DishInfo dish = dishes.get(id);
            if (dish != null) {
                dish.byAgeGroup()
                        .values()
                        .forEach(ps -> ps.stream().map(Portion::food).forEach(f -> names.addAll(f.allergens())));
            }
        }
        return names;
    }

    private static String gateDetail(
            MenuAiDraftSource source, MenuAiFallbackReason fallback, Integer aiScore, Integer baselineScore) {
        String scores = "điểm AI " + (aiScore == null ? "—" : aiScore) + ", theo quy tắc " + baselineScore;
        if (source == MenuAiDraftSource.AI) {
            return "Phương án AI qua mọi kiểm tra chặn và không thua phương án theo quy tắc (" + scores + ")";
        }
        String why =
                switch (fallback == null ? MenuAiFallbackReason.AI_DISABLED : fallback) {
                    case AI_DISABLED -> "AI đang tắt";
                    case AI_UNAVAILABLE -> "dịch vụ AI không phản hồi";
                    case AI_TIMEOUT -> "AI quá thời gian chờ";
                    case AI_INVALID -> "kết quả AI sai cấu trúc hoặc còn lỗi chặn sau khi sửa";
                    case AI_NOT_BETTER -> "phương án AI có điểm thấp hơn";
                };
        return "Dùng phương án theo quy tắc vì " + why + " (" + scores + ")";
    }
}
