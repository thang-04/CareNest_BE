package com.carenest.service.attendance.impl;

import com.carenest.entity.attendance.AttendanceRecordHistory;
import com.carenest.entity.attendance.LeaveAttendanceEffect;
import com.carenest.entity.attendance.LeaveRequest;
import com.carenest.repository.attendance.AttendanceRecordHistoryRepository;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.LeaveAttendanceEffectRepository;
import com.carenest.repository.attendance.LeaveRequestRepository;
import com.carenest.service.child.YearTransitionService;
import com.carenest.utils.enums.AttendanceStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Trẻ thôi học hoặc ra trường (Cần chốt 27/11/2026): hủy đơn nghỉ từ ngày đó và bỏ bản ghi vắng có phép của các ngày
 * sau hôm nay, trong cùng giao dịch. Ngày đã qua và hôm nay giữ nguyên (sổ suất có thể đã xác nhận).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LeaveOnChildLeftSchoolListener {
    private static final String LOG_PREFIX = "[LeaveOnChildLeftSchoolListener]";
    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceRecordHistoryRepository historyRepository;
    private final LeaveAttendanceEffectRepository effectRepository;
    private final Clock clock;

    @EventListener
    public void onChildrenLeftSchool(YearTransitionService.ChildrenLeftSchoolEvent event) {
        if (event.childIds().isEmpty()) {
            return;
        }
        LocalDate from = event.effectiveDate();
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        List<LeaveRequest> leaves = leaveRequestRepository.findOverlapping(event.childIds(), from, FAR_FUTURE).stream()
                .filter(leave -> !leave.effectiveTo().isBefore(from))
                .toList();
        for (LeaveRequest leave : leaves) {
            LocalDate first = leave.getFromDate().isAfter(from) ? leave.getFromDate() : from;
            LocalDate end = leave.effectiveTo();
            for (LeaveAttendanceEffect effect : effectRepository.findByLeaveRequestId(leave.getId())) {
                recordRepository
                        .findById(effect.getAttendanceRecordId())
                        .filter(record -> record.getDate().isAfter(today)
                                && !record.getDate().isBefore(first)
                                && !record.getDate().isAfter(end)
                                && record.getStatus() == AttendanceStatus.EXCUSED)
                        .ifPresent(record -> {
                            historyRepository.save(
                                    AttendanceRecordHistory.cancelled(record, leave.getCreatedBy(), now));
                            effectRepository.delete(effect);
                            recordRepository.delete(record);
                        });
            }
            leave.cancelFrom(first, leave.getCreatedBy(), now);
        }
        log.info("{}|children={}|leaves={}", LOG_PREFIX, event.childIds().size(), leaves.size());
    }
}
