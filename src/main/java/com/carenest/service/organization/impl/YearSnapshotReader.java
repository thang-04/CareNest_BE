package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CFG-YR-02: đọc bản chụp lúc năm học kết thúc. Năm còn mở (hoặc không chọn năm) ⇒ Optional.empty / null,
 * nơi gọi dùng dữ liệu hiện tại.
 */
@Component
@RequiredArgsConstructor
class YearSnapshotReader {

    private final SchoolYearRepository schoolYearRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final SchoolStructureMapper schoolStructureMapper;

    boolean isClosed(Long yearId) {
        return yearId != null
                && schoolYearRepository
                        .findById(yearId)
                        .map(y -> y.getStatus() == SchoolYearStatus.CLOSED)
                        .orElse(false);
    }

    Optional<List<AgeGroupDetailResponse>> ageGroups(Long yearId) {
        if (!isClosed(yearId)) {
            return Optional.empty();
        }
        return Optional.of(schoolYearRepository.findAgeGroupSnapshot(yearId).stream()
                .map(g -> {
                    List<MealSession> sessions =
                            g.getMealSessions() == null || g.getMealSessions().isBlank()
                                    ? List.of()
                                    : Arrays.stream(g.getMealSessions().split(","))
                                            .map(String::trim)
                                            .map(MealSession::valueOf)
                                            .toList();
                    return new AgeGroupDetailResponse(
                            g.getAgeGroupId(),
                            g.getCode(),
                            g.getName(),
                            g.getShortName(),
                            g.getSortOrder(),
                            g.getAgeFromMonths() == null
                                    ? null
                                    : g.getAgeFromMonths().intValue(),
                            g.getAgeToMonths() == null
                                    ? null
                                    : g.getAgeToMonths().intValue(),
                            sessions,
                            sessions.size(),
                            g.getMaxClassSize() == null
                                    ? null
                                    : g.getMaxClassSize().intValue(),
                            g.getNutritionNote());
                })
                .toList());
    }

    /**
     * Nhóm tuổi của năm học: năm đã kết thúc đọc bản chụp; năm còn mở đọc danh sách riêng của năm (Cần chốt
     * 22/10/2026). Migration đã backfill các năm cũ; danh sách rỗng là trạng thái hợp lệ.
     */
    List<AgeGroupDetailResponse> yearAgeGroups(Long yearId) {
        return ageGroups(yearId).orElseGet(() -> {
            List<Long> ids = yearId == null ? List.of() : ageGroupRepository.findYearAgeGroupIds(yearId);
            List<AgeGroup> groups = yearId == null
                    ? ageGroupRepository.findAll()
                    : ids.isEmpty() ? List.of() : ageGroupRepository.findAllById(ids);
            // Năm còn mở: thông tin riêng của năm (V154)
            var info = YearAgeGroups.infoOf(ageGroupRepository, yearId);
            return groups.stream()
                    .map(schoolStructureMapper::toDetailResponse)
                    .map(g -> YearAgeGroups.overlay(g, info))
                    .sorted(Comparator.comparing((AgeGroupDetailResponse g) ->
                                    g.ageFromMonths() == null ? Integer.MAX_VALUE : g.ageFromMonths())
                            .thenComparing(AgeGroupDetailResponse::sortOrder))
                    .toList();
        });
    }

    /** Phòng theo bản chụp (mã, tên) của năm đã kết thúc; null = năm còn mở. */
    Map<Long, SchoolYearRepository.RoomSnapshot> rooms(Long yearId) {
        if (!isClosed(yearId)) {
            return null;
        }
        return schoolYearRepository.findRoomSnapshot(yearId).stream()
                .collect(Collectors.toMap(SchoolYearRepository.RoomSnapshot::getRoomId, Function.identity()));
    }
}
