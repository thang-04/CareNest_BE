package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.CutoffRequest;
import com.carenest.dto.organization.CutoffResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AttendanceCutoffService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Giờ chốt điểm danh trên PostgreSQL thật (V17): lịch sử và giờ áp dụng theo ngày. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class AttendanceCutoffIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private AttendanceCutoffService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vicePrincipal;
    private long year;

    @BeforeEach
    void setUp() {
        long principalId = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                        + "VALUES ('kc-p', 'principal.it', 'Principal IT') RETURNING id",
                Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        year = jdbc.queryForObject(
                "INSERT INTO school_years (name, start_date, end_date, status) "
                        + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id",
                Long.class);
        principal = new CurrentUser(principalId, "kc-p", "principal.it", "P", null, Set.of(CareNestRole.PRINCIPAL));
        vicePrincipal = new CurrentUser(99L, "kc-vp", "vp", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));
    }

    @Test
    void newerSettingAppliesFromItsDateAndHistoryKeepsPrevious() {
        service.saveCutoff(principal, year, new CutoffRequest(LocalTime.of(8, 30), LocalDate.of(2026, 9, 1), null));
        CutoffResponse response = service.saveCutoff(
                principal, year, new CutoffRequest(LocalTime.of(8, 45), LocalDate.of(2026, 11, 1), "Đổi theo bếp"));

        assertThat(response.setting().time()).isEqualTo(LocalTime.of(8, 45));
        assertThat(response.setting().history().getFirst().previousTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(response.setting().updatedByName()).isEqualTo("Principal IT");
        assertThat(response.setting().history()).hasSize(2);
        // Hôm nay 22/10 vẫn dùng 08:30; từ 01/11 dùng 08:45
        assertThat(response.todayTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(service.findCutoffTime(year, LocalDate.of(2026, 11, 2))).contains(LocalTime.of(8, 45));
    }

    @Test
    void effectiveDateOutsideYearIsRejected() {
        expectError(
                () -> service.saveCutoff(
                        principal, year, new CutoffRequest(LocalTime.of(8, 30), LocalDate.of(2027, 6, 1), null)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void unchangedSettingIsRejected() {
        CutoffRequest request = new CutoffRequest(LocalTime.of(8, 30), LocalDate.of(2026, 9, 1), null);
        service.saveCutoff(principal, year, request);

        expectError(() -> service.saveCutoff(principal, year, request), ApiCode.BAD_REQUEST);
    }

    @Test
    void vicePrincipalCannotSave() {
        expectError(
                () -> service.saveCutoff(
                        vicePrincipal, year, new CutoffRequest(LocalTime.of(8, 30), LocalDate.of(2026, 9, 1), null)),
                ApiCode.FORBIDDEN);
    }

    @Test
    void noSettingYet() {
        CutoffResponse response = service.getCutoff(year);

        assertThat(response.setting()).isNull();
        assertThat(response.todayTime()).isNull();
    }

    private static void expectError(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(apiCode.getHttpStatus());
    }
}
