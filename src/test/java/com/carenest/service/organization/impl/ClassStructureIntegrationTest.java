package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.AgeGroupRequest;
import com.carenest.dto.organization.ClassRequest;
import com.carenest.dto.organization.ClassResponse;
import com.carenest.dto.organization.CopyClassesRequest;
import com.carenest.dto.organization.CopyClassesResponse;
import com.carenest.dto.organization.RoomRequest;
import com.carenest.dto.organization.RoomResponse;
import com.carenest.dto.organization.SchoolStructureResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassManagementService;
import com.carenest.service.organization.RoomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.RoomType;
import java.time.Clock;
import java.time.Instant;
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

/** Phòng, nhóm tuổi, lớp trên PostgreSQL thật (V12, V13). Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ClassStructureIntegrationTest {

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
    private RoomService roomService;

    @Autowired
    private AgeGroupService ageGroupService;

    @Autowired
    private ClassManagementService classService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vicePrincipal;
    private long campusA;
    private long campusB;
    private long year;
    private long ageGroup3;

    @BeforeEach
    void setUp() {
        long principalId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-p', 'principal.it', 'Principal IT') RETURNING id");
        long vpId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-vp', 'vp.it', 'VP IT') RETURNING id");
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
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        principal = new CurrentUser(principalId, "kc-p", "principal.it", "P", null, Set.of(CareNestRole.PRINCIPAL));
        vicePrincipal = new CurrentUser(vpId, "kc-vp", "vp.it", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));
    }

    @Test
    void ageGroupsSeededByV13() {
        List<AgeGroupDetailResponse> groups = ageGroupService.getAgeGroups();

        AgeGroupDetailResponse ag3 =
                groups.stream().filter(g -> g.id() == ageGroup3).findFirst().orElseThrow();
        assertThat(ag3.maxClassSize()).isEqualTo(25);
        assertThat(ag3.mealSessions()).containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        assertThat(ag3.mealsPerDay()).isEqualTo(2);
    }

    @Test
    void overlappingAgeRangeIsRejected() {
        AgeGroupRequest overlapping =
                new AgeGroupRequest("Nhóm mới", "Mới", 30, 40, List.of(MealSession.LUNCH), 20, null);

        expectError(() -> ageGroupService.createAgeGroup(principal, overlapping), ApiCode.CONFLICT);
    }

    @Test
    void createAgeGroupOutsideExistingRanges() {
        AgeGroupDetailResponse created = ageGroupService.createAgeGroup(
                principal,
                new AgeGroupRequest(
                        "Nhà trẻ nhỏ", "NT nhỏ", 12, 24, List.of(MealSession.LUNCH, MealSession.MORNING), 15, ""));

        assertThat(created.code()).startsWith("ag-");
        assertThat(created.mealSessions()).containsExactly(MealSession.MORNING, MealSession.LUNCH);
    }

    @Test
    void classSizeLimitedByAgeGroupAndRoom() {
        RoomResponse room = createRoom(campusA, "P.101", 20);

        expectError(
                () -> classService.createClass(principal, classRequest("Lớp Mầm 1", campusA, room.id(), 22)),
                ApiCode.BAD_REQUEST);
        ClassResponse created = classService.createClass(principal, classRequest("Lớp Mầm 1", campusA, room.id(), 20));
        assertThat(created.capacity()).isEqualTo(20);
        assertThat(created.missingTeachers()).isEqualTo(2);
    }

    @Test
    void roomUsedOnceForEachYearAndCampusMustMatch() {
        RoomResponse room = createRoom(campusA, "P.101", 25);
        classService.createClass(principal, classRequest("Lớp Mầm 1", campusA, room.id(), 25));

        expectError(
                () -> classService.createClass(principal, classRequest("Lớp Mầm 2", campusA, room.id(), 25)),
                ApiCode.CONFLICT);
        expectError(
                () -> classService.createClass(principal, classRequest("Lớp Mầm 3", campusB, room.id(), 25)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void roomCapacityCannotDropBelowClassAndRoomInUseCannotBeDeactivated() {
        RoomResponse room = createRoom(campusA, "P.101", 25);
        classService.createClass(principal, classRequest("Lớp Mầm 1", campusA, room.id(), 25));

        expectError(
                () -> roomService.updateRoom(
                        principal,
                        room.id(),
                        new RoomRequest(campusA, "P.101", "Phòng P.101", RoomType.CLASS, 20, null, null)),
                ApiCode.CONFLICT);
        expectError(() -> roomService.changeRoomStatus(principal, room.id(), RoomStatus.INACTIVE), ApiCode.CONFLICT);
        expectError(() -> roomService.deleteRoom(principal, room.id()), ApiCode.CONFLICT);
    }

    @Test
    void unusedRoomIsHardDeleted() {
        RoomResponse room = createRoom(campusA, "P.201", 25);

        roomService.deleteRoom(principal, room.id());

        Integer remaining = jdbc.queryForObject("SELECT count(*) FROM rooms WHERE id = ?", Integer.class, room.id());
        assertThat(remaining).isZero();
    }

    @Test
    void duplicatedRoomCodeInCampusIsConflictButAllowedInOtherCampus() {
        createRoom(campusA, "P.101", 25);

        expectError(() -> createRoom(campusA, "p.101", 25), ApiCode.CONFLICT);
        assertThat(createRoom(campusB, "P.101", 25).campusId()).isEqualTo(campusB);
    }

    @Test
    void classWithChildrenKeepsAgeGroupAndCannotBeDeleted() {
        RoomResponse room = createRoom(campusA, "P.101", 25);
        ClassResponse created = classService.createClass(principal, classRequest("Lớp Mầm 1", campusA, room.id(), 25));
        long child = insertId(
                "INSERT INTO children (full_name, date_of_birth) VALUES ('Trẻ IT', '2022-01-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                child,
                created.id());
        long ageGroup4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);

        expectError(
                () -> classService.updateClass(
                        principal,
                        created.id(),
                        new ClassRequest("Lớp Mầm 1", year, campusA, ageGroup4, room.id(), 25)),
                ApiCode.CONFLICT);
        expectError(() -> classService.deleteClass(principal, created.id()), ApiCode.CONFLICT);
    }

    @Test
    void structureForVicePrincipalOnlyShowsOwnCampus() {
        RoomResponse roomA = createRoom(campusA, "P.101", 25);
        RoomResponse roomB = createRoom(campusB, "P.101", 25);
        classService.createClass(principal, classRequest("Lớp A", campusA, roomA.id(), 25));
        classService.createClass(principal, classRequest("Lớp B", campusB, roomB.id(), 25));

        SchoolStructureResponse structure = classService.getSchoolStructure(vicePrincipal, year, null);

        assertThat(structure.classes()).extracting(ClassResponse::name).containsExactly("Lớp A");
        assertThat(structure.groupStats().get(ageGroup3).classCount()).isEqualTo(1);
    }

    @Test
    void copyClassesSkipsExistingNamesAndNeverCopiesChildren() {
        RoomResponse room = createRoom(campusA, "P.101", 25);
        classService.createClass(principal, classRequest("Lớp A", campusA, room.id(), 25));
        classService.createClass(
                principal,
                classRequest("Lớp B", campusA, createRoom(campusA, "P.102", 25).id(), 25));
        long nextYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2027-2028', '2027-09-05', '2028-05-31') RETURNING id");
        jdbc.update(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, capacity) VALUES (?, ?, 'Lớp B', ?, 20)",
                campusA,
                nextYear,
                ageGroup3);

        CopyClassesResponse result = classService.copyClasses(principal, nextYear, new CopyClassesRequest(year, null));

        assertThat(result.copied()).containsExactly("Lớp A");
        assertThat(result.skipped())
                .extracting(CopyClassesResponse.Skipped::name)
                .containsExactly("Lớp B");
        Long copiedRoom = jdbc.queryForObject(
                "SELECT room_id FROM classrooms WHERE school_year_id = ? AND name = 'Lớp A'", Long.class, nextYear);
        assertThat(copiedRoom).isEqualTo(room.id());
    }

    @Test
    void vicePrincipalCannotWrite() {
        expectError(() -> createRoomAs(vicePrincipal, campusA, "P.301", 25), ApiCode.FORBIDDEN);
    }

    private RoomResponse createRoom(long campusId, String code, int capacity) {
        return createRoomAs(principal, campusId, code, capacity);
    }

    private RoomResponse createRoomAs(CurrentUser user, long campusId, String code, int capacity) {
        return roomService.createRoom(
                user, new RoomRequest(campusId, code, "Phòng " + code, RoomType.CLASS, capacity, null, null));
    }

    private ClassRequest classRequest(String name, long campusId, long roomId, int capacity) {
        return new ClassRequest(name, year, campusId, ageGroup3, roomId, capacity);
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
