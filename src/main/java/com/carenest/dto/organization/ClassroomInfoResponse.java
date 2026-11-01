package com.carenest.dto.organization;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.List;

/** Thông tin lớp cho module khác (điểm danh): năm học, điểm trường, các bữa của nhóm tuổi. */
public record ClassroomInfoResponse(
        Long id,
        String name,
        Long campusId,
        Long schoolYearId,
        SchoolYearStatus yearStatus,
        LocalDate yearStart,
        LocalDate yearEnd,
        Long ageGroupId,
        List<MealSession> mealSessions) {}
