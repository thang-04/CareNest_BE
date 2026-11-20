package com.carenest.dto.child;

import java.time.LocalDate;

/**
 * Form tiếp nhận trẻ ({@code POST /children/enrollments}, mục 4.1 plan tiếp nhận). Lớp không bắt buộc; không có lớp thì
 * điểm trường tiếp nhận (PHT một điểm trường bỏ trống = điểm trường của mình). Service kiểm từng ô (C-5).
 */
public record EnrollChildRequest(
        EnrollmentChildInput child,
        HealthDeclarationInput declaration,
        Long classId,
        Long campusId,
        Long schoolYearId,
        LocalDate startDate) {

    public EnrollChildRequest(
            EnrollmentChildInput child, HealthDeclarationInput declaration, Long classId, Long campusId) {
        this(child, declaration, classId, campusId, null, null);
    }
}
