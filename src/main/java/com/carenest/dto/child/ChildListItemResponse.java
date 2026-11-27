package com.carenest.dto.child;

import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Gender;
import java.time.LocalDate;
import java.util.List;

/**
 * Một trẻ ở danh sách trẻ ({@code GET /children}, D-4, G94-180/CHD-09): như ChildView không có hành chính, lịch sử.
 * Lớp, điểm trường, nhóm tuổi, năm học theo lớp hiển thị (lớp cuối trong năm học đang lọc; không lọc năm ⇒ lớp đang học
 * hoặc lớp cuối của trẻ đã nghỉ). {@code guardians} bỏ CCCD, email (AUTH-03: màn danh sách không cần).
 * {@code canManage}: người xem quản lý được tài khoản phụ huynh của trẻ (AUTH-19). G94-180/CHD-14: {@code startDate},
 * {@code startDue}, {@code nextClass} như ChildView (lớp hiển thị ưu tiên lớp đã bắt đầu); {@code scheduledLeaveOn} =
 * ngày của lịch nghỉ học chưa tới ngày (null khi không có).
 *
 * <p>{@code classroomId} (= {@code classId}) và {@code guardianCount} (số tài khoản phụ huynh đã liên kết) giữ cho màn
 * Tài khoản phụ huynh của Web đang đọc.
 */
public record ChildListItemResponse(
        Long id,
        String code,
        String fullName,
        LocalDate dateOfBirth,
        Gender gender,
        ChildStatus status,
        Long campusId,
        Long classId,
        String className,
        Long ageGroupId,
        Long schoolYearId,
        LocalDate enrolledAt,
        LocalDate leftAt,
        LocalDate readmittedAt,
        List<String> allergies,
        boolean hasDeclaration,
        boolean mealRegistered,
        List<ChildGuardianResponse> guardians,
        boolean canManage,
        String photoUrl,
        Long classroomId,
        long guardianCount,
        PlannedAdmissionResponse plannedAdmission,
        LocalDate startDate,
        boolean startDue,
        ChildNextClassResponse nextClass,
        LocalDate scheduledLeaveOn,
        boolean photoMissing) {}
