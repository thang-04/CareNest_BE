package com.carenest.dto.child;

import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Gender;
import java.time.LocalDate;
import java.util.List;

/**
 * Một trẻ ở danh sách trẻ ({@code GET /children}, D-4, G94-180/CHD-09): như ChildView không có hành chính, lịch sử.
 * Lớp, điểm trường, nhóm tuổi, năm học theo lớp hiển thị (lớp cuối trong năm học đang lọc; không lọc năm ⇒ lớp đang học
 * hoặc lớp cuối của trẻ đã nghỉ). {@code guardians} bỏ CCCD, email (AUTH-03: màn danh sách không cần).
 * {@code canManage}: người xem quản lý được tài khoản phụ huynh của trẻ (AUTH-19).
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
        List<ChildGuardianResponse> guardians,
        boolean canManage,
        String photoUrl,
        Long classroomId,
        long guardianCount) {}
