package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Lưu cấu hình nhiều nhóm tuổi một lần: một nhóm lỗi thì không nhóm nào được lưu. */
public record AgeGroupMealConfigBatchRequest(
        @jakarta.validation.constraints.NotNull @Size(max = 20)
        List<@Valid AgeGroupMealConfigRequest> items,

        @Size(max = 20) List<@jakarta.validation.constraints.NotNull @Valid NutritionGroupStatus> statuses) {
    @jakarta.validation.constraints.AssertTrue(message = "Cần ít nhất một thay đổi")
    public boolean isNonEmpty() {
        return (items != null && !items.isEmpty()) || (statuses != null && !statuses.isEmpty());
    }
}
