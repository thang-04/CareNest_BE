package com.carenest.service.child.impl;

import com.carenest.service.child.ChildRecordService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * G94-180/CHD-03, CHD-04 (user chốt 2026-11-25): trẻ có lịch nghỉ học vẫn Đang học tới ngày nghỉ; 00:05 ngày đó job áp
 * lịch (Đã nghỉ, lớp kết thúc hôm trước) và khóa phụ huynh không còn con đang học. Chạy thêm một lần lúc khởi động: lịch
 * chưa áp (BE tắt lúc 00:05) vẫn còn trong bảng nên được áp bù, không giới hạn số ngày lỡ. Lịch đã áp bị xóa nên chạy lặp
 * trong ngày không áp lại. Transaction do {@link ChildRecordService#applyDueScheduledLeaves} tự mở theo từng trẻ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ScheduledLeaveJob {
    private static final String LOG_PREFIX = "[ScheduledLeaveJob]";

    private final ChildRecordService childRecordService;
    private final Clock clock;

    @Scheduled(cron = "0 5 0 * * *", zone = "${carenest.time-zone}")
    public void applyDueScheduledLeaves() {
        String logPrefix = LOG_PREFIX + "|applyDueScheduledLeaves";
        log.info("{}|START", logPrefix);
        try {
            List<Long> applied = childRecordService.applyDueScheduledLeaves(LocalDate.now(clock));
            log.info("{}|children={}|END", logPrefix, applied.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // BE tắt lúc 00:05 thì lần chạy đó bị lỡ: khởi động lại là áp bù ngay, không chờ 00:05 hôm sau
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        String logPrefix = LOG_PREFIX + "|catchUpOnStartup";
        log.info("{}|START", logPrefix);
        try {
            List<Long> applied = childRecordService.applyDueScheduledLeaves(LocalDate.now(clock));
            log.info("{}|children={}|END", logPrefix, applied.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
