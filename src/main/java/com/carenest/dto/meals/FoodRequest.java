package com.carenest.dto.meals;

import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Thực phẩm: dinh dưỡng trên 100 đơn vị; giá theo đơn vị mua ({@code purchaseUnitSize} đơn vị trong một đơn vị mua).
 * Vi chất (NUT-26, trên 100 đơn vị ăn được) tùy chọn, để trống = chưa có số liệu.
 * Nguồn dinh dưỡng và nguồn gốc (NUT-24) kiểm ở service để trả mã lỗi riêng (NUT-05). Mã để trống ⇒ hệ thống tự sinh (TP001…).
 */
public record FoodRequest(
        @Size(max = 20) String code,
        @NotBlank @Size(max = 150) String name,
        @NotNull FoodGroup foodGroup,
        @NotNull FoodUnit unit,
        @NotBlank @Size(max = 30) String purchaseUnit,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal purchaseUnitSize,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @NotNull @DecimalMin("0") BigDecimal kcal,
        @NotNull @DecimalMin("0") BigDecimal protein,
        @NotNull @DecimalMin("0") BigDecimal lipid,
        @NotNull @DecimalMin("0") BigDecimal glucid,
        @DecimalMin("0") @DecimalMax("1000000") BigDecimal calcium,
        @DecimalMin("0") @DecimalMax("1000000") BigDecimal phosphorus,
        @DecimalMin("0") @DecimalMax("1000000") BigDecimal iron,
        @DecimalMin("0") @DecimalMax("1000000") BigDecimal vitaminA,
        @DecimalMin("0") @DecimalMax("1000000") BigDecimal vitaminB1,
        @DecimalMin("0") @DecimalMax("1000000") BigDecimal vitaminB2,
        @DecimalMin("0") @DecimalMax("1000000") BigDecimal vitaminPp,
        @DecimalMin("0") @DecimalMax("1000000") BigDecimal vitaminC,
        @Size(max = 200) String nutrientSource,
        @Size(max = 50) String nutrientSourceVersion,

        @NotNull @DecimalMin("0") @DecimalMax(value = "100", inclusive = false)
        BigDecimal wastePct,

        @NotNull FoodStorage storage,
        FoodOrigin origin,
        @Size(max = 500) String note,
        List<@NotNull Long> allergenIds) {}
