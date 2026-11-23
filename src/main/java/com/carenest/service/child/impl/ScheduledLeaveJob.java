package com.carenest.service.child.impl;

import com.carenest.repository.child.ChildRepository;
import com.carenest.service.child.YearTransitionService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * G94-180/CHD-03, CHD-04 (user chốt 2026-11-24): trẻ cho nghỉ từ ngày học kế tiếp vẫn học tới hết hôm trước, nên đầu
 * ngày nghỉ mới báo module tài khoản khóa phụ huynh không còn con đang học. Cho nghỉ từ hôm nay thì withdraw báo ngay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ScheduledLeaveJob {
    private static final String LOG_PREFIX = "[ScheduledLeaveJob]";

    private final ChildRepository childRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Listener khóa tài khoản chạy sau commit nên phát sự kiện trong transaction. Chạy cả lúc khởi động (như
    // closeExpiredHealthChecks): BE tắt lúc 00:05 vẫn khóa trong ngày nghỉ; khóa lại tài khoản đã khóa thì bỏ qua
    @Scheduled(cron = "0 5 0 * * *", zone = "${carenest.time-zone}")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void lockParentsOfChildrenLeavingToday() {
        String logPrefix = LOG_PREFIX + "|lockParentsOfChildrenLeavingToday";
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            List<Long> childIds = childRepository.findIdsLeavingOn(today);
            if (!childIds.isEmpty()) {
                eventPublisher.publishEvent(new YearTransitionService.ChildrenLeftSchoolEvent(childIds, today));
            }
            log.info("{}|children={}|END", logPrefix, childIds.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
