package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.AgeGroupRequest;
import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.ClassResponse;
import com.carenest.dto.organization.ClassTeachersRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.YearTransitionService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.AttendanceCutoffService;
import com.carenest.service.organization.CampusService;
import com.carenest.service.organization.ClassManagementService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.TeacherAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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

/** Phạm vi theo năm học ở Cấu hình trường: năm đã kết thúc, năm chưa tới ngày chuẩn bị, dữ liệu riêng từng năm. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class SchoolYearScopeIntegrationTest {

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
    private ClassManagementService classService;

    @Autowired
    private CampusService campusService;

    @Autowired
    private TeacherAssignmentService teacherAssignmentService;

    @Autowired
    private AgeGroupService ageGroupService;

    @Autowired
    private SchoolYearService schoolYearService;

    @Autowired
    private AttendanceCutoffService cutoffService;

    @Autowired
    private YearTransitionService yearTransitionService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vicePrincipal;
    private CurrentUser oldTeacher;
    private long teacher1;
    private long teacher2;
    private long campusA;
    private long campusB;
    private long closedYear;
    private long activeYear;
    private long plannedYear;
    private long oldClass;
    private long activeClass;
    private long ageGroup3;

    @BeforeEach
    void setUp() {
        long principalId = user("principal");
        long vpId = user("vp");
        long oldTeacherId = user("oldteacher");
        teacher1 = user("teacher1");
        teacher2 = user("teacher2");
        campusA = insertId("INSERT INTO campuses (code, name, short_name, address, is_main)"
                + " VALUES ('CS-A', 'Campus A', 'Cơ sở A', 'Địa chỉ A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name, short_name, address)"
                + " VALUES ('CS-B', 'Campus B', 'Cơ sở B', 'Địa chỉ B') RETURNING id");
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        closedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status, closed_at) VALUES"
                + " ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED', TIMESTAMPTZ '2026-06-05 10:00:00+07')"
                + " RETURNING id");
        activeYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        plannedYear = insertId("INSERT INTO school_years (name, prep_date, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2027-08-05', '2027-09-06', '2028-05-31', 'PLANNED') RETURNING id");
        for (long y : List.of(closedYear, activeYear, plannedYear)) {
            jdbc.update(
                    "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address,"
                            + " phone, status, is_main) SELECT ?, id, code, name, short_name, address, phone, status,"
                            + " is_main FROM campuses",
                    y);
        }
        jdbc.update(
                "INSERT INTO school_year_campuses (school_year_id, campus_id, code, name, short_name, address, phone,"
                        + " is_main, status) SELECT ?, id, code, name, short_name, address, phone, is_main, status"
                        + " FROM campuses",
                closedYear);
        for (long y : List.of(activeYear, plannedYear)) {
            jdbc.update(
                    "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name,"
                            + " age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note)"
                            + " SELECT ?, id, name, short_name, age_from_months, age_to_months, meal_sessions,"
                            + " max_class_size, nutrition_note FROM age_groups",
                    y);
        }
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
                vpId,
                campusA);
        oldClass = classroom(closedYear, "Lớp cũ");
        activeClass = classroom(activeYear, "Lớp mới");
        long child = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on)"
                + " VALUES ('IT-OLD', 'Trẻ IT', '2022-01-01', 'MALE', 'ACTIVE', '2025-09-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2025-09-01',"
                        + " '2026-05-31')",
                child,
                oldClass);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to) "
                        + "VALUES (?, 'TEACHER', ?, '2025-09-01', '2026-05-31')",
                oldTeacherId,
                oldClass);
        principal = new CurrentUser(
                principalId,
                "kc-principal",
                "principal",
                "P",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                Set.of(Permission.STAFF_ASSIGNMENT_MANAGE, Permission.YEAR_PLACEMENT_MANAGE));
        vicePrincipal = new CurrentUser(
                vpId,
                "kc-vp",
                "vp",
                "VP",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.STAFF_ASSIGNMENT_MANAGE, Permission.YEAR_PLACEMENT_MANAGE),
                Set.of(Permission.STAFF_ASSIGNMENT_MANAGE, Permission.YEAR_PLACEMENT_MANAGE),
                Map.of());
        oldTeacher = new CurrentUser(
                oldTeacherId, "kc-oldteacher", "oldteacher", "GV", null, Set.of(CareNestRole.TEACHER), Set.of());
    }

    @Test
    @DisplayName("CFG-YR-02: năm đã kết thúc ⇒ sĩ số, giáo viên của lớp tính tại ngày kết thúc năm, không theo hôm nay")
    void closedYearStructureCountsAsOfYearEnd() {
        ClassResponse cls = classService.getSchoolStructure(principal, closedYear, null).classes().stream()
                .filter(c -> c.id() == oldClass)
                .findFirst()
                .orElseThrow();

        assertThat(cls.childCount()).isEqualTo(1);
        assertThat(cls.teacherIds()).containsExactly(oldTeacher.userId());
    }

    @Test
    @DisplayName("CFG-YR-02: số trẻ theo điểm trường của năm đã kết thúc tính tại ngày kết thúc năm")
    void closedYearCampusChildCountAsOfYearEnd() {
        CampusResponse campus = campusService.getCampuses(principal, closedYear).stream()
                .filter(c -> c.id() == campusA)
                .findFirst()
                .orElseThrow();

        assertThat(campus.childCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AUTH-03: GV từng dạy lớp ở năm đã đóng xem (chỉ xem) cấu trúc lớp mình đã dạy năm đó")
    void formerTeacherSeesClosedYearClass() {
        List<ClassResponse> classes =
                classService.getSchoolStructure(oldTeacher, closedYear, null).classes();

        assertThat(classes).extracting(ClassResponse::id).containsExactly(oldClass);
    }

    @Test
    @DisplayName("SCH-04: năm đang hoạt động đã qua ngày kết thúc (chưa đóng) ⇒ không phân công GV mới")
    void assigningTeachersAfterYearEndIsRejected() {
        jdbc.update("UPDATE school_years SET end_date = '2026-10-01' WHERE id = ?", activeYear);

        expectCode(
                () -> teacherAssignmentService.assignClassTeachers(
                        principal, activeClass, new ClassTeachersRequest(List.of(teacher1, teacher2), teacher1)),
                ApiCode.ASSIGNMENT_YEAR_ENDED);
    }

    @Test
    @DisplayName("CFG-YR-05: năm sắp diễn ra chưa tới ngày mở chuẩn bị chỉ HT thấy; PHT nhận như không có năm đó")
    void vicePrincipalCannotReachPlannedYearBeforePrepDate() {
        expectCode(
                () -> classService.getSchoolStructure(vicePrincipal, plannedYear, null), ApiCode.SCHOOL_YEAR_NOT_FOUND);
        expectCode(
                () -> teacherAssignmentService.getTeacherAssignments(vicePrincipal, plannedYear, null),
                ApiCode.SCHOOL_YEAR_NOT_FOUND);
        expectCode(
                () -> teacherAssignmentService.getTeamLeaders(vicePrincipal, plannedYear),
                ApiCode.SCHOOL_YEAR_NOT_FOUND);
        expectCode(() -> schoolYearService.getHolidays(vicePrincipal, plannedYear), ApiCode.SCHOOL_YEAR_NOT_FOUND);
        expectCode(() -> ageGroupService.getYearAgeGroups(vicePrincipal, plannedYear), ApiCode.SCHOOL_YEAR_NOT_FOUND);
        expectCode(() -> cutoffService.getCutoff(vicePrincipal, plannedYear), ApiCode.SCHOOL_YEAR_NOT_FOUND);
        expectCode(
                () -> yearTransitionService.getPlacements(vicePrincipal, plannedYear), ApiCode.SCHOOL_YEAR_NOT_FOUND);
        long plannedClass = classroom(plannedYear, "Lớp năm sau");
        expectCode(
                () -> teacherAssignmentService.assignClassTeachers(
                        vicePrincipal, plannedClass, new ClassTeachersRequest(List.of(teacher1), teacher1)),
                ApiCode.SCHOOL_YEAR_NOT_FOUND);

        assertThat(classService.getSchoolStructure(principal, plannedYear, null).classes())
                .extracting(ClassResponse::id)
                .contains(plannedClass);
        assertThat(cutoffService.getCutoff(principal, plannedYear)).isNotNull();
    }

    // ag-0 không có sẵn trong năm của test nên thêm được vào năm sắp diễn ra
    private AgeGroupRequest standardRequest() {
        long ag0 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-0'", Long.class);
        jdbc.update("DELETE FROM school_year_age_group_members WHERE age_group_id = ?", ag0);
        return new AgeGroupRequest("Nhóm", "N", 3, 13, List.of(MealSession.LUNCH), 15, "", ag0);
    }

    @Test
    @DisplayName("Thêm nhóm tuổi không kèm năm (client cũ) chỉ vào năm sắp diễn ra, không đụng năm đang hoạt động")
    void legacyCreateAgeGroupOnlyTouchesPlannedYears() {
        long id = ageGroupService.createAgeGroup(principal, standardRequest()).id();

        assertThat(inYear(plannedYear, id)).isTrue();
        assertThat(inYear(activeYear, id)).isFalse();
    }

    @Test
    @DisplayName("Thêm nhóm tuổi không kèm năm khi không có năm sắp diễn ra ⇒ từ chối")
    void legacyCreateAgeGroupWithoutPlannedYearIsRejected() {
        jdbc.update("DELETE FROM school_years WHERE id = ?", plannedYear);

        expectCode(
                () -> ageGroupService.createAgeGroup(principal, standardRequest()),
                ApiCode.AGE_GROUP_CREATE_YEAR_NOT_PLANNED);
    }

    @Test
    @DisplayName("Sửa nhóm tuổi không kèm năm (client cũ) chỉ đổi năm chứa nhóm đó, không đổi bảng chung")
    void legacyUpdateAgeGroupKeepsSharedRow() {
        long id = insertId("INSERT INTO age_groups (code, name, short_name, sort_order, age_from_months, age_to_months,"
                + " meal_sessions, max_class_size, nutrition_note, standard_group, regulation_ref, created_at,"
                + " updated_at) VALUES ('ag-it', 'Nhà trẻ nhỏ', 'NT nhỏ', 90, 0, 3, 'LUNCH', 15, '', FALSE, '',"
                + " now(), now()) RETURNING id");
        jdbc.update(
                "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name,"
                        + " age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note)"
                        + " SELECT ?, id, name, short_name, age_from_months, age_to_months, meal_sessions,"
                        + " max_class_size, nutrition_note FROM age_groups WHERE id = ?",
                plannedYear,
                id);

        ageGroupService.updateAgeGroup(
                principal, id, new AgeGroupRequest("Nhà trẻ bé", "NT bé", 0, 3, List.of(MealSession.LUNCH), 12, ""));

        assertThat(jdbc.queryForObject("SELECT name FROM age_groups WHERE id = ?", String.class, id))
                .isEqualTo("Nhà trẻ nhỏ");
        assertThat(jdbc.queryForObject(
                        "SELECT name FROM school_year_age_group_members WHERE school_year_id = ? AND age_group_id = ?",
                        String.class,
                        plannedYear,
                        id))
                .isEqualTo("Nhà trẻ bé");
    }

    @Test
    @DisplayName("Điểm trường chính theo từng năm: đổi ở năm sắp diễn ra không đổi điểm chính năm đang hoạt động")
    void mainCampusIsPerYear() {
        campusService.updateCampus(
                principal, campusB, new CampusRequest("CS-B", "Campus B", "B", "Địa chỉ B", null, true), plannedYear);

        assertThat(mainOf(plannedYear)).isEqualTo(campusB);
        assertThat(mainOf(activeYear)).isEqualTo(campusA);
        assertThat(jdbc.queryForObject("SELECT is_main FROM campuses WHERE id = ?", Boolean.class, campusA))
                .isTrue();
    }

    @Test
    @DisplayName(
            "Cần chốt 24/11/2026: tên ngắn điểm trường không trùng (bỏ khoảng trắng thừa, không phân biệt hoa thường)")
    void campusShortNameMustBeUnique() {
        expectCode(
                () -> campusService.createCampus(
                        principal,
                        new CampusRequest("CS-C", "Campus C", "  cơ   SỞ a ", "Địa chỉ C", null, false),
                        null),
                ApiCode.CAMPUS_SHORT_NAME_EXISTS);
        expectCode(
                () -> campusService.updateCampus(
                        principal,
                        campusB,
                        new CampusRequest("CS-B", "Campus B", "CƠ SỞ  A", "Địa chỉ B", null, null),
                        null),
                ApiCode.CAMPUS_SHORT_NAME_EXISTS);
        expectCode(
                () -> campusService.updateCampus(
                        principal,
                        campusB,
                        new CampusRequest("CS-B", "Campus B", "cơ sở a", "Địa chỉ B", null, null),
                        plannedYear),
                ApiCode.CAMPUS_SHORT_NAME_EXISTS);

        CampusResponse kept = campusService.updateCampus(
                principal,
                campusB,
                new CampusRequest("CS-B", "Campus B 2", " Cơ  sở B ", "Địa chỉ B", null, null),
                null);

        assertThat(kept.shortName()).isEqualTo("Cơ sở B");
    }

    private long mainOf(long yearId) {
        return campusService.getCampuses(principal, yearId).stream()
                .filter(CampusResponse::isMain)
                .map(CampusResponse::id)
                .findFirst()
                .orElseThrow();
    }

    private boolean inYear(long yearId, long ageGroupId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT count(*) > 0 FROM school_year_age_group_members WHERE school_year_id = ? AND age_group_id = ?",
                Boolean.class,
                yearId,
                ageGroupId));
    }

    private long classroom(long yearId, String name) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, capacity) VALUES ("
                + campusA + ", " + yearId + ", '" + name + "', " + ageGroup3 + ", 25) RETURNING id");
    }

    private long user(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + " IT') RETURNING id");
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(apiCode);
    }
}
