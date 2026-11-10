package com.carenest.service.meals;

import com.carenest.dto.meals.MealPlanResponse;
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
     * trả tuần hiện tại. Mẫu ngày không dùng thao tác này.
     */
    MealPlanResponse applyDraft(CurrentUser currentUser, Long id);

    /** Mẫu ngày: Web đã điền form thực đơn mẫu từ nháp ⇒ đánh dấu đã dùng (không ghi bảng mẫu). */
    MenuAiDraftResponse markDailyUsed(CurrentUser currentUser, Long id);
}
