package com.carenest.service.health.impl;

import com.carenest.dto.health.HealthExamForm;
import com.carenest.entity.health.HealthFollowUp;
import com.carenest.entity.health.HealthFollowUpEvent;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.repository.health.HealthFollowUpEventRepository;
import com.carenest.repository.health.HealthFollowUpRepository;
import com.carenest.utils.enums.HealthFollowUpAction;
import com.carenest.utils.enums.HealthFollowUpLevel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Đưa lần khám vừa kết luận vào phiếu theo dõi (user chốt 2026-10-31, như mock FE): lần khám "theo dõi sát" mở phiếu
 * hoặc thêm vào phiếu đang mở; có người đi cùng ⇒ ghi trao đổi tại buổi khám. Sửa lần khám đã thuộc phiếu không mở,
 * đóng hay gỡ phiếu: chỉ cập nhật ngày khám, lời trao đổi tại buổi khám, cờ bất thường mới.
 */
@Component
@RequiredArgsConstructor
class HealthFollowUpTracker {

    private static final List<HealthFollowUpAction> ENTRY_ACTIONS =
            List.of(HealthFollowUpAction.OPENED, HealthFollowUpAction.MEASUREMENT_ADDED);
    private static final List<HealthFollowUpAction> CONTACT_ACTIONS = List.of(HealthFollowUpAction.PARENT_CONTACTED);

    private final HealthFollowUpRepository followUpRepository;
    private final HealthFollowUpEventRepository eventRepository;

    /** Chạy sau mỗi lần kết luận có lưu thay đổi; {@code exam} là nội dung đã lưu. */
    void track(
            HealthMeasurement m,
            HealthExamForm exam,
            HealthFollowUpLevel level,
            Long campusId,
            Long userId,
            Instant now) {
        boolean newFinding = HealthExamOutcome.newFinding(exam);
        boolean followUpMode = HealthExamOutcome.followUpMode(exam);
        HealthExamForm.HealthExamCompanion companion = HealthExamOutcome.companionOf(exam);
        Optional<HealthFollowUpEvent> entry =
                eventRepository.findFirstByMeasurementIdAndActionIn(m.getId(), ENTRY_ACTIONS);
        if (entry.isPresent()) {
            HealthFollowUpEvent e = entry.get();
            e.changeExamDate(m.getDate());
            Optional<HealthFollowUpEvent> atExam =
                    eventRepository.findFirstByMeasurementIdAndActionIn(m.getId(), CONTACT_ACTIONS);
            // Bỏ người đi cùng thì giữ nguyên lần trao đổi đã ghi
            if (companion != null) {
                atExam.ifPresent(
                        c -> c.rewriteExamContact(m.getDate(), HealthExamOutcome.contactNote(exam, companion)));
            }
            HealthFollowUp f = followUpRepository.findById(e.getFollowUpId()).orElseThrow();
            if (e.getAction() == HealthFollowUpAction.MEASUREMENT_ADDED
                    && followUpMode
                    && f.isOpen()
                    && !Boolean.FALSE.equals(e.getNewFinding()) != newFinding) {
                e.changeNewFinding(newFinding);
                f.touch(now);
                if (newFinding && atExam.isEmpty()) {
                    addExamContact(f, m, exam, companion, userId, now);
                }
            }
            return;
        }
        if (level != HealthFollowUpLevel.CLOSE_WATCH) {
            return;
        }
        Optional<HealthFollowUp> open = followUpRepository.findByChildIdAndClosedAtIsNull(m.getChildId());
        HealthFollowUp f;
        if (open.isPresent()) {
            f = open.get();
            eventRepository.save(HealthFollowUpEvent.exam(
                    f.getId(),
                    HealthFollowUpAction.MEASUREMENT_ADDED,
                    m.getId(),
                    m.getDate(),
                    followUpMode ? newFinding : null,
                    userId,
                    now));
            f.touch(now);
        } else {
            f = followUpRepository.save(HealthFollowUp.open(m.getChildId(), campusId, now));
            eventRepository.save(HealthFollowUpEvent.exam(
                    f.getId(), HealthFollowUpAction.OPENED, m.getId(), m.getDate(), null, userId, now));
        }
        if (newFinding) {
            addExamContact(f, m, exam, companion, userId, now);
        }
    }

    // Bác sĩ đã trao đổi kết luận với người đi cùng tại buổi khám: tính là trao đổi phụ huynh của vòng
    private void addExamContact(
            HealthFollowUp f,
            HealthMeasurement m,
            HealthExamForm exam,
            HealthExamForm.HealthExamCompanion companion,
            Long userId,
            Instant now) {
        if (companion == null) {
            return;
        }
        eventRepository.save(HealthFollowUpEvent.parentContact(
                f.getId(), m.getDate(), HealthExamOutcome.contactNote(exam, companion), m.getId(), userId, now));
    }
}
