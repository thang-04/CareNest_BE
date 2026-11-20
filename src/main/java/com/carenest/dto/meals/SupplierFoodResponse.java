package com.carenest.dto.meals;

import java.math.BigDecimal;

/** Thực phẩm đang lấy từ một NCC tại một ngày (NUT-27), nguồn cho màn áp dụng hợp đồng mới. */
public record SupplierFoodResponse(Long foodId, String code, String name, String purchaseUnit, BigDecimal price) {}
