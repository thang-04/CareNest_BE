package com.carenest.config;

import com.carenest.service.eduplan.EduYearPlanService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** EDU-10: người sửa đóng tab / mất mạng thì khóa "Đang sửa" tự về Nháp sau thời hạn cấu hình. */
@Configuration
public class EduPlanLockCleanupConfig {
    private final EduYearPlanService eduYearPlanService;

    public EduPlanLockCleanupConfig(EduYearPlanService eduYearPlanService) {
        this.eduYearPlanService = eduYearPlanService;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void releaseExpiredEditLocks() {
        eduYearPlanService.releaseExpiredEditLocks();
    }
}
