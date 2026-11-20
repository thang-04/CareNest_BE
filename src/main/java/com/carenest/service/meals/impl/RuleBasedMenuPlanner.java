package com.carenest.service.meals.impl;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phương án theo quy tắc (baseline, bước 3): cách hệ thống tự lập khi không có AI — lấy món của thực đơn mẫu đang dùng
 * nếu món đó còn là ứng viên, ô còn lại quay vòng món ứng viên, tránh lặp trong tuần. Tất định: cùng dữ liệu cho cùng
 * lưới, để so với AI công bằng.
 */
final class RuleBasedMenuPlanner {

    private RuleBasedMenuPlanner() {}

    static MenuGrid plan(MenuContext ctx) {
        Map<String, Set<Long>> usedBySlot = new HashMap<>();
        MenuGrid grid = MenuGrid.empty();
        for (int dayIndex = 0; dayIndex < ctx.dates().size(); dayIndex++) {
            LocalDate date = ctx.dates().get(dayIndex);
            if (ctx.offDays().contains(date)) {
                continue;
            }
            Map<String, Long> fromTemplate = ctx.template().getOrDefault(dayIndex, Map.of());
            for (MenuSlot slot : ctx.slots()) {
                List<Long> candidates = ctx.candidatesOf(slot);
                if (candidates.isEmpty()) {
                    continue;
                }
                Set<Long> used = usedBySlot.computeIfAbsent(slot.key(), k -> new HashSet<>());
                Long templateDish = fromTemplate.get(slot.key());
                Long pick = templateDish != null
                                && candidates.contains(templateDish)
                                && !ctx.signals().outOfStock().contains(templateDish)
                                && !grid.usedInSession(date, slot, templateDish, ctx.slots())
                        ? templateDish
                        : rotate(grid, date, slot, candidates, used, dayIndex, ctx);
                if (pick != null) {
                    grid = grid.with(date, slot.key(), pick);
                    used.add(pick);
                }
            }
        }
        return grid;
    }

    // Bắt đầu từ vị trí theo ngày để các ngày khác nhau; món đã dùng trong tuần ở ô này chỉ lấy lại khi hết món
    private static Long rotate(
            MenuGrid grid,
            LocalDate date,
            MenuSlot slot,
            List<Long> candidates,
            Set<Long> used,
            int dayIndex,
            MenuContext ctx) {
        // Thứ tự ưu tiên: món còn hàng chưa dùng trong tuần > món còn hàng dùng lại > món kho khô đang hết (NUT-11)
        Long reuse = null;
        Long outOfStock = null;
        for (int k = 0; k < candidates.size(); k++) {
            Long dishId = candidates.get((dayIndex + k) % candidates.size());
            if (grid.usedInSession(date, slot, dishId, ctx.slots())) {
                continue;
            }
            if (ctx.signals().outOfStock().contains(dishId)) {
                outOfStock = outOfStock == null ? dishId : outOfStock;
            } else if (!used.contains(dishId)) {
                return dishId;
            } else if (reuse == null) {
                reuse = dishId;
            }
        }
        return reuse != null ? reuse : outOfStock;
    }
}
