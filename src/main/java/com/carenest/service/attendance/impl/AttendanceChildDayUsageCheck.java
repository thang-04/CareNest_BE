package com.carenest.service.attendance.impl;

import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.DailyHealthEntryRepository;
import com.carenest.repository.attendance.PickupRecordRepository;
import com.carenest.service.child.ChildDayUsageCheck;
import com.carenest.utils.enums.AttendanceStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceChildDayUsageCheck implements ChildDayUsageCheck {
    private final AttendanceRecordRepository attendance;
    private final DailyHealthEntryRepository health;
    private final PickupRecordRepository pickups;

    private final Clock clock;

    @Override
    public boolean hasData(Long childId, LocalDate date) {
        // Ngày chưa tới chỉ có bản ghi vắng do đơn nghỉ tạo: chưa phải dữ liệu thật, không chặn chuyển lớp
        boolean future = date.isAfter(LocalDate.now(clock));
        return attendance.findByChildIdInAndDate(List.of(childId), date).stream()
                        .anyMatch(r -> !future || r.getStatus() != AttendanceStatus.EXCUSED)
                || !health.findByChildIdAndDateOrderByRecordedAtDescIdDesc(childId, date)
                        .isEmpty()
                || !pickups.findByChildIdInAndDateOrderByRecordedAtAscIdAsc(List.of(childId), date)
                        .isEmpty();
    }
}
