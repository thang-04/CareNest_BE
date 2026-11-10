package com.carenest.service.meals.impl;

import com.carenest.entity.meals.Food;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * NUT-12 (user chốt 2026-11-08): lượng cần đặt/nhập thêm làm tròn lên nguyên đơn vị mua khi đơn vị mua đếm được (hộp,
 * quả, chai, gói…); đơn vị cân đo (kg, g, lít, ml, lạng) mua lẻ được nên giữ nguyên. Số trả về vẫn theo đơn vị gốc.
 */
final class PurchaseUnits {

    // Chữ đầu của đơn vị mua (bỏ dấu, chữ thường) là đơn vị cân đo ⇒ mua lẻ được; "chai 1 kg" vẫn đếm được
    private static final Set<String> MEASURE_UNITS = Set.of(
            "kg", "kilogam", "kilogram", "g", "gam", "gram", "lang", "l", "lit", "liter", "litre", "ml", "mililit");

    private PurchaseUnits() {}

    static boolean countable(String purchaseUnit) {
        if (purchaseUnit == null || purchaseUnit.isBlank()) {
            return false;
        }
        String plain = Normalizer.normalize(purchaseUnit.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        String head = plain.split("[\\s\\d.,/()]+", 2)[0];
        return !head.isEmpty() && !MEASURE_UNITS.contains(head);
    }

    /** Lượng theo đơn vị gốc, làm tròn lên bội số của quy cách mua khi đơn vị mua đếm được. */
    static BigDecimal roundUp(BigDecimal quantity, Food food) {
        return food == null ? quantity : roundUp(quantity, food.getPurchaseUnit(), food.getPurchaseUnitSize());
    }

    static BigDecimal roundUp(BigDecimal quantity, String purchaseUnit, BigDecimal size) {
        if (quantity == null
                || quantity.signum() <= 0
                || size == null
                || size.signum() <= 0
                || !countable(purchaseUnit)) {
            return quantity;
        }
        return quantity.divide(size, 0, RoundingMode.CEILING).multiply(size);
    }
}
