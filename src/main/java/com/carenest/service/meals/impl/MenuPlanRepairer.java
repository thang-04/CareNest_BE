package com.carenest.service.meals.impl;

import com.carenest.dto.meals.MealPlanResponse.Evaluation;
import com.carenest.dto.meals.MealPlanResponse.Issue;
import com.carenest.dto.meals.MealPlanResponse.NormRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Bước 7a: sửa tất định ngày còn lỗi chặn bằng cách đổi món một ô trong tập ứng viên của ô đó, mỗi lần chọn phép đổi
 * giảm lệch định mức nhiều nhất. Dùng chung cho phương án AI và phương án theo quy tắc nên phần thắng không đến từ bước
 * sửa.
 */
final class MenuPlanRepairer {

    // Mỗi ngày đổi tối đa vài ô: đủ kéo kcal/P:L:G về khoảng mà không biến phương án thành lưới khác hẳn
    private static final int MAX_SWAPS_PER_DAY = 4;
    private static final double OTHER_BLOCKING_PENALTY = 10;
    private static final double OUT_OF_STOCK_PENALTY = 0.5;

    private MenuPlanRepairer() {}

    static MenuGrid repair(MenuGrid grid, MenuContext ctx) {
        return repair(grid, ctx, ctx.servedDates());
    }

    static MenuGrid repair(MenuGrid grid, MenuContext ctx, Collection<LocalDate> dates) {
        MenuGrid current = grid;
        for (LocalDate date : dates) {
            if (ctx.offDays().contains(date)) {
                continue;
            }
            current = repairDay(current, date, ctx);
        }
        return current;
    }

    private static MenuGrid repairDay(MenuGrid grid, LocalDate date, MenuContext ctx) {
        MenuGrid current = grid;
        double penalty = penalty(current.evaluateDay(date, ctx));
        for (int swap = 0; swap < MAX_SWAPS_PER_DAY && penalty > 0; swap++) {
            MenuGrid best = null;
            double bestPenalty = penalty;
            for (MenuSlot slot : ctx.slots()) {
                Long now = current.dish(date, slot.key());
                for (Long candidate : ctx.candidatesOf(slot)) {
                    if (candidate.equals(now) || current.usedInSession(date, slot, candidate, ctx.slots())) {
                        continue;
                    }
                    MenuGrid trial = current.with(date, slot.key(), candidate);
                    // Cùng sửa được thì tránh món kho khô đang hết; phần cộng < 1 nên vẫn ưu tiên hết lỗi chặn
                    double trialPenalty = penalty(trial.evaluateDay(date, ctx))
                            + (ctx.signals().outOfStock().contains(candidate) ? OUT_OF_STOCK_PENALTY : 0);
                    if (trialPenalty < bestPenalty) {
                        best = trial;
                        bestPenalty = trialPenalty;
                    }
                }
            }
            if (best == null) {
                break;
            }
            current = best;
            penalty = bestPenalty;
        }
        return current;
    }

    /** 0 ⇔ ngày không còn lỗi chặn; lỗi lệch định mức tính theo mức lệch để biết phép đổi nào đi đúng hướng. */
    static double penalty(MealPlanEvaluator.Result day) {
        double total = 0;
        for (Issue issue : day.issues()) {
            if (MealPlanEvaluator.BLOCKING.equals(issue.severity()) && !"NOT_BALANCED".equals(issue.code())) {
                total += OTHER_BLOCKING_PENALTY;
            }
        }
        for (Evaluation evaluation : day.evaluations()) {
            List<NormRow> off = evaluation.rows().stream()
                    .filter(MenuScore::coreRow)
                    .filter(r -> MenuScore.offRange(r.status()))
                    .toList();
            for (NormRow row : off) {
                total += 1 + MenuScore.deviation(row);
            }
        }
        return total;
    }
}
