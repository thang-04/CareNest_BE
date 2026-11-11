package com.carenest.entity.meals;

/**
 * Ghi chú lưu mẫu của một món (Mẫu 5): khối lượng hoặc thể tích mẫu (gam/ml), dụng cụ chứa, nhiệt độ bảo quản. Chuỗi
 * tự do vì bếp ghi theo thói quen ("100 g", "hộp nhựa có nắp", "2–8 °C"); rỗng khi chưa ghi.
 */
public record FoodSampleDishDetail(Long dishId, String weight, String container, String temperature) {}
