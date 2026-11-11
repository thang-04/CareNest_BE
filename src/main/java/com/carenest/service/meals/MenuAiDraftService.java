package com.carenest.service.meals;

import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MenuAiDraftCandidatesResponse;
import com.carenest.dto.meals.MenuAiDraftEvaluationResponse;
import com.carenest.dto.meals.MenuAiDraftGridRequest;
import com.carenest.dto.meals.MenuAiDraftRequest;
import com.carenest.dto.meals.MenuAiDraftResponse;
import com.carenest.security.CurrentUser;

/** Nháp gợi ý thực đơn (AI-01, AI-02, AI-07): tạo, xem, bỏ, dùng vào kế hoạch tuần nháp. Không công bố (NUT-08). */
public interface MenuAiDraftService {

    MenuAiDraftResponse createDraft(CurrentUser currentUser, MenuAiDraftRequest request);

    MenuAiDraftResponse getDraft(CurrentUser currentUser, Long id);

    MenuAiDraftResponse dismissDraft(CurrentUser currentUser, Long id);

    /**
     * Tuần: ghi lưới của khối vào tuần chưa công bố, giữ khối khác. Thay một món: chỉ đổi đúng ô. Gọi lại khi đã dùng
     * trả tuần hiện tại. Mẫu ngày không dùng thao tác này. {@code edited} khác null (chỉ gợi ý tuần) ⇒ ghi lưới người
     * duyệt đã sửa thay cho lưới gợi ý và lưu số ô đã sửa; lưới còn lỗi chặn vẫn ghi vào tuần nháp, công bố vẫn bị
     * chặn (NUT-09).
     */
    MealPlanResponse applyDraft(CurrentUser currentUser, Long id, MenuAiDraftGridRequest edited);

    /** Món ứng viên theo ô của nháp gợi ý tuần đang chờ duyệt, để người duyệt đổi món. */
    MenuAiDraftCandidatesResponse getCandidates(CurrentUser currentUser, Long id);

    /** Chấm thử lưới đã sửa của nháp gợi ý tuần đang chờ duyệt; không lưu, không gọi model. */
    MenuAiDraftEvaluationResponse evaluateDraft(CurrentUser currentUser, Long id, MenuAiDraftGridRequest grid);

    /** Mẫu ngày: Web đã điền form thực đơn mẫu từ nháp ⇒ đánh dấu đã dùng (không ghi bảng mẫu). */
    MenuAiDraftResponse markDailyUsed(CurrentUser currentUser, Long id);
}
