package com.carenest.dto.meals;

import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.ContractStatus;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import java.math.BigDecimal;
import java.util.List;

/** {@code unitPrice}, NCC và trạng thái hợp đồng lấy tại hôm nay theo lịch sử giá (NUT-27). */
public record FoodResponse(
        Long id,
        String code,
        String name,
        FoodGroup foodGroup,
        FoodUnit unit,
        String purchaseUnit,
        BigDecimal purchaseUnitSize,
        BigDecimal unitPrice,
        BigDecimal kcal,
        BigDecimal protein,
        BigDecimal lipid,
        BigDecimal glucid,
        BigDecimal calcium,
        BigDecimal phosphorus,
        BigDecimal iron,
        BigDecimal vitaminA,
        BigDecimal vitaminB1,
        BigDecimal vitaminB2,
        BigDecimal vitaminPp,
        BigDecimal vitaminC,
        BigDecimal zinc,
        String nutrientSource,
        BigDecimal wastePct,
        FoodStorage storage,
        FoodOrigin origin,
        String note,
        CatalogStatus status,
        List<AllergenResponse> allergens,
        Long supplierId,
        String supplierName,
        ContractStatus contractStatus) {}
