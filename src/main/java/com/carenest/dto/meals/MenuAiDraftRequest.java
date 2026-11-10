package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuBlock;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Yêu cầu gợi ý thực đơn (AI-07). {@code MENU_WEEKLY} cần {@code weekStart} là thứ Hai; {@code season}, {@code wishes}
 * chỉ là ưu tiên gửi kèm, không chứa thông tin trẻ (AI-04). {@code targetDate}, {@code targetSession}, {@code
 * fromDishId} dành cho thay một món.
 */
public record MenuAiDraftRequest(
        @NotNull MenuAiDraftKind kind,
        @NotNull MenuBlock block,
        LocalDate weekStart,
        @Size(max = 50) String season,
        @Size(max = 300) String wishes,
        LocalDate targetDate,
        MealSession targetSession,
        Long fromDishId) {}
