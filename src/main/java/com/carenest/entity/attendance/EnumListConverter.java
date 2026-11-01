package com.carenest.entity.attendance;

import com.carenest.utils.enums.HealthAction;
import com.carenest.utils.enums.HealthSymptom;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Lưu danh sách enum dạng "A,B" theo thứ tự enum, không trùng; chuỗi rỗng = danh sách rỗng. */
public abstract class EnumListConverter<E extends Enum<E>> implements AttributeConverter<List<E>, String> {

    private final Class<E> type;

    protected EnumListConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(List<E> values) {
        return values == null
                ? ""
                : values.stream().distinct().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public List<E> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(name -> Enum.valueOf(type, name))
                .sorted()
                .toList();
    }

    @Converter
    public static class Symptoms extends EnumListConverter<HealthSymptom> {
        public Symptoms() {
            super(HealthSymptom.class);
        }
    }

    @Converter
    public static class Actions extends EnumListConverter<HealthAction> {
        public Actions() {
            super(HealthAction.class);
        }
    }
}
