package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.ClassTeachersRequest;
import com.carenest.dto.organization.ClassTeachersResponse;
import com.carenest.dto.organization.NewStaffAssignment;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.dto.organization.TeacherAssignmentResponse;
import com.carenest.dto.organization.TeamLeaderRequest;
import com.carenest.dto.organization.TeamLeaderResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.service.organization.TeacherAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

    @Autowired
    private EntityManager entityManager;

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
        principal = new CurrentUser(
                principalId,
                "kc-principal",
                "principal",
                "P",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                Set.of(Permission.STAFF_ASSIGNMENT_MANAGE));
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
    @DisplayName("SCH-04: năm đang học mỗi lớp đúng 2 GV đứng lớp và có chủ nhiệm (Cần chốt 10/11/2026)")
    void activeYearNeedsExactlyTwoTeachersAndHomeroom() {
        expectError(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1), teacher1)),
                ApiCode.CLASS_NEEDS_TWO_TEACHERS);
        expectError(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), null)),
                ApiCode.HOMEROOM_REQUIRED);
        assertThat(service.assignClassTeachers(
                                principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), teacher2))
                        .homeroomTeacherId())
                .isEqualTo(teacher2);
    }

    @Test
    void remainingTeacherBecomesHomeroomWhenNoneChosenInPlannedYear() {
        // Năm sắp tới HT xếp dần: lưu tạm 1 GV được, người còn lại làm chủ nhiệm
        jdbc.update("UPDATE school_years SET status = 'PLANNED' WHERE id = ?", year);
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
    void oneTeacherIsAllowedWithHomeroomInPlannedYear() {
        jdbc.update("UPDATE school_years SET status = 'PLANNED' WHERE id = ?", year);
        ClassTeachersResponse result =
                service.assignClassTeachers(principal, classA1, new ClassTeachersRequest(List.of(teacher1), teacher1));

        assertThat(result.teacherIds()).containsExactly(teacher1);
        assertThat(result.homeroomTeacherId()).isEqualTo(teacher1);
        assertThat(staffAssignmentService.hasActiveTeacherAssignment(teacher2, classA1, TODAY))
                .isFalse();
    }

    @Test
    void homeroomIsOptionalInPlannedYear() {
        jdbc.update("UPDATE school_years SET status = 'PLANNED' WHERE id = ?", year);
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
        // PHT cơ sở A không kéo được GV đang thuộc cơ sở B (HT thì được, xem principalMayMoveTeacherAcrossCampuses)
        CurrentUser vpA = vicePrincipalOf("vpA", "CS-A");
        expectError(
                () -> service.assignClassTeachers(
                        vpA, classA1, new ClassTeachersRequest(List.of(teacher1, teacherB), teacher1)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void newTeacherWithoutAssignmentIsCandidateOfEveryCampus() {
        // AUTH-15: tạo GV không kèm lớp ⇒ chưa thuộc điểm trường nào, xếp được vào lớp đầu tiên ở mọi điểm trường
        long newTeacher = user("teacherNew");
        jdbc.update("UPDATE user_accounts SET primary_role = 'TEACHER' WHERE id = ?", newTeacher);
        jdbc.update("UPDATE user_accounts SET primary_role = 'TEACHER' WHERE id = ?", newTeacher);
        long campusA = jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, classA1);

        var candidate = service.getTeacherAssignments(principal, year, String.valueOf(campusA)).teachers().stream()
                .filter(t -> t.id() == newTeacher)
                .findFirst()
                .orElseThrow();
        assertThat(candidate.campusId()).isNull();
        assertThat(candidate.campusIds()).isEmpty();

        ClassTeachersResponse result = service.assignClassTeachers(
                principal, classA1, new ClassTeachersRequest(List.of(teacher1, newTeacher), teacher1));
        assertThat(result.teacherIds()).containsExactlyInAnyOrder(teacher1, newTeacher);
    }

    @Test
    void vicePrincipalCannotPullTeacherOfOtherCampus() {
        CurrentUser vpA = vicePrincipalOf("vpA", "CS-A");
        expectError(
                () -> service.assignClassTeachers(
                        vpA, classA1, new ClassTeachersRequest(List.of(teacher1, teacherB), teacher1)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void vicePrincipalAssignsTeachersOnlyInOwnCampus() {
        CurrentUser vpA = vicePrincipalOf("vpA", "CS-A");
        CurrentUser vpB = vicePrincipalOf("vpB", "CS-B");

        // AUTH-16: PHT không phân công lớp của điểm trường khác
        expectError(
                () -> service.assignClassTeachers(
                        vpB, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), teacher1)),
                ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED);

        ClassTeachersResponse result = service.assignClassTeachers(
                vpA, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), teacher2));
        assertThat(result.homeroomTeacherId()).isEqualTo(teacher2);
    }

    @Test
    void schoolScopeLetsVicePrincipalAssignEveryCampusAndRevokeBlocksIt() {
        CurrentUser vpB = vicePrincipalOf("vpB", "CS-B");
        // AUTH-13: HT cấp STAFF_ASSIGNMENT_MANAGE phạm vi SCHOOL ⇒ PHT cơ sở B phân công được lớp cơ sở A
        CurrentUser schoolWide = new CurrentUser(
                vpB.userId(),
                vpB.keycloakUserId(),
                vpB.username(),
                vpB.fullName(),
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.STAFF_ASSIGNMENT_MANAGE),
                Set.of(Permission.STAFF_ASSIGNMENT_MANAGE));
        assertThat(service.getTeacherAssignments(schoolWide, year, null).classes())
                .hasSize(3);
        ClassTeachersResponse result = service.assignClassTeachers(
                schoolWide, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), teacher1));
        assertThat(result.teacherIds()).containsExactlyInAnyOrder(teacher1, teacher2);
        // Phạm vi CAMPUS chỉ thấy lớp cơ sở mình
        assertThat(service.getTeacherAssignments(vpB, year, null).classes()).hasSize(1);

        // HT thu hồi mã quyền ⇒ còn là PHT vẫn bị chặn
        CurrentUser revoked = new CurrentUser(
                vpB.userId(),
                vpB.keycloakUserId(),
                vpB.username(),
                vpB.fullName(),
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of());
        assertThat(service.getTeacherAssignments(revoked, year, null).classes()).isEmpty();
        expectError(
                () -> service.assignClassTeachers(
                        revoked, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), teacher1)),
                ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED);
    }

    private CurrentUser vicePrincipalOf(String username, String campusCode) {
        long userId = user(username);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "SELECT ?, 'VICE_PRINCIPAL', id, '2026-01-01' FROM campuses WHERE code = ?",
                userId,
                campusCode);
        return new CurrentUser(
                userId,
                "kc-" + username,
                username,
                "VP",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.STAFF_ASSIGNMENT_MANAGE));
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
        CurrentUser vicePrincipal = new CurrentUser(
                999L,
                "kc-vp",
                "vp",
                "VP",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.STAFF_ASSIGNMENT_MANAGE));

        expectError(
                () -> service.assignTeamLeader(vicePrincipal, new TeamLeaderRequest(year, ageGroup3, teacher1)),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("AUTH-24: /staff-assignments áp cùng luật xếp lớp — GV đang dạy chính lớp khác bị từ chối")
    void staffAssignmentRejectsSecondMainClass() {
        expectCode(
                () -> staffAssignmentService.createAssignment(teacherAssignment(teacher1, classA2, null)),
                ApiCode.TEACHER_ALREADY_ASSIGNED);
        // Ngày kết thúc trùng/ sau ngày cuối năm vẫn là dạy chính
        expectCode(
                () -> staffAssignmentService.createAssignment(
                        teacherAssignment(teacher1, classA2, LocalDate.of(2027, 5, 31))),
                ApiCode.TEACHER_ALREADY_ASSIGNED);
        // Không suy dạy thay từ ngày kết thúc: thiếu cờ dạy thay thì vẫn là dạy chính
        expectCode(
                () -> staffAssignmentService.createAssignment(teacherAssignment(teacher1, classA2, TODAY.plusDays(14))),
                ApiCode.TEACHER_ALREADY_ASSIGNED);
    }

    @Test
    @DisplayName("AUTH-24: phân công đánh dấu dạy thay được miễn, ghi người phân công")
    void substituteWithEndDateIsExemptAndRecordsAssigner() {
        StaffAssignmentResponse created =
                staffAssignmentService.createAssignment(substituteAssignment(teacher1, classA2, TODAY.plusDays(14)));

        assertThat(created.validTo()).isEqualTo(TODAY.plusDays(14));
        assertThat(created.substitute()).isTrue();
        assertThat(created.replacedTeacherId()).isEqualTo(teacher3);
        assertThat(jdbc.queryForObject(
                        "SELECT assigned_by FROM staff_assignments WHERE id = ?", Long.class, created.id()))
                .isEqualTo(principal.userId());
        // GV dạy thay ở A2 vẫn được giữ khi HT sửa GV lớp chính A1 và khi sửa GV lớp A2
        service.assignClassTeachers(
                principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacher2), teacher2));
        ClassTeachersResponse a2 = service.assignClassTeachers(
                principal, classA2, new ClassTeachersRequest(List.of(teacher3, teacher1), teacher3));
        assertThat(a2.teacherIds()).containsExactlyInAnyOrder(teacher3, teacher1);
    }

    @Test
    @DisplayName("SCH-04 (Cần chốt 11/11/2026): dạy thay được lấy GV cơ sở khác; dạy chính lẻ vẫn trong cơ sở của lớp")
    void substituteMayComeFromAnotherCampus() {
        StaffAssignmentResponse created =
                staffAssignmentService.createAssignment(substituteAssignment(teacherB, classA2, TODAY.plusDays(7)));
        assertThat(created.substitute()).isTrue();
        assertThat(created.userId()).isEqualTo(teacherB);
    }

    @Test
    @DisplayName("AUTH-16: HT xếp GV điểm trường khác vào lớp, chỉ còn chặn khi GV đang dạy chính lớp khác")
    void principalMayMoveTeacherAcrossCampuses() {
        // GV cơ sở B không bị chặn vì điểm trường; vẫn đang dạy chính lớp B1 nên bị chặn theo AUTH-24
        expectCode(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1, teacherB), teacher1)),
                ApiCode.TEACHER_ALREADY_ASSIGNED);
    }

    @Test
    @DisplayName("AUTH-24: GV chưa có lớp nhận dạy chính qua /staff-assignments ⇒ hiệu lực tới hết năm học")
    void mainAssignmentRunsToYearEnd() {
        long newTeacher = user("teacherNew");
        jdbc.update("UPDATE user_accounts SET primary_role = 'TEACHER' WHERE id = ?", newTeacher);

        StaffAssignmentResponse created =
                staffAssignmentService.createAssignment(teacherAssignment(newTeacher, classA2, null));

        assertThat(created.validTo()).isEqualTo(LocalDate.of(2027, 5, 31));
        // Sau đó không nhận thêm lớp dạy chính ở cả đường PUT
        expectCode(
                () -> service.assignClassTeachers(
                        principal, classA1, new ClassTeachersRequest(List.of(teacher1, newTeacher), teacher1)),
                ApiCode.TEACHER_ALREADY_ASSIGNED);
    }

    @Test
    @DisplayName("AUTH-24: không kéo GV cơ sở khác, tài khoản khóa, năm đã kết thúc ⇒ từ chối ở /staff-assignments")
    void staffAssignmentDeniedCases() {
        expectCode(
                () -> staffAssignmentService.createAssignment(teacherAssignment(teacherB, classA2, TODAY.plusDays(7))),
                ApiCode.TEACHER_WRONG_CAMPUS);
        jdbc.update("UPDATE user_accounts SET status = 'INACTIVE' WHERE id = ?", teacher2);
        expectCode(
                () -> staffAssignmentService.createAssignment(teacherAssignment(teacher2, classA2, TODAY.plusDays(7))),
                ApiCode.TEACHER_ACCOUNT_LOCKED);
        jdbc.update("UPDATE school_years SET status = 'CLOSED' WHERE id = ?", year);
        entityManager.clear();
        expectCode(
                () -> staffAssignmentService.createAssignment(teacherAssignment(teacher1, classA2, TODAY.plusDays(7))),
                ApiCode.SCHOOL_YEAR_CLOSED);
    }

    @Test
    @DisplayName("AUTH-24: dạy thay bắt buộc ngày kết thúc và chỉ cho giáo viên")
    void substituteNeedsEndDateAndTeacherRole() {
        expectCode(
                () -> staffAssignmentService.createAssignment(substituteAssignment(teacher1, classA2, null)),
                ApiCode.SUBSTITUTE_END_DATE_REQUIRED);
        Long campus = jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, classA2);
        expectCode(
                () -> staffAssignmentService.createAssignment(new NewStaffAssignment(
                        teacher3,
                        StaffRole.KITCHEN_STAFF,
                        campus,
                        null,
                        TODAY,
                        TODAY.plusDays(7),
                        principal.userId(),
                        true)),
                ApiCode.SUBSTITUTE_TEACHER_ONLY);
    }

    private NewStaffAssignment teacherAssignment(long teacherId, long classroomId, LocalDate validTo) {
        return new NewStaffAssignment(
                teacherId, StaffRole.TEACHER, null, classroomId, TODAY, validTo, principal.userId(), false);
    }

    // Dạy thay ở lớp A2 cho teacher3 (GV đứng lớp A2)
    private NewStaffAssignment substituteAssignment(long teacherId, long classroomId, LocalDate validTo) {
        return substituteFor(teacherId, classroomId, validTo, teacher3);
    }

    private NewStaffAssignment substituteFor(long teacherId, long classroomId, LocalDate validTo, Long replaced) {
        return new NewStaffAssignment(
                teacherId, StaffRole.TEACHER, null, classroomId, TODAY, validTo, principal.userId(), true, replaced);
    }

    @Test
    @DisplayName("AUTH-16, SCH-04: dạy thay phải chọn dạy thay cho GV đứng lớp; mỗi GV một người dạy thay cùng lúc")
    void substituteMustNameReplacedClassTeacher() {
        LocalDate end = TODAY.plusDays(7);
        expectError(
                () -> staffAssignmentService.createAssignment(substituteFor(teacher1, classA2, end, null)),
                ApiCode.SUBSTITUTE_REPLACED_REQUIRED);
        // teacher2 không đứng lớp A2
        expectError(
                () -> staffAssignmentService.createAssignment(substituteFor(teacher1, classA2, end, teacher2)),
                ApiCode.SUBSTITUTE_REPLACED_NOT_IN_CLASS);
        // GV đứng lớp A2 không dạy thay cho lớp mình
        long teacher4 = user("teacher4");
        teacherOf(teacher4, classA2);
        expectError(
                () -> staffAssignmentService.createAssignment(substituteFor(teacher4, classA2, end, teacher3)),
                ApiCode.SUBSTITUTE_IS_CLASS_TEACHER);

        StaffAssignmentResponse created =
                staffAssignmentService.createAssignment(substituteFor(teacher1, classA2, end, teacher3));
        assertThat(created.replacedTeacherId()).isEqualTo(teacher3);
        // Đã có người dạy thay cho teacher3 trong khoảng này
        long teacher5 = user("teacher5");
        jdbc.update("UPDATE user_accounts SET primary_role = 'TEACHER' WHERE id = ?", teacher5);
        expectError(
                () -> staffAssignmentService.createAssignment(substituteFor(teacher5, classA2, end, teacher3)),
                ApiCode.SUBSTITUTE_ALREADY_COVERED);
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

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    @DisplayName("Tổ trưởng chỉ có quyền ở nhóm tuổi mình trong đúng năm được phân công (valid_to trống không tràn năm)")
    void teamLeaderScopeFollowsSchoolYear() {
        long closed = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        long planned = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2027-09-01', '2028-05-31', 'PLANNED') RETURNING id");
        // Dữ liệu thật hay để trống valid_to: teacher2 tổ trưởng năm nay, teacher1 năm sau, teacher3 năm trước
        jdbc.update("INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from)"
                + " VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?, '2026-09-01')", teacher2, ageGroup4, year);
        jdbc.update("INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from)"
                + " VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?, '2027-09-01')", teacher1, ageGroup4, planned);
        jdbc.update("INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from,"
                + " valid_to) VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?, '2025-09-01', '2026-05-31')",
                teacher3, ageGroup4, closed);

        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher2, year, TODAY))
                .containsExactly(ageGroup4);
        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher2, null, TODAY))
                .as("null = năm đang hoạt động")
                .containsExactly(ageGroup4);
        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher2, planned, TODAY)).isEmpty();
        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher2, closed, TODAY)).isEmpty();
        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher1, year, TODAY)).isEmpty();
        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher1, planned, TODAY))
                .as("năm sắp diễn ra: tổ trưởng năm đó (giai đoạn chuẩn bị)")
                .containsExactly(ageGroup4);
        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher3, closed, TODAY))
                .as("năm đã kết thúc: tổ trưởng năm đó vẫn xem lại được")
                .containsExactly(ageGroup4);
        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher3, year, TODAY)).isEmpty();

        // Năm đang chạy: phân công bắt đầu sau hôm nay chưa có quyền
        jdbc.update("INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from)"
                + " VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?, '2026-11-15')", teacher3, ageGroup4, year);
        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher3, year, TODAY)).isEmpty();
        assertThat(staffAssignmentService.findScopedTeamLeaderAgeGroupIds(teacher3, year, LocalDate.of(2026, 11, 15)))
                .containsExactly(ageGroup4);

        assertThat(staffAssignmentService.findYearTeamLeaderIds(ageGroup4, year, TODAY)).containsExactly(teacher2);
        assertThat(staffAssignmentService.findYearTeamLeaderIds(ageGroup4, planned, LocalDate.of(2027, 10, 1)))
                .containsExactly(teacher1);
    }
}
