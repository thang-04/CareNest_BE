package com.carenest.service.meals.impl;

import com.carenest.entity.meals.Food;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Các trường thực phẩm được ghi lịch sử: nhãn hiển thị → giá trị chữ; null/rỗng đều là chưa có. */
final class FoodSnapshot {

    private FoodSnapshot() {}

    static Map<String, String> of(Food food) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Tên thực phẩm", food.getName());
        values.put("Mã", food.getCode());
        values.put("Nhóm thực phẩm", text(food.getFoodGroup()));
        values.put("Bảo quản", text(food.getStorage()));
        values.put("Nguồn gốc", text(food.getOrigin()));
        values.put("Đơn vị gốc", text(food.getUnit()));
        values.put("Đơn vị mua", food.getPurchaseUnit());
        values.put("Quy đổi đơn vị mua", text(food.getPurchaseUnitSize()));
        values.put("Tỷ lệ thải bỏ", text(food.getWastePct()));
        values.put("Năng lượng", text(food.getKcal()));
        values.put("Chất đạm", text(food.getProtein()));
        values.put("Chất béo", text(food.getLipid()));
        values.put("Tinh bột", text(food.getGlucid()));
        values.put("Canxi", text(food.getCalcium()));
        values.put("Phốt pho", text(food.getPhosphorus()));
        values.put("Sắt", text(food.getIron()));
        values.put("Vitamin A", text(food.getVitaminA()));
        values.put("Vitamin B1", text(food.getVitaminB1()));
        values.put("Vitamin B2", text(food.getVitaminB2()));
        values.put("Vitamin PP", text(food.getVitaminPp()));
        values.put("Vitamin C", text(food.getVitaminC()));
        values.put("Kẽm", text(food.getZinc()));
        values.put("Nguồn số liệu dinh dưỡng", food.getNutrientSource());
        values.put("Ghi chú", food.getNote());
        values.put(
                "Chất gây dị ứng",
                food.getAllergens().stream().map(Enum::name).sorted().collect(Collectors.joining(", ")));
        return values;
    }

    // Số lấy giá trị, không lấy cách viết: 3.20 và 3.2 là một
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal number) {
            return number.stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    static boolean same(String before, String after) {
        return normalize(before).equals(normalize(after));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
