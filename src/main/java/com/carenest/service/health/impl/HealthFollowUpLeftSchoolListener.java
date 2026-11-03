package com.carenest.service.health.impl;

import com.carenest.entity.health.HealthFollowUp;
import com.carenest.entity.health.HealthFollowUpEvent;
import com.carenest.repository.health.HealthFollowUpEventRepository;
import com.carenest.repository.health.HealthFollowUpRepository;
import com.carenest.service.child.YearTransitionService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Trẻ ra trường khi chuyển năm học (CHD-04): phiếu theo dõi đang mở của trẻ tự đóng, không đổi mức theo dõi (C8 plan
 * 2026-10-31-health-exam-follow-ups). Chạy sau khi chuyển năm học đã lưu; lỗi ở đây chỉ để phiếu mở thêm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class HealthFollowUpLeftSchoolListener {
    private static final String LOG_PREFIX = "[HealthFollowUpLeftSchoolListener]";
    private static final String REASON = "Trẻ nghỉ học: ra trường khi chuyển năm học";

    private final HealthFollowUpRepository followUpRepository;
    private final HealthFollowUpEventRepository eventRepository;
    private final Clock clock;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onChildrenLeftSchool(YearTransitionService.ChildrenLeftSchoolEvent event) {
        String logPrefix = LOG_PREFIX + "|onChildrenLeftSchool|children="
                + event.childIds().size();
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            List<HealthFollowUp> open = followUpRepository.findByChildIdInAndClosedAtIsNull(event.childIds());
            for (HealthFollowUp f : open) {
                f.close(null, REASON, null, null, now);
                eventRepository.save(HealthFollowUpEvent.closed(
                        f.getId(), REASON, null, null, HealthFollowUpEvent.AUTO_CHILD_LEFT_SCHOOL, null, now));
            }
            log.info("{}|closed={}|END", logPrefix, open.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
