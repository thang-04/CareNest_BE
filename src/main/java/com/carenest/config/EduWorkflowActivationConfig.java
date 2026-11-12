package com.carenest.config;

import com.carenest.service.eduplan.EduWorkflowService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Hook startup có kiểm readiness (EDU-54, data-model §7):
 * Kích hoạt và đối soát các tài liệu đang pending / backfill snapshot trước khi ứng dụng nhận traffic.
 */
@Configuration
public class EduWorkflowActivationConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EduWorkflowActivationConfig.class);
    private static final String LOG_PREFIX = "EDU_WORKFLOW_ACTIVATION";

    private final EduWorkflowService workflowService;
    private final Clock clock;

    public EduWorkflowActivationConfig(EduWorkflowService workflowService, Clock clock) {
        this.workflowService = workflowService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("{}|START", LOG_PREFIX);
        try {
            workflowService.activateLegacyPending(clock.instant());
            log.info("{}|END|status=READY", LOG_PREFIX);
        } catch (Exception ex) {
            log.error("{}|ERROR|Activation failed", LOG_PREFIX, ex);
            throw ex;
        }
    }
}
