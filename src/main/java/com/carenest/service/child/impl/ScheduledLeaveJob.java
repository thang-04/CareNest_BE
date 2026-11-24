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
 * G94-180/CHD-03, CHD-04 (user chốt 2026-11-24): trẻ nghỉ từ ngày học kế tiếp còn học tới hết hôm trước, nên từ ngày
 * nghỉ mới báo module tài khoản khóa phụ huynh không còn con đang học (nghỉ từ hôm nay thì withdraw báo ngay). Chạy
 * 00:05 và một lần lúc khởi động, xét ngày nghỉ trong 7 ngày gần nhất: BE tắt lúc 00:05 vẫn khóa bù; chạy lặp vô hại vì
 * listener bỏ qua tài khoản đã khóa hoặc còn con đang học. Chấp nhận: tài khoản phụ huynh HT/PHT mở tay trong 7 ngày đó
 * (không còn con đang học) bị khóa lại ở lần chạy sau.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ScheduledLeaveJob {
    private static final String LOG_PREFIX = "[ScheduledLeaveJob]";
    private static final int CATCH_UP_DAYS = 7;

    private final ChildRepository childRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Listener khóa tài khoản chạy sau commit nên phát sự kiện trong transaction
    @Scheduled(cron = "0 5 0 * * *", zone = "${carenest.time-zone}")
    @Transactional(readOnly = true)
    public void lockParentsOfChildrenWhoLeft() {
        String logPrefix = LOG_PREFIX + "|lockParentsOfChildrenWhoLeft";
        log.info("{}|START", logPrefix);
        try {
            int children = publishRecentLeaves();
            log.info("{}|children={}|END", logPrefix, children);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // BE tắt lúc 00:05 thì lần chạy đó bị lỡ: khởi động lại là khóa bù ngay, không chờ 00:05 hôm sau
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void catchUpOnStartup() {
        String logPrefix = LOG_PREFIX + "|catchUpOnStartup";
        log.info("{}|START", logPrefix);
        try {
            int children = publishRecentLeaves();
            log.info("{}|children={}|END", logPrefix, children);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private int publishRecentLeaves() {
        LocalDate today = LocalDate.now(clock);
        List<Long> childIds = childRepository.findIdsLeftBetween(today.minusDays(CATCH_UP_DAYS), today);
        if (!childIds.isEmpty()) {
            eventPublisher.publishEvent(new YearTransitionService.ChildrenLeftSchoolEvent(childIds, today));
        }
        return childIds.size();
    }
}
