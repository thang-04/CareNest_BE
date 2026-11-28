package com.carenest.service.meals.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ràng buộc của người lập áp lên một lưới món, tất định và dùng chung cho phương án AI lẫn phương án theo quy tắc: giữ
 * ô theo thực đơn mẫu khi chỉnh từ mẫu (NUT-13) và đặt món bắt buộc vào tuần. Thực phẩm đang thiếu đã được loại từ
 * bước chọn món ứng viên nên không xử lý ở đây.
 */
final class MenuPlanConstraints {

    /** Một ô khác thực đơn mẫu: {@code fromDishId} null khi mẫu không có món ở ô đó. */
    record Changed(LocalDate date, String slot, Long fromDishId, Long toDishId) {}

    private MenuPlanConstraints() {}

    static MenuGrid apply(MenuGrid grid, MenuContext ctx) {
        return placeRequired(keepTemplate(grid, ctx), ctx);
    }

    /** Món mẫu của ô nếu ô đó được giữ theo mẫu: còn là ứng viên của ô và kho khô không hết; ngược lại null. */
    static Long pinned(MenuContext ctx, LocalDate date, MenuSlot slot) {
        if (!ctx.keepTemplate()) {
            return null;
        }
        Long dish = ctx.templateDish(date, slot);
        return dish != null
                        && ctx.candidatesOf(slot).contains(dish)
                        && !ctx.signals().outOfStock().contains(dish)
                ? dish
                : null;
    }

    /** Còn ô nào không giữ theo mẫu (ô để model hoặc quy tắc tự chọn món). */
    static boolean hasOpenCells(MenuContext ctx) {
        for (LocalDate date : ctx.servedDates()) {
            for (MenuSlot slot : ctx.slots()) {
                if (!ctx.candidatesOf(slot).isEmpty() && pinned(ctx, date, slot) == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MenuGrid keepTemplate(MenuGrid grid, MenuContext ctx) {
        if (!ctx.keepTemplate()) {
            return grid;
        }
        MenuGrid current = grid;
        for (LocalDate date : ctx.servedDates()) {
            for (MenuSlot slot : ctx.slots()) {
                Long dish = pinned(ctx, date, slot);
                if (dish != null) {
                    current = current.with(date, slot.key(), dish);
                }
            }
            // Ô tự chọn trùng món với ô vừa giữ theo mẫu trong cùng bữa ⇒ đổi sang món ứng viên khác
            for (MenuSlot slot : ctx.slots()) {
                Long now = current.dish(date, slot.key());
                if (now == null
                        || pinned(ctx, date, slot) != null
                        || !current.usedInSession(date, slot, now, ctx.slots())) {
                    continue;
                }
                for (Long candidate : ctx.candidatesOf(slot)) {
                    if (!current.usedInSession(date, slot, candidate, ctx.slots())) {
                        current = current.with(date, slot.key(), candidate);
                        break;
                    }
                }
            }
        }
        return current;
    }

    // Món bắt buộc chưa có trong tuần ⇒ đặt vào ô đầu tiên nhận món đó mà chưa giữ món bắt buộc khác
    private static MenuGrid placeRequired(MenuGrid grid, MenuContext ctx) {
        if (ctx.requiredDishIds().isEmpty()) {
            return grid;
        }
        Set<Long> present = new HashSet<>();
        ctx.servedDates()
                .forEach(d ->
                        present.addAll(grid.cells().getOrDefault(d, Map.of()).values()));
        MenuGrid current = grid;
        for (Long required : new TreeSet<>(ctx.requiredDishIds())) {
            if (present.contains(required)) {
                continue;
            }
            current = place(current, ctx, required);
        }
        return current;
    }

    private static MenuGrid place(MenuGrid grid, MenuContext ctx, Long required) {
        for (MenuSlot slot : ctx.slots()) {
            if (!ctx.candidatesOf(slot).contains(required)) {
                continue;
            }
            for (LocalDate date : ctx.servedDates()) {
                Long now = grid.dish(date, slot.key());
                if ((now == null || !ctx.requiredDishIds().contains(now))
                        && !grid.usedInSession(date, slot, required, ctx.slots())) {
                    return grid.with(date, slot.key(), required);
                }
            }
        }
        return grid;
    }

    /** Các ô của lưới khác thực đơn mẫu, theo thứ tự ngày rồi ô; rỗng khi không chỉnh từ mẫu hoặc lưới rỗng. */
    static List<Changed> changedCells(MenuGrid grid, MenuContext ctx) {
        List<Changed> changed = new ArrayList<>();
        if (!ctx.keepTemplate() || grid.cells().isEmpty()) {
            return changed;
        }
        for (LocalDate date : ctx.servedDates()) {
            for (MenuSlot slot : ctx.slots()) {
                Long from = ctx.templateDish(date, slot);
                Long to = grid.dish(date, slot.key());
                if (to != null && !Objects.equals(from, to)) {
                    changed.add(new Changed(date, slot.key(), from, to));
                }
            }
        }
        return changed;
    }
}
