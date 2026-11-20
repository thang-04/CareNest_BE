package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuAiStrategy;
import com.carenest.utils.enums.MenuBlock;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Yêu cầu gợi ý thực đơn (AI-07). {@code MENU_WEEKLY} cần {@code weekStart} là thứ Hai; {@code season}, {@code wishes}
 * chỉ là ưu tiên gửi kèm, không chứa thông tin trẻ (AI-04). {@code targetDate}, {@code targetSession}, {@code
 * fromDishId} dành cho thay một món. {@code strategy} (mặc định {@code FREE}) và {@code requiredDishIds} (món phải có
 * ít nhất một lần trong tuần) chỉ dùng cho {@code MENU_WEEKLY}; {@code unavailableFoodIds} (thực phẩm đang thiếu, món
 * chứa nó không được gợi ý) dùng cho mọi loại.
 */
public record MenuAiDraftRequest(
        @NotNull MenuAiDraftKind kind,
        @NotNull MenuBlock block,
        LocalDate weekStart,
        @Size(max = 50) String season,
        @Size(max = 300) String wishes,
        LocalDate targetDate,
        MealSession targetSession,
        Long fromDishId,
        MenuAiStrategy strategy,
        @Size(max = 50) List<@NotNull Long> unavailableFoodIds,
        @Size(max = 20) List<@NotNull Long> requiredDishIds) {}
