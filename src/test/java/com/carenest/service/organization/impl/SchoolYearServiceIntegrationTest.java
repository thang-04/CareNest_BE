package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.SchoolYearRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
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

/** Vòng đời năm học trên PostgreSQL thật (V10: status, unique 1 năm ACTIVE, FK chặn xóa). Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class SchoolYearServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2027-08-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private SchoolYearService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long currentYear;

    @BeforeEach
    void setUp() {
        long principalId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-principal', 'principal.it', 'Principal IT') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        principal = new CurrentUser(
                principalId, "kc-principal", "principal.it", "Principal IT", null, Set.of(CareNestRole.PRINCIPAL));
        currentYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
    }

    @Test
    void createListActivateKeepsSingleActiveYear() {
        SchoolYearDetailResponse next = service.createSchoolYear(
                principal, new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2028, 5, 31)));
        assertThat(next.status()).isEqualTo(SchoolYearStatus.PLANNED);

        SchoolYearDetailResponse activated = service.activateSchoolYear(principal, next.id(), "2027-2028");

        assertThat(activated.status()).isEqualTo(SchoolYearStatus.ACTIVE);
        assertThat(statusOf(currentYear)).isEqualTo("CLOSED");
        Integer activeCount =
                jdbc.queryForObject("SELECT count(*) FROM school_years WHERE status = 'ACTIVE'", Integer.class);
        assertThat(activeCount).isEqualTo(1);

        List<SchoolYearDetailResponse> years = service.getSchoolYears(principal, null);
        assertThat(years).extracting(SchoolYearDetailResponse::name).containsExactly("2027-2028", "2026-2027");
    }

    @Test
    void overlappingYearIsRejected() {
        SchoolYearRequest overlapping =
                new SchoolYearRequest("2027-2028", LocalDate.of(2027, 5, 1), LocalDate.of(2028, 5, 31));

        assertThatThrownBy(() -> service.createSchoolYear(principal, overlapping))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CONFLICT);
    }

    @Test
    void databaseRejectsSecondActiveYear() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO school_years (name, start_date, end_date, status) "
                        + "VALUES ('2030-2031', '2030-09-01', '2031-05-31', 'ACTIVE')"))
                .hasMessageContaining("uk_school_years_active");
    }

    @Test
    void deleteBlockedByClassroom() {
        SchoolYearDetailResponse planned = service.createSchoolYear(
                principal, new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2028, 5, 31)));
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-IT', 'Campus IT') RETURNING id");
        jdbc.update(
                "INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'Lớp IT')",
                campus,
                planned.id());

        assertThatThrownBy(() -> service.deleteSchoolYear(principal, planned.id()))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CONFLICT);
    }

    @Test
    void deletesPlannedYearWithoutUsage() {
        SchoolYearDetailResponse planned = service.createSchoolYear(
                principal, new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2028, 5, 31)));

        service.deleteSchoolYear(principal, planned.id());

        Integer remaining =
                jdbc.queryForObject("SELECT count(*) FROM school_years WHERE id = ?", Integer.class, planned.id());
        assertThat(remaining).isZero();
    }

    private String statusOf(long schoolYearId) {
        return jdbc.queryForObject("SELECT status FROM school_years WHERE id = ?", String.class, schoolYearId);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
