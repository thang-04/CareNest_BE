package com.carenest.service.announcement.impl;

import com.carenest.service.announcement.AnnouncementScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chạy lịch gửi lặp đến hạn mỗi phút; mỗi lịch một giao dịch có khóa SKIP LOCKED (PAR-10). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementScheduleWorker {
    private static final String LOG_PREFIX = "[AnnouncementScheduleWorker]";
    private static final int BATCH = 20;

    private final AnnouncementScheduleService scheduleService;

    @Scheduled(
            fixedDelayString = "${carenest.announcement.schedule-delay:60s}",
            initialDelayString = "${carenest.announcement.schedule-delay:60s}")
    public void runDue() {
        for (Long id : scheduleService.findDueScheduleIds(BATCH)) {
            try {
                scheduleService.runDue(id);
            } catch (RuntimeException ex) {
                log.warn(
                        "{}|runDue|scheduleId={}|ERROR|type={}",
                        LOG_PREFIX,
                        id,
                        ex.getClass().getSimpleName());
            }
        }
    }
}
