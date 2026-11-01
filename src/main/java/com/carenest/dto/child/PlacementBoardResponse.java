package com.carenest.dto.child;

import com.carenest.utils.enums.PlacementAction;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Màn Xếp lớp năm học mới: lớp năm ACTIVE (nguồn) kèm trẻ đang học và kết quả đã xếp, lớp năm mới (đích) kèm chỗ đã dùng.
 * Dữ liệu trẻ chỉ gồm id, họ tên, ngày sinh (cần để xếp lớp).
 */
public record PlacementBoardResponse(
        YearBrief year,
        YearBrief sourceYear,
        boolean editable,
        List<AgeGroupBrief> ageGroups,
        List<CampusBrief> campuses,
        List<SourceClass> classes,
        List<TargetClass> targets) {

    public record YearBrief(Long id, String name) {}

    public record AgeGroupBrief(Long id, String shortName) {}

    public record CampusBrief(Long id, String name) {}

    public record SourceClass(
            Long id,
            String name,
            Long campusId,
            Long ageGroupId,
            Long nextAgeGroupId,
            boolean graduating,
            Long suggestedTargetId,
            List<ChildPlacement> children) {}

    /** code, photoUrl: BE chưa có (luôn null), giữ để khớp dạng FE. */
    public record ChildPlacement(
            Long id, String code, String fullName, String photoUrl, LocalDate birthDate, Placement placement) {}

    public record Placement(
            Long schoolYear,
            Long childId,
            Long fromClassId,
            PlacementAction action,
            Long toClassId,
            Long updatedBy,
            Instant updatedAt) {}

    public record TargetClass(Long id, String name, Long campusId, Long ageGroupId, Integer capacity, long taken) {}
}
