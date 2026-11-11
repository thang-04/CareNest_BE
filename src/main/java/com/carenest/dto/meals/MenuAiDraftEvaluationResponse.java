package com.carenest.dto.meals;

import java.util.List;

/**
 * Kết quả chấm thử một lưới món đã sửa (không lưu): cùng evaluator, điểm và bảng kiểm với lúc tạo nháp. {@code
 * editedCells}: số ô khác gợi ý gốc; {@code feasible=false} khi còn lỗi chặn (NUT-09).
 */
public record MenuAiDraftEvaluationResponse(
        boolean feasible,
        int editedCells,
        Score score,
        List<MealPlanResponse.Evaluation> evaluations,
        List<MealPlanResponse.Issue> issues,
        List<MenuAiDraftResponse.Check> checks) {

    public record Score(int total, MenuAiDraftResponse.Breakdown breakdown) {}
}
