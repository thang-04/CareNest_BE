package com.carenest.config;

import com.carenest.service.account.PermissionService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * EDU-110 WF2: chuyển chuỗi soạn/duyệt đang chạy sang ma trận quyền một lần khi khởi động, sau
 * {@link EduWorkflowActivationConfig}. Lỗi bắt ngoài giao dịch, không chặn khởi động; lần khởi động sau chạy lại.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class EduRoleMatrixMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EduRoleMatrixMigrationRunner.class);
    private static final String LOG_PREFIX = "[EduRoleMatrixMigrationRunner]";

    private final PermissionService permissionService;
    private final Clock clock;

    public EduRoleMatrixMigrationRunner(PermissionService permissionService, Clock clock) {
        this.permissionService = permissionService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("{}|run|START", LOG_PREFIX);
        try {
            String summary = permissionService.migrateEduRoleMatrix(clock.instant());
            log.info("{}|run|{}|END", LOG_PREFIX, summary);
        } catch (RuntimeException ex) {
            log.error("{}|run|ERROR|type={}", LOG_PREFIX, ex.getClass().getSimpleName(), ex);
        }
    }
}
