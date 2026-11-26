package com.carenest.service.observation.impl;

import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.AssessmentDayUnlock;
import com.carenest.repository.observation.AssessmentDayUnlockRepository;
import com.carenest.service.attendance.AttendanceDayUnlockService.AttendanceDayUnlockedEvent;
import com.carenest.utils.enums.SchoolYearStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mở lại điểm danh một lớp/ngày đã khóa thì mở lại đánh giá cùng lớp/ngày (cùng hạn) nếu ngày đánh giá cũng đã khóa
 * (OBS-12, OBS-19, ATT-08): trẻ được sửa Vắng → Có mặt vẫn được đánh giá. Đã có lượt mở còn hiệu lực thì giữ nguyên.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AssessmentReopenOnAttendanceUnlockListener {
    private static final String LOG_PREFIX = "[AssessmentReopenOnAttendanceUnlockListener]";

    private final ObservationDayRules rules;
    private final AssessmentDayUnlockRepository unlockRepository;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAttendanceDayUnlocked(AttendanceDayUnlockedEvent event) {
        try {
            ClassroomInfoResponse cls = rules.findClass(event.classroomId());
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE || !rules.isBaseLocked(event.date())) {
                return;
            }
            String reason = ("Mở cùng điểm danh: " + event.reason());
            reason = reason.length() > 300 ? reason.substring(0, 300) : reason;
            rules.lockClassForUpdate(cls.id());
            if (rules.activeUnlock(cls.id(), event.date()).isPresent()) {
                return;
            }
            unlockRepository.save(AssessmentDayUnlock.create(
                    cls.id(),
                    cls.campusId(),
                    event.date(),
                    event.validUntil(),
                    reason,
                    event.unlockedBy(),
                    rules.now()));
        } catch (RuntimeException ex) {
            // Không chặn việc mở lại điểm danh, nhưng phải thấy được để HT mở đánh giá bằng tay
            log.error(
                    "{}|classId={}|date={}|ERROR|type={}",
                    LOG_PREFIX,
                    event.classroomId(),
                    event.date(),
                    ex.getClass().getSimpleName(),
                    ex);
        }
    }
}
