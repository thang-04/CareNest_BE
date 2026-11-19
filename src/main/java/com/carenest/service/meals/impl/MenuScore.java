package com.carenest.service.meals.impl;

import com.carenest.config.MenuAiProperties.Weights;
import com.carenest.dto.meals.MealPlanResponse.Evaluation;
import com.carenest.dto.meals.MealPlanResponse.NormRow;
import com.carenest.dto.meals.MealPlanResponse.SessionRow;
import com.carenest.utils.enums.Micronutrient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Điểm phương án 0–100, tất định (bước 6): sát định mức (kcal, P:L:G, gam ĐV-TV, % kcal bữa), giá so giá suất, đa dạng
 * món (trừ món trùng các tuần liền trước), số cảnh báo. Chỉ dùng để so AI với phương án theo quy tắc; cổng cứng là evaluator.
 */
final class MenuScore {

    private static final String OK = "OK";
    private static final String LOW = "LOW";
    private static final String HIGH = "HIGH";
    private static final String NA = "NA";
    private static final Set<String> ENERGY_SHARE_ROWS = Set.of("protein", "lipid", "glucid");
    private static final Set<String> MICRO_KEYS =
            Arrays.stream(Micronutrient.values()).map(Micronutrient::key).collect(Collectors.toSet());
    // Vượt giá suất 50% thì mất hết điểm giá; mỗi cảnh báo trừ 20% điểm cảnh báo
    private static final double OVER_BUDGET_SLOPE = 2.0;
    private static final double WARNING_PENALTY = 0.2;
    private static final double RECENT_PENALTY = 0.5;

    record Breakdown(int norm, int cost, int variety, int warnings) {
        int total() {
            return norm + cost + variety + warnings;
        }
    }

    private MenuScore() {}

    static Breakdown score(MenuGrid grid, MealPlanEvaluator.Result result, MenuContext ctx, Weights weights) {
        List<Evaluation> evaluations = result.evaluations();
        double norm =
                evaluations.stream().mapToDouble(MenuScore::normFit).average().orElse(0);
        double cost =
                evaluations.stream().mapToDouble(MenuScore::costFit).average().orElse(0);
        long warnings = result.issues().stream()
                .filter(i -> MealPlanEvaluator.WARNING.equals(i.severity()))
                .count();
        return new Breakdown(
                points(weights.norm(), norm),
                points(weights.cost(), cost),
                points(weights.variety(), variety(grid, ctx)),
                points(weights.warnings(), Math.max(0, 1 - WARNING_PENALTY * warnings)));
    }

    /**
     * Mức lệch khỏi khoảng định mức, theo bề rộng khoảng (0 = trong khoảng). Dòng P:L:G so theo % năng lượng; dòng
     * khác so theo giá trị.
     */
    static double deviation(NormRow row) {
        BigDecimal measured = ENERGY_SHARE_ROWS.contains(row.key()) ? row.percent() : row.value();
        return deviation(measured, row.min(), row.max());
    }

    static boolean coreRow(NormRow row) {
        return !MICRO_KEYS.contains(row.key());
    }

    static boolean offRange(String status) {
        return LOW.equals(status) || HIGH.equals(status);
    }

    private static double deviation(BigDecimal measured, BigDecimal min, BigDecimal max) {
        if (min == null || max == null) {
            return 0;
        }
        if (measured == null) {
            return 1;
        }
        double width = Math.max(max.subtract(min).doubleValue(), Math.max(max.doubleValue() * 0.1, 1));
        if (measured.compareTo(min) < 0) {
            return min.subtract(measured).doubleValue() / width;
        }
        return measured.compareTo(max) > 0 ? measured.subtract(max).doubleValue() / width : 0;
    }

    // Trung bình mức đạt các dòng chính và % kcal bữa đã đặt định mức; chưa đặt định mức hoặc thiếu dữ liệu = 0
    private static double normFit(Evaluation evaluation) {
        List<Double> fits = new ArrayList<>();
        for (NormRow row : evaluation.rows()) {
            if (!coreRow(row) || NA.equals(row.status())) {
                continue;
            }
            fits.add(fit(row.status(), deviation(row)));
        }
        for (SessionRow row : evaluation.sessions()) {
            if (!NA.equals(row.status())) {
                fits.add(fit(row.status(), deviation(row.percent(), row.min(), row.max())));
            }
        }
        return fits.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double fit(String status, double deviation) {
        if (OK.equals(status)) {
            return 1;
        }
        return offRange(status) ? Math.max(0, 1 - deviation) : 0;
    }

    private static double costFit(Evaluation evaluation) {
        BigDecimal price = evaluation.mealPrice();
        if (price == null || price.signum() <= 0 || evaluation.cost() == null) {
            return 1;
        }
        double ratio = evaluation.cost().doubleValue() / price.doubleValue();
        return ratio <= 1 ? 1 : Math.max(0, 1 - (ratio - 1) * OVER_BUDGET_SLOPE);
    }

    // Số món khác nhau / số ô, trừ phần ô dùng món đã có các tuần liền trước
    private static double variety(MenuGrid grid, MenuContext ctx) {
        int cells = 0;
        int recent = 0;
        Set<Long> distinct = new HashSet<>();
        for (LocalDate date : ctx.servedDates()) {
            for (Long dishId : grid.cells().getOrDefault(date, Map.of()).values()) {
                cells++;
                distinct.add(dishId);
                if (ctx.recentDishIds().contains(dishId)) {
                    recent++;
                }
            }
        }
        if (cells == 0) {
            return 0;
        }
        return Math.max(0, (double) distinct.size() / cells - RECENT_PENALTY * recent / cells);
    }

    private static int points(int weight, double fraction) {
        return (int) Math.round(weight * Math.max(0, Math.min(1, fraction)));
    }
}
