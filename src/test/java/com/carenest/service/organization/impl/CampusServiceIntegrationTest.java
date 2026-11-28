package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
    private CampusService service;

    @Autowired
    private com.carenest.service.organization.YearSetupService yearSetupService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.carenest.service.organization.StaffAssignmentService staffAssignmentService;

    @Autowired
    private com.carenest.service.organization.TeacherAssignmentService teacherAssignmentService;

    private CurrentUser principal;
    private CurrentUser vicePrincipal;
    private long campusA;
    private long campusB;
    private long schoolYearId;

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
        schoolYearId = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address, phone, status) "
                        + "SELECT ?, id, code, name, short_name, address, phone, status FROM campuses",
                schoolYearId);
        long classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (" + campusA + ", "
                + schoolYearId + ", 'Lớp A1') RETURNING id");
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
    @DisplayName(
            "Tên điểm trường: dấu gạch ngang, gạch nối, gạch dài và khoảng trắng quanh dấu gạch tính là trùng (2106)")
    void campusNameDuplicateIgnoresDashKind() {
        service.updateCampus(
                principal,
                campusB,
                new CampusRequest("CS-B", "Cơ sở 1 – Trường Mầm non Thượng Hồng", "Cơ sở B", "Địa chỉ B", null, false));
        for (String name : List.of(
                "Cơ sở 1 - Trường Mầm non Thượng Hồng",
                "Cơ sở 1-Trường Mầm non Thượng Hồng",
                "CƠ SỞ 1 — TRƯỜNG MẦM NON THƯỢNG HỒNG")) {
            assertThatThrownBy(() -> service.createCampus(
                            principal, new CampusRequest("CS-C", name, "C", "Địa chỉ C", null, false)))
                    .isInstanceOf(GlobalException.class)
                    .extracting(ex -> ((GlobalException) ex).getApiCode())
                    .isEqualTo(ApiCode.CAMPUS_NAME_EXISTS);
        }
        assertThatThrownBy(() -> service.updateCampus(
                        principal,
                        campusA,
                        new CampusRequest(
                                "CS-A", "Cơ sở 1 - Trường Mầm non Thượng Hồng", "Cơ sở A", "Địa chỉ A", null, true)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CAMPUS_NAME_EXISTS);
    }

    @Test
    @DisplayName("Tên ngắn điểm trường không trùng, không phân biệt hoa thường (2126)")
    void campusShortNameMustBeUnique() {
        service.updateCampus(
                principal, campusB, new CampusRequest("CS-B", "Campus B", "Cơ sở 1", "Địa chỉ B", null, false));
        assertThatThrownBy(() -> service.createCampus(
                        principal, new CampusRequest("CS-C", "Campus C", "CƠ SỞ 1", "Địa chỉ C", null, false)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CAMPUS_SHORT_NAME_EXISTS);
        assertThatThrownBy(() -> service.updateCampus(
                        principal, campusA, new CampusRequest("CS-A", "Campus A", "cơ sở 1", "Địa chỉ A", null, true)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CAMPUS_SHORT_NAME_EXISTS);
        // Giữ nguyên tên ngắn của chính mình khi sửa thông tin khác thì không bị chặn
        service.updateCampus(
                principal, campusB, new CampusRequest("CS-B", "Campus B2", "Cơ sở 1", "Địa chỉ B", null, false));
    }

    @Test
    @DisplayName("Mã điểm trường chuẩn hóa: khác khoảng trắng và hoa thường vẫn tính là trùng (2105)")
    void campusCodeDuplicateIgnoresSpacesAndCase() {
        assertThatThrownBy(() -> service.createCampus(
                        principal, new CampusRequest(" cs - a ", "Campus C", "C", "Địa chỉ C", null, false)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CAMPUS_CODE_EXISTS);
    }

    @Test
    @DisplayName("Điểm trường ngừng hoạt động vẫn giữ mã, tên và tên ngắn để tránh trùng khi mở lại")
    void inactiveCampusKeepsIdentifiersReserved() {
        jdbc.update(
                "UPDATE campuses SET status = 'INACTIVE', name = 'Cơ sở A', short_name = 'CS A' WHERE id = ?", campusA);

        assertCampusCreateFails(
                new CampusRequest("cs - a", "Cơ sở mới", "CS mới", "Địa chỉ C", null, false),
                ApiCode.CAMPUS_CODE_EXISTS);
        assertCampusCreateFails(
                new CampusRequest("CS-C", "cơ  sở  a", "CS mới", "Địa chỉ C", null, false), ApiCode.CAMPUS_NAME_EXISTS);
        assertCampusCreateFails(
                new CampusRequest("CS-C", "Cơ sở mới", " cs   a ", "Địa chỉ C", null, false),
                ApiCode.CAMPUS_SHORT_NAME_EXISTS);
    }

    @Test
    @DisplayName("Không thể đổi bản thiết lập năm học thành định danh của điểm trường đã ngừng hoạt động")
    void yearCampusUpdateKeepsInactiveIdentifiersReserved() {
        jdbc.update(
                "UPDATE campuses SET status = 'INACTIVE', name = 'Cơ sở A', short_name = 'CS A' WHERE id = ?", campusA);

        assertThatThrownBy(() -> service.updateCampus(
                        principal,
                        campusB,
                        new CampusRequest("cs - a", "Campus B", "B", "Địa chỉ B", null, false),
                        schoolYearId))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CAMPUS_CODE_EXISTS);
    }

    private void assertCampusCreateFails(CampusRequest request, ApiCode code) {
        assertThatThrownBy(() -> service.createCampus(principal, request))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(code);
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
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address, phone, status) "
                        + "SELECT ?, id, code, name, short_name, address, phone, status FROM campuses",
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
        assertThatThrownBy(() -> service.updateCampus(
                        principal,
                        campusA,
                        new CampusRequest("CS-A", "Campus A năm sau", "hòa bình", "Địa chỉ A", null, true),
                        planned))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CAMPUS_SHORT_NAME_EXISTS);

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

    @Test
    @DisplayName("Phân công PHT giữ qua các năm: năm sắp diễn ra vẫn hiện, tính đủ khi chuẩn bị năm và chặn ngừng")
    void carriedOverVicePrincipalShowsInPlannedYear() {
        long planned = insertPlannedYearWithCampuses();
        insertVicePrincipal("kc-vpb", "PHT năm trước", "2026-09-01");

        assertThat(service.getCampuses(principal, planned))
                .filteredOn(c -> c.id() == campusB)
                .singleElement()
                .satisfies(c ->
                        assertThat(c.vicePrincipals()).extracting("fullName").containsExactly("PHT năm trước"));
        assertThat(service.getCampusDetail(principal, campusB, planned).vicePrincipals())
                .extracting("fullName")
                .containsExactly("PHT năm trước");
        assertThat(yearSetupService.getYearSetup(planned).campusesWithoutVicePrincipal())
                .doesNotContain(campusA, campusB);
        assertThatThrownBy(() -> service.changeCampusStatus(principal, campusB, CampusStatus.INACTIVE, planned))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("PHT năm trước");
    }

    @Test
    @DisplayName("Ngừng điểm trường: PHT được phân công từ năm trước, chưa kết thúc, vẫn chặn")
    void vicePrincipalFromEarlierYearStillBlocksDeactivation() {
        insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        insertVicePrincipal("kc-vpb", "PHT từ năm trước", "2025-09-01");

        assertThatThrownBy(() -> service.changeCampusStatus(principal, campusB, CampusStatus.INACTIVE))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("PHT từ năm trước");
    }

    @Test
    @DisplayName("Phó hiệu trưởng bị khóa tài khoản vẫn hiện ở điểm trường, kèm cờ locked")
    void lockedVicePrincipalIsFlagged() {
        assertThat(service.getCampuses(principal, null).getFirst().vicePrincipals())
                .extracting("locked")
                .containsExactly(false);

        jdbc.update("UPDATE user_accounts SET status = 'INACTIVE' WHERE id = ?", vicePrincipal.userId());

        assertThat(service.getCampuses(principal, null).getFirst().vicePrincipals())
                .extracting("fullName", "locked")
                .containsExactly(tuple("Phó HT IT", true));
        assertThat(service.getCampusDetail(principal, campusA, null).vicePrincipals())
                .extracting("locked")
                .containsExactly(true);
    }

    @Test
    @DisplayName("Bỏ điểm trường khỏi năm sắp diễn ra: PHT còn phụ trách thì chặn, gỡ rồi mới bỏ được")
    void carriedOverVicePrincipalBlocksRemovalFromYear() {
        long planned = insertPlannedYearWithCampuses();
        long vpB = insertVicePrincipal("kc-vpb", "PHT năm trước", "2026-09-01");

        assertThatThrownBy(() -> service.deleteCampus(principal, campusB, planned))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("PHT năm trước");

        jdbc.update("UPDATE staff_assignments SET valid_to = '2027-08-01' WHERE user_id = ?", vpB);
        service.deleteCampus(principal, campusB, planned);

        assertThat(service.getCampuses(principal, planned))
                .extracting(CampusResponse::id)
                .doesNotContain(campusB);
        assertThat(service.getCampuses(principal, null))
                .extracting(CampusResponse::id)
                .contains(campusB);
    }

    @Test
    @DisplayName("Chi tiết điểm trường năm sắp diễn ra hiện giáo viên đã phân công cho năm đó")
    void plannedYearDetailListsAssignedTeachers() {
        long planned = insertPlannedYearWithCampuses();
        long classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (" + campusA + ", "
                + planned + ", 'Lớp A2') RETURNING id");
        insertTeacher("kc-gv1", "GV từ ngày chuẩn bị", classroom, "2027-08-02");
        insertTeacher("kc-gv2", "GV từ đầu năm", classroom, "2027-09-01");

        assertThat(service.getCampusDetail(principal, campusA, planned).teachers())
                .extracting("fullName")
                .containsExactlyInAnyOrder("GV từ ngày chuẩn bị", "GV từ đầu năm");
    }

    @Test
    @DisplayName("SCH-04: giáo viên bị khóa tài khoản không tính là đủ giáo viên khi kiểm tra chuẩn bị năm")
    void lockedTeacherIsNotCountedInYearSetup() {
        long planned = insertPlannedYearWithCampuses();
        long classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (" + campusA + ", "
                + planned + ", 'Lớp A2') RETURNING id");
        insertTeacher("kc-gv1", "GV chủ nhiệm", classroom, "2027-08-02");
        insertTeacher("kc-gv2", "GV thứ hai", classroom, "2027-08-02");
        jdbc.update("UPDATE staff_assignments SET is_homeroom = TRUE WHERE user_id ="
                + " (SELECT id FROM user_accounts WHERE username = 'kc-gv1')");

        assertThat(yearSetupService.getYearSetup(planned).classes())
                .extracting("teacherCount", "hasHomeroom")
                .containsExactly(tuple(2, true));

        jdbc.update("UPDATE user_accounts SET status = 'INACTIVE' WHERE username = 'kc-gv1'");

        assertThat(yearSetupService.getYearSetup(planned).classes())
                .extracting("teacherCount", "hasHomeroom")
                .containsExactly(tuple(1, false));
    }

    @Test
    @DisplayName(
            "SCH-04: phân công năm sau đã cắt ngay ngày đầu (đổi / thu hồi vai trò) không còn tính là giáo viên lớp")
    void teacherEndedAtStartOfPlannedYearIsNotCounted() {
        long planned = insertPlannedYearWithCampuses();
        long classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (" + campusA + ", "
                + planned + ", 'Lớp A2') RETURNING id");
        insertTeacher("kc-gv1", "GV đổi vai trò", classroom, "2027-08-02");
        insertTeacher("kc-gv2", "GV còn lại", classroom, "2027-08-02");
        long assignment = jdbc.queryForObject(
                "SELECT a.id FROM staff_assignments a JOIN user_accounts u ON u.id = a.user_id WHERE u.username = 'kc-gv1'",
                Long.class);

        // Như AccountProvisioningServiceImpl khi đổi / thu hồi vai trò: phân công chưa bắt đầu kết thúc ở ngày bắt đầu
        staffAssignmentService.endAssignment(assignment, java.time.LocalDate.of(2027, 8, 2));

        assertThat(yearSetupService.getYearSetup(planned).classes())
                .filteredOn(c -> c.name().equals("Lớp A2"))
                .singleElement()
                .satisfies(c -> assertThat(c.teacherCount()).isEqualTo(1));
        CurrentUser assigner = new CurrentUser(
                principal.userId(),
                principal.keycloakUserId(),
                principal.username(),
                principal.fullName(),
                principal.email(),
                principal.roles(),
                Set.of(com.carenest.utils.enums.Permission.STAFF_ASSIGNMENT_MANAGE));
        assertThat(teacherAssignmentService
                        .getTeacherAssignments(assigner, planned, "all")
                        .classes())
                .filteredOn(c -> c.name().equals("Lớp A2"))
                .singleElement()
                .satisfies(c -> assertThat(c.teachers()).extracting("fullName").containsExactly("GV còn lại"));
        assertThat(service.getCampusDetail(principal, campusA, planned).teachers())
                .extracting("fullName")
                .containsExactly("GV còn lại");
    }

    private long insertPlannedYearWithCampuses() {
        long planned = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2027-09-01', '2028-05-31', 'PLANNED') RETURNING id");
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address, phone, status) "
                        + "SELECT ?, id, code, name, short_name, address, phone, status FROM campuses",
                planned);
        return planned;
    }

    private void insertTeacher(String keycloakId, String fullName, long classroomId, String validFrom) {
        long userId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('"
                + keycloakId + "', '" + keycloakId + "', '" + fullName + "') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to) "
                        + "VALUES (?, 'TEACHER', ?, ?::date, '2028-05-31')",
                userId,
                classroomId,
                validFrom);
    }

    private long insertVicePrincipal(String keycloakId, String fullName, String validFrom) {
        long userId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('"
                + keycloakId + "', '" + keycloakId + "', '" + fullName + "') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, ?::date)",
                userId,
                campusB,
                validFrom);
        return userId;
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
