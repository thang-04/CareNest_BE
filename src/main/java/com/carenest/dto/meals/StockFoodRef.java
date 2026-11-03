package com.carenest.dto.meals;

import java.math.BigDecimal;

/** Thực phẩm trong dữ liệu kho: đơn vị gốc ({@code unit}) và đơn vị mua để client quy đổi khi hiển thị. */
public record StockFoodRef(
        Long id, String code, String name, String unit, String purchaseUnit, BigDecimal purchaseUnitSize) {}
