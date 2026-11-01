package com.carenest.entity.organization;

import com.carenest.utils.enums.MealSession;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Lưu danh sách bữa dạng "LUNCH,AFTERNOON"; chuỗi rỗng = chưa cấu hình. Luôn theo thứ tự enum. */
@Converter
public class MealSessionListConverter implements AttributeConverter<List<MealSession>, String> {

    @Override
    public String convertToDatabaseColumn(List<MealSession> sessions) {
        if (sessions == null) {
            return "";
        }
        return sessions.stream().distinct().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public List<MealSession> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(MealSession::valueOf)
                .sorted()
                .toList();
    }
}
