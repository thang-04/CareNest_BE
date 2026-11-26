package com.carenest.entity.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

/**
 * Gam ăn một trẻ của một thực phẩm trong một món. {@code baseGrams} là số trước khi làm tròn lượng mua; thải bỏ, giá và
 * đơn vị mua chụp tại lúc lưu để số đã chốt không đổi khi danh mục đổi (NUT-27).
 */
@Embeddable
public record DayBalanceLine(
        @Column(name = "age_group_id", nullable = false) Long ageGroupId,

        @Enumerated(EnumType.STRING) @Column(name = "session", nullable = false, length = 20)
        MealSession session,

        @Column(name = "dish_id", nullable = false) Long dishId,
        @Column(name = "food_id", nullable = false) Long foodId,
        @Column(name = "base_grams", nullable = false) BigDecimal baseGrams,
        @Column(name = "grams", nullable = false) BigDecimal grams,
        @Column(name = "waste_pct") BigDecimal wastePct,
        @Column(name = "unit_price") BigDecimal unitPrice,
        @Column(name = "purchase_unit", length = 50) String purchaseUnit,
        @Column(name = "purchase_unit_size") BigDecimal purchaseUnitSize) {}
