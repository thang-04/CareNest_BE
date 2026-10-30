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

/** Phân công GV/tổ trưởng trên PostgreSQL thật (V18) và tác động lên scope của security. Cần Docker. */
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
        // teacher3 phải được gỡ khỏi lớp A2 trước (mỗi giáo viên một lớp)
        endAssignments(teacher3);
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
    void closedYearShowsTeachersAsOfClosingWithNames() {
        // Năm kết thúc hôm qua; phân công của teacher2 kết thúc hôm nay (sau khi đóng năm) vẫn phải hiện
        jdbc.update(
                "UPDATE school_years SET status = 'CLOSED', closed_at = TIMESTAMPTZ '2026-10-21 10:00:00+07' WHERE id = ?",
                year);
        jdbc.update(
                "UPDATE staff_assignments SET valid_to = ? WHERE user_id = ? AND classroom_id = ?",
                TODAY.minusDays(1),
                teacher2,
                classA1);

        ClassTeachersResponse a1 = service.getTeacherAssignments(principal, year, null).classes().stream()
                .filter(c -> c.id() == classA1)
                .findFirst()
                .orElseThrow();

        assertThat(a1.teacherIds()).containsExactlyInAnyOrder(teacher1, teacher2);
        assertThat(a1.teachers())
                .extracting(ClassTeachersResponse.Teacher::fullName)
                .doesNotContainNull();
    }

    @Test
    void removingAllTeachersOfActiveYearNeedsReplacement() {
        expectError(
                () -> service.assignClassTeachers(principal, classA1, new ClassTeachersRequest(List.of(), null)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void emptyListRemovesAllTeachersOfPlannedYearAndKeepsHistory() {
        jdbc.update("UPDATE school_years SET status = 'PLANNED' WHERE id = ?", year);
        ClassTeachersResponse result =
                service.assignClassTeachers(principal, classA1, new ClassTeachersRequest(List.of(), null));

        assertThat(result.teacherIds()).isEmpty();
        assertThat(result.homeroomTeacherId()).isNull();
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacher1, classA1, TODAY))
                .isFalse();
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacher1, classA1, TODAY.minusDays(1)))
                .isTrue();
        // Giáo viên vừa gỡ vẫn chọn được để phân công lại
        assertThat(service.getTeacherAssignments(principal, year, null).teachers())
                .extracting(t -> t.id())
                .contains(teacher1, teacher2);
    }

    @Test
    void remainingTeacherBecomesHomeroomWhenNoneChosen() {
        ClassTeachersResponse result =
                service.assignClassTeachers(principal, classA1, new ClassTeachersRequest(List.of(teacher2), null));

        assertThat(result.teacherIds()).containsExactly(teacher2);
        assertThat(result.homeroomTeacherId()).isEqualTo(teacher2);
    }

    @Test
    void historyListsEndedAndCurrentAssignmentsNewestFirst() {
        endAssignments(teacher3);
        service.assignClassTeachers(
                principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher3), teacher3));

        var history = service.getClassTeacherHistory(principal, classA1);

        // teacher3 mới phân công hôm nay (đang phụ trách), teacher2 kết thúc hôm qua, teacher1 giữ nguyên
        assertThat(history.getFirst().userId()).isEqualTo(teacher3);
        assertThat(history.getFirst().current()).isTrue();
        assertThat(history.getFirst().homeroom()).isTrue();
        assertThat(history)
                .filteredOn(h -> h.userId() == teacher2)
                .singleElement()
                .satisfies(h -> assertThat(h.validTo()).isEqualTo(TODAY.minusDays(1)));
    }

    @Test
    void lockedTeacherIsHiddenAndRejected() {
        jdbc.update("UPDATE user_accounts SET status = 'INACTIVE' WHERE id = ?", teacher2);

        assertThat(service.getTeacherAssignments(principal, year, null).teachers())
                .extracting(t -> t.id())
                .doesNotContain(teacher2);
        expectError(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), teacher1)),
                ApiCode.TEACHER_ACCOUNT_LOCKED);
        expectError(
                () -> service.assignTeamLeader(principal, new TeamLeaderRequest(year, ageGroup3, teacher2)),
                ApiCode.TEACHER_ACCOUNT_LOCKED);
    }

    @Test
    void oneTeacherIsAllowedWithHomeroom() {
        ClassTeachersResponse result =
                service.assignClassTeachers(principal, classA1, new ClassTeachersRequest(List.of(teacher1), teacher1));

        assertThat(result.teacherIds()).containsExactly(teacher1);
        assertThat(result.homeroomTeacherId()).isEqualTo(teacher1);
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacher2, classA1, TODAY))
                .isFalse();
    }

    @Test
    void homeroomIsOptional() {
        ClassTeachersResponse result = service.assignClassTeachers(
                principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), null));

        assertThat(result.teacherIds()).containsExactlyInAnyOrder(teacher1, teacher2);
        assertThat(result.homeroomTeacherId()).isNull();
    }

    @Test
    void teacherOfAnotherClassIsConflict() {
        expectError(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher3), teacher1)),
                ApiCode.CONFLICT);
    }

    @Test
    void teacherCampusFollowsLatestAssignment() {
        // teacherB từng dạy lớp A1 (cơ sở A) trước khi chuyển sang B1 ⇒ thuộc cơ sở B
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to) "
                        + "VALUES (?, 'TEACHER', ?, '2026-08-01', '2026-08-31')",
                teacherB,
                classA1);

        var candidate = service.getTeacherAssignments(principal, year, null).teachers().stream()
                .filter(t -> t.id() == teacherB)
                .findFirst()
                .orElseThrow();
        assertThat(candidate.campusIds()).containsExactly(candidate.campusId());
        assertThat(service.getTeacherAssignments(principal, year, null).classes().stream()
                        .filter(c -> c.id() == classA1)
                        .findFirst()
                        .orElseThrow()
                        .campusId())
                .isNotEqualTo(candidate.campusId());
        expectError(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacherB), teacher1)),
                ApiCode.BAD_REQUEST);
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

    // Kết thúc mọi phân công của giáo viên từ hôm qua (để phân công sang lớp khác)
    private void endAssignments(long userId) {
        jdbc.update("UPDATE staff_assignments SET valid_to = ? WHERE user_id = ?", TODAY.minusDays(1), userId);
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
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(apiCode.getHttpStatus());
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
