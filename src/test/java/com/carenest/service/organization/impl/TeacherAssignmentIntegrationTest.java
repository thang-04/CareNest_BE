package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.ClassTeachersRequest;
import com.carenest.dto.organization.ClassTeachersResponse;
import com.carenest.dto.organization.TeacherAssignmentResponse;
import com.carenest.dto.organization.TeamLeaderRequest;
import com.carenest.dto.organization.TeamLeaderResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.service.organization.TeacherAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

/** Phân công GV/tổ trưởng trên PostgreSQL thật (V15) và tác động lên scope của security. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class TeacherAssignmentIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

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
    private TeacherAssignmentService service;

    @Autowired
    private StaffAssignmentService staffAssignmentService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long teacher1;
    private long teacher2;
    private long teacher3;
    private long teacherB;
    private long classA1;
    private long classA2;
    private long year;
    private long ageGroup3;
    private long ageGroup4;

    @BeforeEach
    void setUp() {
        long principalId = user("principal");
        teacher1 = user("teacher1");
        teacher2 = user("teacher2");
        teacher3 = user("teacher3");
        teacherB = user("teacherB");
        long campusA =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Campus A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name) VALUES ('CS-B', 'Campus B') RETURNING id");
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        ageGroup4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        classA1 = classroom(campusA, "Lớp A1");
        classA2 = classroom(campusA, "Lớp A2");
        long classB1 = classroom(campusB, "Lớp B1");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        teacherOf(teacher1, classA1);
        teacherOf(teacher2, classA1);
        teacherOf(teacher3, classA2);
        teacherOf(teacherB, classB1);
        principal =
                new CurrentUser(principalId, "kc-principal", "principal", "P", null, Set.of(CareNestRole.PRINCIPAL));
    }

    @Test
    void overviewListsClassTeachersAndCampusCandidates() {
        TeacherAssignmentResponse overview = service.getTeacherAssignments(principal, year, null);

        assertThat(overview.classes()).hasSize(3);
        ClassTeachersResponse a1 = overview.classes().stream()
                .filter(c -> c.id() == classA1)
                .findFirst()
                .orElseThrow();
        assertThat(a1.teacherIds()).containsExactlyInAnyOrder(teacher1, teacher2);
        assertThat(overview.teachers()).hasSize(4);
    }

    @Test
    void replacingTeacherEndsOldAssignmentAndGrantsNewScope() {
        ClassTeachersResponse result = service.assignClassTeachers(
                principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher3), teacher3));

        assertThat(result.teacherIds()).containsExactlyInAnyOrder(teacher1, teacher3);
        assertThat(result.homeroomTeacherId()).isEqualTo(teacher3);
        // teacher2 hết quyền với lớp A1 từ hôm nay, teacher3 có quyền ngay
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacher2, classA1, TODAY))
                .isFalse();
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacher2, classA1, TODAY.minusDays(1)))
                .isTrue();
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacher3, classA1, TODAY))
                .isTrue();
    }

    @Test
    void teacherOfOtherCampusIsRejected() {
        expectError(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacherB), teacher1)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void homeroomMustBeOneOfTheTeachers() {
        expectError(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), teacher3)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void sameTeacherTwiceIsRejected() {
        expectError(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher1), teacher1)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void oneLeaderPerAgeGroupAndOneGroupPerTeacher() {
        service.assignTeamLeader(principal, new TeamLeaderRequest(year, ageGroup3, teacher1));
        // teacher1 chuyển sang làm tổ trưởng nhóm 4 ⇒ nhóm 3 không còn tổ trưởng
        TeamLeaderResponse response =
                service.assignTeamLeader(principal, new TeamLeaderRequest(year, ageGroup4, teacher1));

        assertThat(response.teamLeaders())
                .extracting(TeamLeaderResponse.Leader::ageGroupId, TeamLeaderResponse.Leader::userId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ageGroup4, teacher1));
        assertThat(staffAssignmentService.findActiveTeamLeaderAgeGroupIds(teacher1, TODAY))
                .containsExactly(ageGroup4);
    }

    @Test
    void removingLeader() {
        service.assignTeamLeader(principal, new TeamLeaderRequest(year, ageGroup3, teacher2));

        TeamLeaderResponse response = service.assignTeamLeader(principal, new TeamLeaderRequest(year, ageGroup3, null));

        assertThat(response.teamLeaders()).isEmpty();
        assertThat(staffAssignmentService.findActiveTeamLeaderAgeGroupIds(teacher2, TODAY))
                .isEmpty();
    }

    @Test
    void vicePrincipalCannotAssign() {
        CurrentUser vicePrincipal =
                new CurrentUser(999L, "kc-vp", "vp", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));

        expectError(
                () -> service.assignTeamLeader(vicePrincipal, new TeamLeaderRequest(year, ageGroup3, teacher1)),
                ApiCode.FORBIDDEN);
    }

    private long user(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + " IT') RETURNING id");
    }

    private long classroom(long campusId, String name) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, capacity) VALUES ("
                + campusId + ", " + year + ", '" + name + "', " + ageGroup3 + ", 25) RETURNING id");
    }

    private void teacherOf(long userId, long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01')",
                userId,
                classroomId);
    }

    private static void expectError(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
