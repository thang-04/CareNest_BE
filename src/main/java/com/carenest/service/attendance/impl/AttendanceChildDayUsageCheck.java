package com.carenest.service.attendance.impl;

import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.DailyHealthEntryRepository;
import com.carenest.repository.attendance.PickupRecordRepository;
import com.carenest.service.child.ChildDayUsageCheck;
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

    @Override
    public boolean hasData(Long childId, LocalDate date) {
        return !attendance.findByChildIdInAndDate(List.of(childId), date).isEmpty()
                || !health.findByChildIdAndDateOrderByRecordedAtDescIdDesc(childId, date)
                        .isEmpty()
                || !pickups.findByChildIdInAndDateOrderByRecordedAtAscIdAsc(List.of(childId), date)
                        .isEmpty();
    }
}
