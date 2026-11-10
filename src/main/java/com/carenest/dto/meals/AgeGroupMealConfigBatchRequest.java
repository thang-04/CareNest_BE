package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Lưu cấu hình nhiều nhóm tuổi một lần: một nhóm lỗi thì không nhóm nào được lưu. */
public record AgeGroupMealConfigBatchRequest(
        @NotEmpty @Size(max = 20) List<@Valid AgeGroupMealConfigRequest> items) {}
