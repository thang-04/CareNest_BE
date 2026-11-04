package com.carenest.service.attendance.impl;

import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.service.organization.SchoolDayUsageCheck;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ngày đã có điểm danh thì không đặt thành ngày nghỉ (SCH-05). */
@Component
@RequiredArgsConstructor
public class AttendanceDayUsageImpl implements SchoolDayUsageCheck {

    private final AttendanceRecordRepository attendanceRecordRepository;

    @Override
    public Optional<LocalDate> firstUsedDay(LocalDate from, LocalDate to) {
        return attendanceRecordRepository.findFirstDateBetween(from, to);
    }
}
