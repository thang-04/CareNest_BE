package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.CampusDetailResponse;
import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.CampusService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
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

/** Điểm trường trên PostgreSQL thật (V11: điểm chính duy nhất, số liệu lớp/trẻ/PHT). Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class CampusServiceIntegrationTest {

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
    private CampusService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vicePrincipal;
    private long campusA;
    private long campusB;

    @BeforeEach
    void setUp() {
        long principalId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-p', 'principal.it', 'Principal IT') RETURNING id");
        long vpId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-vp', 'vp.it', 'Phó HT IT') RETURNING id");
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Campus A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name) VALUES ('CS-B', 'Campus B') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vpId,
                campusA);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (" + campusA + ", "
                + year + ", 'Lớp A1') RETURNING id");
        long child = insertId(
                "INSERT INTO children (full_name, date_of_birth) VALUES ('Trẻ IT', '2022-01-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                child,
                classroom);
        principal = new CurrentUser(principalId, "kc-p", "principal.it", "P", null, Set.of(CareNestRole.PRINCIPAL));
        vicePrincipal = new CurrentUser(vpId, "kc-vp", "vp.it", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));
    }

    @Test
    void listShowsStatsOfActiveYear() {
        List<CampusResponse> campuses = service.getCampuses(principal, null);

        CampusResponse a = campuses.getFirst();
        assertThat(a.id()).isEqualTo(campusA);
        assertThat(a.isMain()).isTrue();
        assertThat(a.classCount()).isEqualTo(1);
        assertThat(a.childCount()).isEqualTo(1);
        assertThat(a.vicePrincipals()).extracting("fullName").containsExactly("Phó HT IT");
    }

    @Test
    void vicePrincipalOnlySeesAssignedCampus() {
        assertThat(service.getCampuses(vicePrincipal, null))
                .extracting(CampusResponse::id)
                .containsExactly(campusA);
        assertThatThrownBy(() -> service.getCampusDetail(vicePrincipal, campusB, null))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FORBIDDEN);
    }

    @Test
    void detailListsClassesAndStaff() {
        CampusDetailResponse detail = service.getCampusDetail(principal, campusA, null);

        assertThat(detail.classes())
                .extracting(CampusDetailResponse.ClassBrief::name)
                .containsExactly("Lớp A1");
        assertThat(detail.vicePrincipals()).hasSize(1);
    }

    @Test
    void switchingMainKeepsOneMainCampus() {
        service.updateCampus(principal, campusB, new CampusRequest("CS-B", "Campus B", "B", "Địa chỉ B", null, true));

        Integer mains = jdbc.queryForObject("SELECT count(*) FROM campuses WHERE is_main", Integer.class);
        assertThat(mains).isEqualTo(1);
        Boolean bIsMain = jdbc.queryForObject("SELECT is_main FROM campuses WHERE id = ?", Boolean.class, campusB);
        assertThat(bIsMain).isTrue();
    }

    @Test
    void campusWithStaffCannotBeDeleted() {
        assertThatThrownBy(() -> service.deleteCampus(principal, campusA))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CONFLICT);
    }

    @Test
    void emptyCampusCanBeDeleted() {
        service.deleteCampus(principal, campusB);

        Integer remaining = jdbc.queryForObject("SELECT count(*) FROM campuses WHERE id = ?", Integer.class, campusB);
        assertThat(remaining).isZero();
    }

    @Test
    void deactivateCampusRequiresNoVicePrincipalAndCanBeReactivated() {
        assertThatThrownBy(() -> service.changeCampusStatus(principal, campusA, CampusStatus.INACTIVE))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CONFLICT);
        long vpB = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-vpb', 'vpb.it', 'PHT B') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vpB,
                campusB);
        long planned = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2027-2028', '2027-09-05', '2028-05-31') RETURNING id");
        long plannedClass = insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (" + campusB
                + ", " + planned + ", 'Lớp B1') RETURNING id");
        // Còn lớp ở năm sắp diễn ra ⇒ chưa ngừng được
        assertThatThrownBy(() -> service.changeCampusStatus(principal, campusB, CampusStatus.INACTIVE))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CONFLICT);
        jdbc.update("DELETE FROM classrooms WHERE id = ?", plannedClass);

        // Còn PHT phụ trách ⇒ chặn, lỗi nêu tên PHT; phân công giữ nguyên
        assertThatThrownBy(() -> service.changeCampusStatus(principal, campusB, CampusStatus.INACTIVE))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("PHT B");
        assertThat(jdbc.queryForObject(
                        "SELECT valid_to FROM staff_assignments WHERE user_id = ? AND campus_id = ?",
                        java.time.LocalDate.class,
                        vpB,
                        campusB))
                .isNull();
        jdbc.update("UPDATE staff_assignments SET valid_to = '2026-10-21' WHERE user_id = ?", vpB);

        CampusResponse off = service.changeCampusStatus(principal, campusB, CampusStatus.INACTIVE);
        assertThat(off.status()).isEqualTo(CampusStatus.INACTIVE);
        assertThatThrownBy(() -> service.changeCampusStatus(vicePrincipal, campusB, CampusStatus.ACTIVE))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FORBIDDEN);

        assertThat(service.changeCampusStatus(principal, campusB, CampusStatus.ACTIVE)
                        .status())
                .isEqualTo(CampusStatus.ACTIVE);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
