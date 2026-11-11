package com.carenest.entity.meals;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cột jsonb của biên bản kiểm thực: dòng, phần đầu, chữ ký, bản chụp, lịch sử. Giá trị null giữ null ở cả hai chiều
 * (chữ ký và bản chụp chỉ có khi đã ký).
 */
public abstract class InspectionJson<T> implements AttributeConverter<T, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TypeReference<T> type;

    protected InspectionJson(TypeReference<T> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(T value) {
        return value == null ? null : MAPPER.writeValueAsString(value);
    }

    @Override
    public T convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? null : MAPPER.readValue(value, type);
    }

    @Converter
    public static class Header extends InspectionJson<Map<String, Object>> {
        public Header() {
            super(new TypeReference<>() {});
        }
    }

    @Converter
    public static class Rows extends InspectionJson<List<Map<String, Object>>> {
        public Rows() {
            super(new TypeReference<>() {});
        }
    }

    @Converter
    public static class Signature extends InspectionJson<InspectionSignature> {
        public Signature() {
            super(new TypeReference<>() {});
        }
    }

    @Converter
    public static class Events extends InspectionJson<List<InspectionEvent>> {
        public Events() {
            super(new TypeReference<>() {});
        }
    }
}
