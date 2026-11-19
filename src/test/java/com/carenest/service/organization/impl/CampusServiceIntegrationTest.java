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

/** Điểm trường trên PostgreSQL thật (V14: điểm chính duy nhất, số liệu lớp/trẻ/PHT). Mỗi test rollback. Cần Docker. */
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
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Trẻ IT', '2022-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
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
    @DisplayName("AUTH-20: PHT còn phân công xem mọi điểm trường, chỉ xem")
    void vicePrincipalSeesEveryCampus() {
        assertThat(service.getCampuses(vicePrincipal, null))
                .extracting(CampusResponse::id)
                .containsExactly(campusA, campusB);
        assertThat(service.getCampusDetail(vicePrincipal, campusB, null)).isNotNull();
        assertThatThrownBy(() -> service.updateCampus(vicePrincipal, campusB, null))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.FORBIDDEN.getHttpStatus());
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
    @DisplayName("Tên điểm trường chuẩn hóa: 2 dấu cách, kiểu gõ dấu khác, hoa thường vẫn tính là trùng (2106)")
    void campusNameDuplicateIgnoresSpacesAccentFormAndCase() {
        service.updateCampus(
                principal, campusB, new CampusRequest("CS-B", "  Cơ   sở 2 ", "B", "Địa chỉ B", null, false));
        assertThat(jdbc.queryForObject("SELECT name FROM campuses WHERE id = ?", String.class, campusB))
                .isEqualTo("Cơ sở 2");
        // "Cơ sở 2" gõ dấu tổ hợp (NFD) và viết hoa
        String decomposed = java.text.Normalizer.normalize("CƠ SỞ 2", java.text.Normalizer.Form.NFD);
        for (String name : List.of("Cơ  sở 2", decomposed)) {
            assertThatThrownBy(() -> service.createCampus(
                            principal, new CampusRequest("CS-C", name, "C", "Địa chỉ C", null, false)))
                    .isInstanceOf(GlobalException.class)
                    .extracting(ex -> ((GlobalException) ex).getApiCode())
                    .isEqualTo(ApiCode.CAMPUS_NAME_EXISTS);
        }
        assertThatThrownBy(() -> service.updateCampus(
                        principal, campusA, new CampusRequest("CS-A", decomposed, "A", "Địa chỉ A", null, true)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CAMPUS_NAME_EXISTS);
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
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
    }

    @Test
    @DisplayName("Cần chốt 19/11/2026: chỉ HT xóa điểm trường trống; còn dữ liệu thì báo đủ lý do (2124)")
    void campusWithDataCannotBeDeletedAndListShowsReason() {
        assertThat(service.getCampuses(principal, null))
                .extracting(CampusResponse::deleteBlockedReason)
                .containsExactly("Điểm trường chính, không xóa được", null);
        assertThatThrownBy(() -> service.deleteCampus(vicePrincipal, campusB))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.FORBIDDEN.getHttpStatus());

        jdbc.update(
                "INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (?, 'P.1', 'Phòng 1', 'CLASS', 20)",
                campusB);
        expectNotEmpty("1 phòng");
        jdbc.update("DELETE FROM rooms WHERE campus_id = ?", campusB);

        long year = jdbc.queryForObject("SELECT id FROM school_years LIMIT 1", Long.class);
        jdbc.update("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'Lớp B1')", campusB, year);
        expectNotEmpty("1 lớp");
        jdbc.update("DELETE FROM classrooms WHERE campus_id = ?", campusB);

        jdbc.update(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, intake_campus_id)"
                        + " VALUES ('IT-B1', 'Trẻ B', '2022-01-01', 'MALE', 'ACTIVE', CURRENT_DATE, ?)",
                campusB);
        expectNotEmpty("1 trẻ");
        jdbc.update("DELETE FROM children WHERE code = 'IT-B1'");

        // Phân công đã kết thúc vẫn tính (lịch sử)
        long vp = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-vp2', 'vp2.it', 'Phó HT Cũ') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from, valid_to) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2025-01-01', '2025-12-31')",
                vp,
                campusB);
        expectNotEmpty("PHT Phó HT Cũ");
        assertThat(service.getCampuses(principal, null).get(1).deleteBlockedReason())
                .isEqualTo("PHT Phó HT Cũ");
    }

    private void expectNotEmpty(String part) {
        assertThatThrownBy(() -> service.deleteCampus(principal, campusB))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> {
                    assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.CAMPUS_NOT_EMPTY);
                    assertThat(ex.getMessage()).contains("Campus B còn " + part);
                });
    }

    @Test
    @DisplayName("Cần chốt 22/10/2026: điểm trường theo năm học — thêm / xóa ở năm nào chỉ năm đó đổi (V151)")
    void campusesArePerSchoolYear() {
        long active = jdbc.queryForObject("SELECT id FROM school_years WHERE status = 'ACTIVE'", Long.class);
        long planned = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2027-09-01', '2028-05-31', 'PLANNED') RETURNING id");
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id) SELECT ?, id FROM campuses",
                active);
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id) SELECT ?, id FROM campuses",
                planned);

        CampusResponse created = service.createCampus(
                principal, new CampusRequest("CS-D3", "Cơ sở 3", "CS3", "Địa chỉ 3", null, false), planned);
        assertThat(service.getCampuses(principal, planned))
                .extracting(CampusResponse::id)
                .contains(created.id());
        assertThat(service.getCampuses(principal, active))
                .extracting(CampusResponse::id)
                .doesNotContain(created.id());

        // Sửa thông tin ở năm sắp diễn ra: chỉ năm đó đổi (V152); trùng tên trong năm bị chặn
        service.updateCampus(
                principal,
                campusB,
                new CampusRequest("CS-B", "Cơ sở Hòa Bình", "Hòa Bình", "Địa chỉ mới", null, false),
                planned);
        assertThat(service.getCampuses(principal, planned))
                .filteredOn(c -> c.id().equals(campusB))
                .extracting(CampusResponse::name)
                .containsExactly("Cơ sở Hòa Bình");
        assertThat(service.getCampuses(principal, active))
                .filteredOn(c -> c.id().equals(campusB))
                .extracting(CampusResponse::name)
                .containsExactly("Campus B");
        assertThatThrownBy(() -> service.updateCampus(
                        principal,
                        campusA,
                        new CampusRequest("CS-A", "cơ sở hòa bình", "A", "Địa chỉ A", null, true),
                        planned))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CAMPUS_NAME_EXISTS);

        // BUG-43: trẻ đã tiếp nhận cho năm sắp diễn ra ở điểm trường (chưa có lớp) chặn bỏ điểm trường khỏi năm đó
        long child = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on)"
                + " VALUES ('IT-PA1', 'Trẻ Tiếp Nhận', '2023-01-01', 'FEMALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        jdbc.update(
                "INSERT INTO planned_admissions (child_id, school_year_id, campus_id, start_date)"
                        + " VALUES (?, ?, ?, '2027-09-01')",
                child,
                planned,
                campusB);
        assertThatThrownBy(() -> service.deleteCampus(principal, campusB, planned))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.CAMPUS_NOT_EMPTY))
                .hasMessageContaining("1 trẻ đã tiếp nhận (Trẻ Tiếp Nhận)");
        jdbc.update("DELETE FROM planned_admissions WHERE child_id = ?", child);

        // Xóa ở năm đang hoạt động: chỉ bỏ khỏi năm đó, năm sau vẫn còn
        service.deleteCampus(principal, campusB, active);
        assertThat(service.getCampuses(principal, active))
                .extracting(CampusResponse::id)
                .doesNotContain(campusB);
        assertThat(service.getCampuses(principal, planned))
                .extracting(CampusResponse::id)
                .contains(campusB);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campuses WHERE id = ?", Integer.class, campusB))
                .isOne();

        // Chỉ còn ở một năm: xóa hẳn
        service.deleteCampus(principal, created.id(), planned);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campuses WHERE id = ?", Integer.class, created.id()))
                .isZero();
        // Năm còn lớp ở điểm trường: không bỏ được khỏi năm đó
        assertThatThrownBy(() -> service.deleteCampus(principal, campusA, planned))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.MAIN_CAMPUS_NOT_DELETABLE);
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
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
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
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
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
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.FORBIDDEN.getHttpStatus());

        assertThat(service.changeCampusStatus(principal, campusB, CampusStatus.ACTIVE)
                        .status())
                .isEqualTo(CampusStatus.ACTIVE);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
