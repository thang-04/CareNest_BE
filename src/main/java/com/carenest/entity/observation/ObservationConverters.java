package com.carenest.entity.observation;

import com.carenest.entity.attendance.EnumListConverter;
import com.carenest.utils.enums.ObservedBehavior;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Converter danh sách của đánh giá hằng ngày: hành vi (enum) và id MT (số) lưu dạng "a,b". */
public final class ObservationConverters {

    private ObservationConverters() {}

    @Converter
    public static class Behaviors extends EnumListConverter<ObservedBehavior> {
        public Behaviors() {
            super(ObservedBehavior.class);
        }
    }

    @Converter
    public static class Ids implements AttributeConverter<List<Long>, String> {
        @Override
        public String convertToDatabaseColumn(List<Long> values) {
            return values == null
                    ? ""
                    : values.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
        }

        @Override
        public List<Long> convertToEntityAttribute(String value) {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .map(Long::valueOf)
                    .sorted()
                    .toList();
        }
    }
}
