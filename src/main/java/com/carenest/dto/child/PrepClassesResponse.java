package com.carenest.dto.child;

import com.carenest.utils.enums.Gender;
import java.time.LocalDate;
import java.util.List;

/**
 * Lớp năm sắp diễn ra mà người xem phụ trách, kèm trẻ đã được xếp vào (chỉ xem, CFG-YR-05). Trẻ chỉ vào lớp thật khi
 * kích hoạt năm.
 */
public record PrepClassesResponse(
        Long schoolYearId, String schoolYearName, LocalDate prepDate, LocalDate startDate, List<PrepClass> classes) {

    /** teaches: người xem dạy lớp này; false = tổ trưởng / BGH chỉ xem. */
    public record PrepClass(
            Long id, String name, Long campusId, Long ageGroupId, boolean teaches, List<PrepChild> children) {}

    public record PrepChild(Long id, String fullName, LocalDate dateOfBirth, Gender gender) {}
}
