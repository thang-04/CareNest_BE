package com.carenest.service.announcement.impl;

import com.carenest.service.announcement.AnnouncementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Gửi thông báo hẹn giờ đã đến giờ; mỗi thông báo một giao dịch có khóa SKIP LOCKED (research R6). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementDispatchWorker {
    private static final String LOG_PREFIX = "[AnnouncementDispatchWorker]";
    private static final int BATCH = 20;

    private final AnnouncementService announcementService;

    @Scheduled(
            fixedDelayString = "${carenest.announcement.worker-delay}",
            initialDelayString = "${carenest.announcement.worker-delay}")
    public void dispatchDue() {
        List<Long> ids = announcementService.findDueAnnouncementIds(BATCH);
        for (Long id : ids) {
            try {
                announcementService.dispatchDue(id);
            } catch (RuntimeException ex) {
                log.warn(
                        "{}|dispatchDue|announcementId={}|ERROR|type={}",
                        LOG_PREFIX,
                        id,
                        ex.getClass().getSimpleName());
            }
        }
    }
}
