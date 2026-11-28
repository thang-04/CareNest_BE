package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.common.FieldErrorResponse;
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
import org.junit.jupiter.api.DisplayName;
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
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

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
        // Đổi giờ chốt lùi về trước hôm nay (22/10) bị chặn: đơn nghỉ đã tính theo giờ cũ
        assertThatThrownBy(() -> service.saveCutoff(
                        principal, year, new CutoffRequest(LocalTime.of(8, 0), LocalDate.of(2026, 10, 1), null)))
                .isInstanceOf(com.carenest.exception.GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(com.carenest.utils.ApiCode.CUTOFF_DATE_PAST);

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
    @DisplayName("Giờ hẹn ngày nào áp dụng từ ngày đó; lưu lại y hệt lịch đã hẹn thì báo chưa thay đổi")
    void scheduledChangeAppliesOnItsDate() {
        service.saveCutoff(principal, year, new CutoffRequest(LocalTime.of(8, 30), LocalDate.of(2026, 9, 1), null));
        CutoffRequest december = new CutoffRequest(LocalTime.of(9, 0), LocalDate.of(2026, 12, 1), null);
        service.saveCutoff(principal, year, december);
        service.saveCutoff(principal, year, new CutoffRequest(LocalTime.of(8, 45), LocalDate.of(2026, 11, 2), null));

        assertThat(service.findCutoffTime(year, LocalDate.of(2026, 11, 30))).contains(LocalTime.of(8, 45));
        assertThat(service.findCutoffTime(year, LocalDate.of(2026, 12, 1))).contains(LocalTime.of(9, 0));
        assertThatThrownBy(() -> service.saveCutoff(principal, year, december))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CUTOFF_UNCHANGED);
    }

    @Test
    @DisplayName("Hủy được lịch chưa tới ngày áp dụng; ngày đã áp dụng và mốc đầu năm thì không")
    void scheduledChangeCanBeCancelledBeforeItApplies() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate december = LocalDate.of(2026, 12, 1);
        service.saveCutoff(principal, year, new CutoffRequest(LocalTime.of(8, 30), start, null));
        service.saveCutoff(principal, year, new CutoffRequest(LocalTime.of(9, 0), december, null));
        service.saveCutoff(principal, year, new CutoffRequest(LocalTime.of(9, 15), december, null));

        assertThatThrownBy(() -> service.cancelScheduledCutoff(principal, year, start))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CUTOFF_NOT_SCHEDULED);
        expectError(() -> service.cancelScheduledCutoff(vicePrincipal, year, december), ApiCode.FORBIDDEN);

        CutoffResponse response = service.cancelScheduledCutoff(principal, year, december);

        assertThat(response.setting().history()).hasSize(1);
        assertThat(service.findCutoffTime(year, december)).contains(LocalTime.of(8, 30));
        // Dòng đã hủy vẫn còn trong bảng để tra lại
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM attendance_cutoffs WHERE school_year_id = ? AND cancelled_at IS NOT NULL",
                        Long.class,
                        year))
                .isEqualTo(2);
        assertThatThrownBy(() -> service.cancelScheduledCutoff(principal, year, december))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CUTOFF_NOT_SCHEDULED);
    }

    @Test
    @DisplayName("Năm sắp diễn ra: không hủy mốc giờ chốt đầu tiên")
    void firstSettingCannotBeCancelled() {
        long next = jdbc.queryForObject(
                "INSERT INTO school_years (name, start_date, end_date, status) "
                        + "VALUES ('2027-2028', '2027-09-01', '2028-05-31', 'PLANNED') RETURNING id",
                Long.class);
        LocalDate start = LocalDate.of(2027, 9, 1);
        service.saveCutoff(principal, next, new CutoffRequest(LocalTime.of(8, 30), start, null));

        assertThatThrownBy(() -> service.cancelScheduledCutoff(principal, next, start))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CUTOFF_FIRST_NOT_CANCELLABLE);
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

    @Test
    void defaultScheduleUsesFullDayPickupFromThreePm() {
        CutoffResponse response = service.saveCutoff(
                principal, year, new CutoffRequest(LocalTime.of(8, 30), LocalDate.of(2026, 9, 1), null));

        // PCK-04 (phương án A): mặc định mốc tính đủ ngày 15:00, cột V202 cũng mặc định 15:00
        assertThat(response.setting().fullDayPickupFrom()).isEqualTo(LocalTime.of(15, 0));
        assertThat(service.findFullDayPickupFrom(year, LocalDate.of(2026, 9, 2)))
                .contains(LocalTime.of(15, 0));
        assertThat(jdbc.queryForObject(
                        "SELECT column_default FROM information_schema.columns WHERE table_name = 'attendance_cutoffs'"
                                + " AND column_name = 'full_day_pickup_from'",
                        String.class))
                .startsWith("'15:00:00'");
    }

    @Test
    void scheduleOutOfOrderReturnsErrorOnEachOffendingField() {
        CutoffRequest request = new CutoffRequest(
                LocalTime.of(8, 30),
                LocalDate.of(2026, 9, 1),
                null,
                LocalTime.of(8, 0),
                null,
                null,
                null,
                LocalTime.of(11, 0),
                LocalTime.of(15, 0),
                LocalTime.of(15, 30),
                null,
                null);

        assertThatThrownBy(() -> service.saveCutoff(principal, year, request))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> {
                    assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                    assertThat(((GlobalException) ex).getFieldErrors())
                            .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                            .containsExactly(
                                    tuple("afternoonStart", "Bắt đầu chiều phải sau Kết thúc sáng (11:00)"),
                                    tuple("pickupStart", "Bắt đầu trả trẻ phải bằng hoặc sau Mốc tính đủ ngày (15:30)"),
                                    tuple(
                                            "latestAfternoonArrival",
                                            "Giờ đến muộn nhất buổi chiều phải sau Giờ chốt (08:30)"));
                });
        assertThat(service.getCutoff(year).setting()).isNull();
    }

    private static void expectError(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(apiCode.getHttpStatus());
    }
}
