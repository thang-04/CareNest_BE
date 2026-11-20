package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.meals.InspectionSaveRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.InspectionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.Permission;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Hai người cùng lưu một biên bản ở phiên bản 0: khóa dòng điểm trường làm hai lần ghi chạy tuần tự, người sau thấy phiên bản
 * mới và nhận xung đột 6082 thay vì ghi đè hoặc đụng khóa duy nhất (INS-01). Không dùng giao dịch của test để hai luồng thật sự
 * tranh nhau.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InspectionConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InspectionService inspectionService;

    @Test
    void concurrentFirstSaveKeepsOneSheetAndRejectsTheLoser() throws Exception {
        long campus = id("INSERT INTO campuses (code, name, is_main) VALUES ('INSRACE', 'Race', TRUE) RETURNING id");
        long userId = id("INSERT INTO user_accounts (keycloak_user_id, username, full_name)"
                + " VALUES ('kc-ins-race', 'ins-race', 'ins-race') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from)"
                        + " VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                userId,
                campus);
        CurrentUser approver = new CurrentUser(
                userId,
                "kc-ins-race",
                "ins-race",
                "ins-race",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.FOOD_SAFETY_APPROVE),
                Set.of());
        LocalDate day = LocalDate.now().minusDays(1);
        InspectionSaveRequest request =
                new InspectionSaveRequest(0, Map.of(), List.of(Map.of("key", "sample:1", "qualityNote", "Mẫu đạt")));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(15, TimeUnit.SECONDS);
                    try {
                        inspectionService.save(approver, campus, day, InspectionTab.SAMPLE, request);
                        return "OK";
                    } catch (GlobalException ex) {
                        return ex.getApiCode().name();
                    }
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(results).containsExactlyInAnyOrder("OK", ApiCode.INSPECTION_STALE.name());
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM inspection_sheets WHERE campus_id = ?", Integer.class, campus))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT version FROM inspection_sheets WHERE campus_id = ?", Integer.class, campus))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
