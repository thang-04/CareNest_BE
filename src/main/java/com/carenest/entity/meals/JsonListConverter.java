package com.carenest.entity.meals;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lưu danh sách nhỏ, luôn đọc/ghi cùng dòng cha (khung giờ ảnh, sĩ số dự trù, ngày của thực đơn tuần) vào một cột
 * jsonb thay cho bảng con riêng. Chuỗi rỗng hoặc null = danh sách rỗng.
 */
public abstract class JsonListConverter<T> implements AttributeConverter<List<T>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TypeReference<List<T>> type;

    protected JsonListConverter(TypeReference<List<T>> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(List<T> values) {
        return MAPPER.writeValueAsString(values == null ? List.of() : values);
    }

    @Override
    public List<T> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.copyOf(MAPPER.readValue(value, type));
    }

    @Converter
    public static class PhotoWindows extends JsonListConverter<MealPhotoWindow> {
        public PhotoWindows() {
            super(new TypeReference<>() {});
        }
    }

    @Converter
    public static class ForecastCounts extends JsonListConverter<FreshForecastCount> {
        public ForecastCounts() {
            super(new TypeReference<>() {});
        }
    }

    @Converter
    public static class PlanDays extends JsonListConverter<MealPlanDay.Info> {
        public PlanDays() {
            super(new TypeReference<>() {});
        }
    }
}
