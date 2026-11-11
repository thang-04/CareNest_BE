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

/** Vòng đời năm học trên PostgreSQL thật (V13: status, unique 1 năm ACTIVE, FK chặn xóa). Mỗi test rollback. Cần Docker. */
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
    @DisplayName("CFG-YR-05: ngày mở chuẩn bị mặc định 1 tháng trước ngày bắt đầu, kẹp vào khoảng hợp lệ")
    void prepDateDefaultsIntoValidRange() {
        // Hôm nay 30/08/2027: 1 tháng trước 05/09/2027 là 05/08 đã qua ⇒ kẹp về hôm nay
        SchoolYearDetailResponse next = service.createSchoolYear(
                principal, new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2028, 5, 31)));
        assertThat(next.prepDate()).isEqualTo(LocalDate.of(2027, 8, 30));
        assertThat(next.preparing()).isTrue();
    }

    @Test
    @DisplayName("CFG-YR-05: ngày mở chuẩn bị phải trước ngày bắt đầu, sau năm trước, không trước hôm nay")
    void prepDateIsValidated() {
        SchoolYearDetailResponse next = service.createSchoolYear(
                principal, new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2028, 5, 31)));
        assertThatThrownBy(() -> service.updateSchoolYear(principal, next.id(), request(LocalDate.of(2027, 9, 5))))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.SCHOOL_YEAR_PREP_AFTER_START.format());
        assertThatThrownBy(() -> service.updateSchoolYear(principal, next.id(), request(LocalDate.of(2027, 5, 31))))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.SCHOOL_YEAR_PREP_BEFORE_PREVIOUS_END.format("2026-2027", "31/05/2027"));
        assertThatThrownBy(() -> service.updateSchoolYear(principal, next.id(), request(LocalDate.of(2027, 8, 20))))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.SCHOOL_YEAR_PREP_IN_PAST.format());
        SchoolYearDetailResponse later =
                service.updateSchoolYear(principal, next.id(), request(LocalDate.of(2027, 9, 1)));
        assertThat(later.prepDate()).isEqualTo(LocalDate.of(2027, 9, 1));
        assertThat(later.preparing()).isFalse();
        // Đổi ngày bắt đầu sớm lại làm ngày mở chuẩn bị đang có không còn hợp lệ ⇒ báo lỗi ngày mở chuẩn bị
        assertThatThrownBy(() -> service.updateSchoolYear(
                        principal,
                        next.id(),
                        new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 1), LocalDate.of(2028, 5, 31), null)))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.SCHOOL_YEAR_PREP_AFTER_START.format());
    }

    @Test
    @DisplayName("CFG-YR-05: năm đã bắt đầu không đổi được ngày mở chuẩn bị")
    void prepDateLockedAfterYearStarted() {
        jdbc.update("UPDATE school_years SET prep_date = '2026-08-01' WHERE id = ?", currentYear);
        SchoolYearRequest changed = new SchoolYearRequest(
                "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 9, 1), LocalDate.of(2026, 8, 15));
        assertThatThrownBy(() -> service.updateSchoolYear(principal, currentYear, changed))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.SCHOOL_YEAR_PREP_LOCKED.format());
        // Gửi lại đúng ngày cũ (đã qua) vẫn lưu được
        SchoolYearDetailResponse same = service.updateSchoolYear(
                principal,
                currentYear,
                new SchoolYearRequest(
                        "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 9, 1), LocalDate.of(2026, 8, 1)));
        assertThat(same.prepDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("CFG-YR-05: GV chỉ thấy năm sắp diễn ra từ ngày mở chuẩn bị; HT thấy mọi năm")
    void teacherSeesPlannedYearOnlyFromPrepDate() {
        SchoolYearDetailResponse next = service.createSchoolYear(principal, request(LocalDate.of(2027, 9, 1)));
        CurrentUser teacher = new CurrentUser(99L, "kc-t", "t", "T", null, Set.of(CareNestRole.TEACHER));

        assertThat(service.getSchoolYears(teacher, null))
                .extracting(SchoolYearDetailResponse::name)
                .containsExactly("2026-2027");
        assertThat(service.getSchoolYears(principal, null)).hasSize(2);
        assertThatThrownBy(() -> service.getSchoolYear(teacher, next.id()))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.SCHOOL_YEAR_NOT_FOUND.format());

        service.updateSchoolYear(principal, next.id(), request(LocalDate.of(2027, 8, 30)));
        assertThat(service.getSchoolYears(teacher, null)).hasSize(2);
    }

    private static SchoolYearRequest request(LocalDate prepDate) {
        return new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2028, 5, 31), prepDate);
    }

    @Test
    void overlappingYearIsRejected() {
        SchoolYearRequest overlapping =
                new SchoolYearRequest("2027-2028", LocalDate.of(2027, 5, 1), LocalDate.of(2028, 5, 31));

        assertThatThrownBy(() -> service.createSchoolYear(principal, overlapping))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
    }

    @Test
    void newYearMustFollowTheLatestYear() {
        // Đang có 2026-2027: chỉ tạo được 2027-2028, không nhảy sang 2028-2029
        SchoolYearRequest skipped =
                new SchoolYearRequest("2028-2029", LocalDate.of(2028, 9, 5), LocalDate.of(2029, 5, 31));
        assertThatThrownBy(() -> service.createSchoolYear(principal, skipped))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.SCHOOL_YEAR_OUT_OF_SEQUENCE.format("2027-2028", "2026-2027"));

        service.createSchoolYear(
                principal, new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2028, 5, 31)));
        assertThat(service.findNextPlannedYear().name()).isEqualTo("2027-2028");
    }

    @Test
    void databaseRejectsSecondActiveYear() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO school_years (name, start_date, end_date, status) "
                        + "VALUES ('2030-2031', '2030-09-01', '2031-05-31', 'ACTIVE')"))
                .hasMessageContaining("uk_school_years_active");
    }

    @Test
    void deletePlannedYearAlsoDeletesItsClasses() {
        SchoolYearDetailResponse planned = service.createSchoolYear(
                principal, new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2028, 5, 31)));
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-IT', 'Campus IT') RETURNING id");
        jdbc.update(
                "INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'Lớp IT')",
                campus,
                planned.id());

        service.deleteSchoolYear(principal, planned.id());

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM classrooms WHERE school_year_id = ?", Long.class, planned.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM school_years WHERE id = ?", Long.class, planned.id()))
                .isZero();
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
