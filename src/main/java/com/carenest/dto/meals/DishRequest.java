package com.carenest.dto.meals;

import com.carenest.utils.enums.DishType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Món kèm toàn bộ lưới định lượng thực phẩm × nhóm tuổi (NUT-18); thiếu ô = nhóm tuổi đó không dùng thực phẩm. Mã để trống ⇒ tự sinh (MA001…). */
public record DishRequest(
        @Size(max = 20) String code,
        @NotBlank @Size(max = 150) String name,
        @NotNull DishType dishType,
        @Size(max = 500) String description,
        @NotEmpty @Size(max = 200) List<@Valid Ingredient> ingredients) {

    public record Ingredient(
            @NotNull Long foodId,
            @NotNull Long ageGroupId,

            @NotNull @DecimalMin(value = "0", inclusive = false)
            BigDecimal gramsPerChild) {}
}
