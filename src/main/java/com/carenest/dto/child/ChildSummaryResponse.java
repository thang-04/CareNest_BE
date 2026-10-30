package com.carenest.dto.child;

import java.time.LocalDate;

/** Một dòng danh sách trẻ để chọn khi cấp tài khoản phụ huynh; lớp/campus hiện tại null nếu chưa có lớp. */
public record ChildSummaryResponse(
        Long id, String fullName, LocalDate dateOfBirth, Long classroomId, Long campusId, long guardianCount) {}
