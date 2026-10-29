package com.carenest.dto.organization;

import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.List;

/** Thông tin lớp cho module khác (điểm danh, tiếp nhận trẻ): năm học, điểm trường, các bữa của nhóm tuổi, sĩ số. */
public record ClassroomInfoResponse(
        Long id,
        String name,
        Long campusId,
        Long schoolYearId,
        SchoolYearStatus yearStatus,
        LocalDate yearStart,
        LocalDate yearEnd,
        Long ageGroupId,
        List<MealSession> mealSessions,
        // Sĩ số tối đa (null = không giới hạn) và trạng thái lớp: tiếp nhận / xếp lớp kiểm (CHD-10)
        Integer capacity,
        ClassroomStatus status) {}
