package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.utils.enums.MealSession;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Thông tin nhóm tuổi riêng của năm còn mở (V154) phủ lên danh mục chung. */
final class YearAgeGroups {

    private YearAgeGroups() {}

    static Map<Long, AgeGroupRepository.YearAgeGroupInfo> infoOf(AgeGroupRepository repository, Long yearId) {
        return yearId == null
                ? Map.of()
                : repository.findYearAgeGroupInfo(yearId).stream()
                        .collect(Collectors.toMap(
                                AgeGroupRepository.YearAgeGroupInfo::getAgeGroupId, Function.identity()));
    }

    static AgeGroupDetailResponse overlay(
            AgeGroupDetailResponse group, Map<Long, AgeGroupRepository.YearAgeGroupInfo> info) {
        AgeGroupRepository.YearAgeGroupInfo i = info.get(group.id());
        if (i == null) {
            return group;
        }
        List<MealSession> sessions = parseSessions(i.getMealSessions());
        return new AgeGroupDetailResponse(
                group.id(),
                group.code(),
                i.getName(),
                i.getShortName(),
                group.sortOrder(),
                i.getAgeFromMonths() == null ? null : i.getAgeFromMonths().intValue(),
                i.getAgeToMonths() == null ? null : i.getAgeToMonths().intValue(),
                sessions,
                sessions.size(),
                i.getMaxClassSize() == null ? null : i.getMaxClassSize().intValue(),
                i.getNutritionNote() == null ? "" : i.getNutritionNote());
    }

    static List<MealSession> parseSessions(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(v -> !v.isEmpty())
                        .map(MealSession::valueOf)
                        .sorted()
                        .toList();
    }

    static String joinSessions(List<MealSession> sessions) {
        return sessions.stream().distinct().sorted().map(Enum::name).collect(Collectors.joining(","));
    }
}
