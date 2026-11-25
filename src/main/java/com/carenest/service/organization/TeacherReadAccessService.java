package com.carenest.service.organization;

import java.time.LocalDate;

public interface TeacherReadAccessService {
    boolean covers(Long userId, Long classroomId, LocalDate from, LocalDate to);

    LocalDate latestDay(Long userId, Long classroomId, LocalDate from, LocalDate to);
}
