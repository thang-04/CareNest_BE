package com.carenest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.organization.StaffRole;
import com.carenest.security.CareNestRole;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Migration V2/V3 + query scope theo ngày trên PostgreSQL thật. Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ScopeQueryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StaffAssignmentService staffAssignmentService;
    @Autowired
    private ClassroomService classroomService;
    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private GuardianChildLinkService guardianChildLinkService;
    @Autowired
    private ChildAccessPolicy childAccessPolicy;

    // "Hôm nay" của policy cố định trùng dữ liệu test (phân công 2026-2027), không phụ thuộc ngày chạy test
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private long campusA;
    private long campusB;
    private long classA1;
    private long classB1;
    private long teacherA;
    private long teacherB;
    private long vicePrincipal;
    private long kitchenStaff;
    private long principal;
    private long parent;
    private long childStayingInA1;
    private long childMovingToB1;

    @BeforeEach
    void setUpScopeData() {
        campusA = insertId("INSERT INTO campuses (code, name) VALUES ('CS-A', 'Campus A') RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name) VALUES ('CS-B', 'Campus B') RETURNING id");
        long schoolYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31') RETURNING id");
        classA1 = insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'A1') RETURNING id",
                campusA, schoolYear);
        classB1 = insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'B1') RETURNING id",
                campusB, schoolYear);

        teacherA = insertUser("teacher.a");
        teacherB = insertUser("teacher.b");
        vicePrincipal = insertUser("viceprincipal.a");
        kitchenStaff = insertUser("kitchen.a");
        principal = insertUser("principal.dev");
        parent = insertUser("parent.a");

        insertClassroomAssignment(teacherA, classA1, "2026-09-01", null);
        // GV B chuyển từ A1 sang B1 giữa kỳ
        insertClassroomAssignment(teacherB, classA1, "2026-09-01", "2026-09-30");
        insertClassroomAssignment(teacherB, classB1, "2026-10-01", null);
        insertCampusAssignment(vicePrincipal, "VICE_PRINCIPAL", campusA);
        insertCampusAssignment(kitchenStaff, "KITCHEN_STAFF", campusA);
        jdbc.update("INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-09-01')",
                principal);

        childStayingInA1 = insertChild("Trẻ Demo 01");
        childMovingToB1 = insertChild("Trẻ Demo 02");
        insertEnrollment(childStayingInA1, classA1, "2026-09-01", null);
        insertEnrollment(childMovingToB1, classA1, "2026-09-01", "2026-10-14");
        insertEnrollment(childMovingToB1, classB1, "2026-10-15", null);
        jdbc.update("INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'MOTHER')",
                parent, childStayingInA1);
    }

    @Test
    void teacherAssignmentGrantsOnlyAssignedClassroom() {
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacherA, classA1, TODAY)).isTrue();
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacherA, classB1, TODAY)).isFalse();
    }

    @Test
    void endedAssignmentNoLongerGrantsAccessButBoundaryDaysAreInclusive() {
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacherB, classA1, TODAY)).isFalse();
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacherB, classA1, LocalDate.of(2026, 9, 30)))
                .isTrue();
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacherB, classB1, LocalDate.of(2026, 10, 1)))
                .isTrue();
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacherB, classB1, LocalDate.of(2026, 9, 30)))
                .isFalse();
    }

    @Test
    void campusAssignmentIsBoundToCampusAndStaffRole() {
        assertThat(staffAssignmentService.hasActiveCampusAssignment(vicePrincipal, StaffRole.VICE_PRINCIPAL, campusA,
                TODAY)).isTrue();
        assertThat(staffAssignmentService.hasActiveCampusAssignment(vicePrincipal, StaffRole.VICE_PRINCIPAL, campusB,
                TODAY)).isFalse();
        // Bếp cùng campus không được coi là phân công Hiệu phó
        assertThat(staffAssignmentService.hasActiveCampusAssignment(kitchenStaff, StaffRole.VICE_PRINCIPAL, campusA,
                TODAY)).isFalse();
        assertThat(staffAssignmentService.hasActiveCampusAssignment(kitchenStaff, StaffRole.KITCHEN_STAFF, campusA,
                TODAY)).isTrue();
    }

    @Test
    void principalAssignmentIsRequiredForSchoolScope() {
        assertThat(staffAssignmentService.hasActivePrincipalAssignment(principal, TODAY)).isTrue();
        assertThat(staffAssignmentService.hasActivePrincipalAssignment(vicePrincipal, TODAY)).isFalse();
    }

    @Test
    void childClassroomFollowsEnrollmentAtGivenDate() {
        assertThat(enrollmentService.findActiveClassroomIds(childMovingToB1, TODAY)).containsExactly(classA1);
        assertThat(enrollmentService.findActiveClassroomIds(childMovingToB1, LocalDate.of(2026, 10, 20)))
                .containsExactly(classB1);
        assertThat(enrollmentService.findActiveClassroomIds(childStayingInA1, LocalDate.of(2026, 8, 1))).isEmpty();
    }

    @Test
    void classroomResolvesToItsCampus() {
        assertThat(classroomService.findCampusId(classA1)).contains(campusA);
        assertThat(classroomService.findCampusId(-1L)).isEmpty();
    }

    @Test
    void guardianLinkIsPerChild() {
        assertThat(guardianChildLinkService.isGuardianOf(parent, childStayingInA1)).isTrue();
        assertThat(guardianChildLinkService.isGuardianOf(parent, childMovingToB1)).isFalse();
    }

    @Test
    void activeAssignmentSummaryExcludesEndedAssignments() {
        assertThat(staffAssignmentService.findActiveAssignments(teacherB, TODAY))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.staffRole()).isEqualTo(StaffRole.TEACHER);
                    assertThat(summary.classroomId()).isEqualTo(classB1);
                    assertThat(summary.validTo()).isNull();
                });
    }

    @Test
    void guardianChildIdsListOnlyLinkedChildren() {
        assertThat(guardianChildLinkService.findChildIdsOfGuardian(parent)).containsExactly(childStayingInA1);
        assertThat(guardianChildLinkService.findChildIdsOfGuardian(teacherA)).isEmpty();
    }

    @Test
    void databaseRejectsAssignmentWhoseScopeDoesNotMatchStaffRole() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                + "VALUES (?, 'TEACHER', ?, '2026-09-01')", teacherA, campusA))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void childAccessPolicyAgainstRealDatabase() {
        assertThat(childAccessPolicy.canViewChild(user(teacherA, CareNestRole.TEACHER), childStayingInA1)).isTrue();
        // GV B đã hết phân công A1 từ 30/09
        assertThat(childAccessPolicy.canViewChild(user(teacherB, CareNestRole.TEACHER), childStayingInA1)).isFalse();
        assertThat(childAccessPolicy.canViewChild(user(vicePrincipal, CareNestRole.VICE_PRINCIPAL), childStayingInA1))
                .isTrue();
        assertThat(childAccessPolicy.canViewChild(user(parent, CareNestRole.PARENT), childStayingInA1)).isTrue();
        assertThat(childAccessPolicy.canViewChild(user(parent, CareNestRole.PARENT), childMovingToB1)).isFalse();
        assertThat(childAccessPolicy.canViewChild(user(kitchenStaff, CareNestRole.KITCHEN_STAFF), childStayingInA1))
                .isFalse();
        assertThat(childAccessPolicy.canViewChild(user(principal, CareNestRole.PRINCIPAL), 999_999L)).isFalse();
    }

    private static CurrentUser user(long userId, CareNestRole role) {
        return new CurrentUser(userId, "kc-" + userId, "user" + userId, "Demo", null, Set.of(role));
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long insertUser(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                "kc-" + username, username, "Demo " + username);
    }

    private long insertChild(String fullName) {
        return insertId("INSERT INTO children (full_name, date_of_birth) VALUES (?, '2022-03-15') RETURNING id", fullName);
    }

    private void insertClassroomAssignment(long userId, long classroomId, String validFrom, String validTo) {
        jdbc.update("INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to) "
                + "VALUES (?, 'TEACHER', ?, ?::date, ?::date)", userId, classroomId, validFrom, validTo);
    }

    private void insertCampusAssignment(long userId, String staffRole, long campusId) {
        jdbc.update("INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                + "VALUES (?, ?, ?, '2026-09-01')", userId, staffRole, campusId);
    }

    private void insertEnrollment(long childId, long classroomId, String validFrom, String validTo) {
        jdbc.update("INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) "
                + "VALUES (?, ?, ?::date, ?::date)", childId, classroomId, validFrom, validTo);
    }
}
