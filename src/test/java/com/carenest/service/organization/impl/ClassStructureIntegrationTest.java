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
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.RoomType;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
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

/** Phòng, nhóm tuổi, lớp trên PostgreSQL thật (V15, V16). Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ClassStructureIntegrationTest {

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
    private RoomService roomService;

    @Autowired
    private AgeGroupService ageGroupService;

    @Autowired
    private ClassManagementService classService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

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
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address, phone, status) "
                        + "SELECT ?, id, code, name, short_name, address, phone, status FROM campuses",
                year);
        jdbc.update(
                "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note) "
                        + "SELECT ?, id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note FROM age_groups",
                year);
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        principal = new CurrentUser(
                principalId,
                "kc-p",
                "principal.it",
                "P",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                Set.of(Permission.ROOM_MANAGE));
        vicePrincipal = new CurrentUser(
                vpId,
                "kc-vp",
                "vp.it",
                "VP",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.ROOM_MANAGE));
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
    @DisplayName("Lỗi xóa nhóm tuổi nêu năm học và tổ trưởng đang giữ nhóm (nhóm tuổi dùng chung mọi năm)")
    void deleteAgeGroupNamesYearAndLeader() {
        AgeGroupDetailResponse created = ageGroupService.createAgeGroup(
                principal, new AgeGroupRequest("Nhà trẻ nhỏ", "NT nhỏ", 12, 24, List.of(MealSession.LUNCH), 15, ""));
        long leaderId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-leader', 'leader.it', 'Cô Tổ Trưởng') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from) "
                        + "VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?, '2026-09-01')",
                leaderId,
                created.id(),
                year);

        assertThatThrownBy(() -> ageGroupService.deleteAgeGroup(principal, created.id()))
                .isInstanceOf(GlobalException.class)
                .hasMessage("Nhóm tuổi đã có tổ trưởng Cô Tổ Trưởng ở năm học 2026-2027, không xóa được");
    }

    @Test
    @DisplayName("Cần chốt 22/10/2026: nhóm tuổi theo từng năm học, thêm / xóa ở năm nào chỉ năm đó đổi")
    void ageGroupsArePerSchoolYear() {
        jdbc.update("UPDATE school_years SET status = 'PLANNED' WHERE id = ?", year);
        long nextYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2027-09-01', '2028-05-31', 'PLANNED') RETURNING id");
        ageGroupService.copyYearAgeGroups(year, nextYear);
        long ag0 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-0'", Long.class);
        jdbc.update(
                "DELETE FROM school_year_age_group_members WHERE school_year_id IN (?, ?) AND age_group_id = ?",
                year,
                nextYear,
                ag0);
        long ag5 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-5'", Long.class);
        long leaderId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-leader5', 'leader5.it', 'Cô Lớp Lớn') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from) "
                        + "VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?, '2026-09-01')",
                leaderId,
                ag5,
                year);

        // Lỗi 2: thêm ở 2027-2028 không hiện ở 2026-2027
        AgeGroupDetailResponse added = ageGroupService.createAgeGroup(
                principal,
                new AgeGroupRequest("Tên client", "Tên tắt client", 0, 1, List.of(MealSession.LUNCH), 15, "", ag0),
                nextYear);
        assertThat(ids(ageGroupService.getYearAgeGroups(nextYear))).contains(added.id());
        assertThat(ids(ageGroupService.getYearAgeGroups(year))).doesNotContain(added.id());

        // Lỗi 1: 2027-2028 chưa có tổ trưởng, lớp ⇒ xóa được; 2026-2027 vẫn giữ nhóm và tổ trưởng
        ageGroupService.deleteAgeGroup(principal, ag5, nextYear);
        assertThat(ids(ageGroupService.getYearAgeGroups(nextYear))).doesNotContain(ag5);
        assertThat(ids(ageGroupService.getYearAgeGroups(year))).contains(ag5);
        assertThatThrownBy(() -> ageGroupService.deleteAgeGroup(principal, ag5, year))
                .isInstanceOf(GlobalException.class)
                .hasMessage("Nhóm tuổi đã có tổ trưởng Cô Lớp Lớn ở năm học 2026-2027, không xóa được");

        // Thêm lại đúng tên nhóm đã có ở năm khác: dùng lại nhóm đó (giữ mã cho kế hoạch, thực đơn)
        String ag5Name = jdbc.queryForObject("SELECT name FROM age_groups WHERE id = ?", String.class, ag5);
        AgeGroupDetailResponse reused = ageGroupService.createAgeGroup(
                principal,
                new AgeGroupRequest(ag5Name, "x", 60, 72, List.of(MealSession.LUNCH), 30, "", ag5),
                nextYear);
        assertThat(reused.id()).isEqualTo(ag5);
        expectError(
                () -> ageGroupService.createAgeGroup(
                        principal,
                        new AgeGroupRequest(ag5Name, "x", 60, 72, List.of(MealSession.LUNCH), 30, "", ag5),
                        nextYear),
                ApiCode.CONFLICT);
    }

    private static List<Long> ids(List<AgeGroupDetailResponse> groups) {
        return groups.stream().map(AgeGroupDetailResponse::id).toList();
    }

    @Test
    @DisplayName("Cần chốt 18/11/2026: phụ trách bếp chỉ là nhân viên bếp, PHT của điểm trường hoặc Hiệu trưởng")
    void kitchenManagerMustBeKitchenStaffViceOrPrincipal() {
        long teacherId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-t-k', 'teacher.k', 'GV Bếp') RETURNING id");
        long kitchenClass = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campusA + ", " + year + ", 'Lớp bếp', " + ageGroup3 + ") RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from) "
                        + "VALUES (?, 'TEACHER', ?, '2026-01-01')",
                teacherId,
                kitchenClass);
        long cookId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-cook', 'cook.k', 'Cô Bếp') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'KITCHEN_STAFF', ?, '2026-01-01')",
                cookId,
                campusA);

        assertThatThrownBy(() -> roomService.createRoom(
                        principal, new RoomRequest(campusA, "B.1", "Bếp 1", RoomType.KITCHEN, null, teacherId, null)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.ROOM_KITCHEN_MANAGER_INVALID);
        assertThat(roomService
                        .createRoom(
                                principal,
                                new RoomRequest(campusA, "B.1", "Bếp 1", RoomType.KITCHEN, null, cookId, null))
                        .id())
                .isNotNull();
        // PHT của điểm trường và Hiệu trưởng cũng được; phòng loại khác vẫn nhận GV của điểm trường
        assertThat(roomService
                        .createRoom(
                                principal,
                                new RoomRequest(
                                        campusA, "B.2", "Bếp 2", RoomType.KITCHEN, null, vicePrincipal.userId(), null))
                        .id())
                .isNotNull();
        assertThat(roomService
                        .createRoom(
                                principal,
                                new RoomRequest(
                                        campusA, "B.3", "Bếp 3", RoomType.KITCHEN, null, principal.userId(), null))
                        .id())
                .isNotNull();
        assertThat(roomService
                        .createRoom(
                                principal,
                                new RoomRequest(campusA, "K.1", "Kho 1", RoomType.STORAGE, null, teacherId, null))
                        .id())
                .isNotNull();
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
    void classNameDuplicateIgnoresSpacesAccentFormAndCaseWithinYearAndCampus() {
        RoomResponse room = createRoom(campusA, "P.101", 25);
        ClassResponse created =
                classService.createClass(principal, classRequest("  Lớp   Mầm 1 ", campusA, room.id(), 25));
        assertThat(created.name()).isEqualTo("Lớp Mầm 1");
        String decomposed = java.text.Normalizer.normalize("LỚP MẦM 1", java.text.Normalizer.Form.NFD);
        for (String name : List.of("Lớp  Mầm 1", decomposed)) {
            assertThatThrownBy(() -> classService.createClass(principal, classRequest(name, campusA, room.id(), 25)))
                    .isInstanceOf(GlobalException.class)
                    .extracting(ex -> ((GlobalException) ex).getApiCode())
                    .isEqualTo(ApiCode.CLASS_NAME_EXISTS);
        }
        // Điểm trường khác cùng năm vẫn được dùng tên này
        RoomResponse roomB = createRoom(campusB, "P.201", 25);
        assertThat(classService
                        .createClass(principal, classRequest("Lớp Mầm 1", campusB, roomB.id(), 25))
                        .name())
                .isEqualTo("Lớp Mầm 1");
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
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Trẻ IT', '2022-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
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
    void classWithPlacedChildrenKeepsAgeGroupSizeAndCannotBeDeleted() {
        RoomResponse room = createRoom(campusA, "P.102", 25);
        ClassResponse created = classService.createClass(principal, classRequest("Lớp Mầm 2", campusA, room.id(), 25));
        ClassResponse source = classService.createClass(
                principal,
                classRequest(
                        "Lớp nguồn 2", campusA, createRoom(campusA, "P.202", 25).id(), 25));
        for (String name : List.of("Trẻ xếp 1", "Trẻ xếp 2")) {
            long child = insertId(
                    "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), '"
                            + name + "', '2022-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
            jdbc.update(
                    "INSERT INTO class_placements (school_year_id, child_id, from_classroom_id, action, to_classroom_id) "
                            + "VALUES (?, ?, ?, 'STAY', ?)",
                    year,
                    child,
                    source.id(),
                    created.id());
            // Bản xếp chỉ đếm khi trẻ vẫn đang học lớp nguồn
            jdbc.update(
                    "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                    child,
                    source.id());
        }
        long ageGroup4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);

        expectError(
                () -> classService.updateClass(
                        principal,
                        created.id(),
                        new ClassRequest("Lớp Mầm 2", year, campusA, ageGroup4, room.id(), 25)),
                ApiCode.CONFLICT);
        expectError(
                () -> classService.updateClass(
                        principal, created.id(), new ClassRequest("Lớp Mầm 2", year, campusA, ageGroup3, room.id(), 1)),
                ApiCode.BAD_REQUEST);
        assertThatThrownBy(() -> classService.deleteClass(principal, created.id()))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.CLASS_HAS_PLACED_CHILDREN.getDefaultDesc());
    }

    @Test
    void placementOfChildWhoLeftSourceClassIsNotCounted() {
        RoomResponse room = createRoom(campusA, "P.103", 25);
        ClassResponse created = classService.createClass(principal, classRequest("Lớp Mầm 3", campusA, room.id(), 25));
        ClassResponse source = classService.createClass(
                principal,
                classRequest(
                        "Lớp nguồn 3", campusA, createRoom(campusA, "P.203", 25).id(), 25));
        long child = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Trẻ đã chuyển', '2022-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        jdbc.update(
                "INSERT INTO class_placements (school_year_id, child_id, from_classroom_id, action, to_classroom_id) "
                        + "VALUES (?, ?, ?, 'STAY', ?)",
                year,
                child,
                source.id(),
                created.id());
        // Trẻ đã rời lớp nguồn trước hôm nay (chuyển lớp / nghỉ): bản xếp cũ không giữ lớp năm sau
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2026-09-01',"
                        + " '2026-10-01')",
                child,
                source.id());

        assertThat(classService.getSchoolStructure(principal, year, null).classes())
                .filteredOn(c -> c.id().equals(created.id()))
                .singleElement()
                .satisfies(c -> assertThat(c.plannedCount()).isIn(null, 0L));
    }

    @Test
    @DisplayName("AUTH-20: PHT còn phân công xem cơ cấu lớp mọi điểm trường")
    void structureForVicePrincipalShowsEveryCampus() {
        RoomResponse roomA = createRoom(campusA, "P.101", 25);
        RoomResponse roomB = createRoom(campusB, "P.101", 25);
        classService.createClass(principal, classRequest("Lớp A", campusA, roomA.id(), 25));
        classService.createClass(principal, classRequest("Lớp B", campusB, roomB.id(), 25));

        SchoolStructureResponse structure = classService.getSchoolStructure(vicePrincipal, year, null);

        assertThat(structure.classes()).extracting(ClassResponse::name).containsExactly("Lớp A", "Lớp B");
        assertThat(structure.groupStats().get(ageGroup3).classCount()).isEqualTo(2);
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
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address, phone, status) "
                        + "SELECT ?, id, code, name, short_name, address, phone, status FROM campuses",
                nextYear);
        jdbc.update(
                "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note) "
                        + "SELECT ?, id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note FROM age_groups",
                nextYear);
        jdbc.update(
                "INSERT INTO school_year_room_members (school_year_id, room_id, campus_id, code, name, room_type, capacity, manager_user_id, status, note) "
                        + "SELECT ?, id, campus_id, code, name, room_type, capacity, manager_user_id, status, note FROM rooms "
                        + "WHERE deleted_at IS NULL AND status <> 'DELETED'",
                nextYear);
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
    void vicePrincipalWritesOnlyWithinGrantedCampusScope() {
        assertThat(createRoomAs(vicePrincipal, campusA, "P.301", 25).campusId()).isEqualTo(campusA);
        expectError(() -> createRoomAs(vicePrincipal, campusB, "P.302", 25), ApiCode.FORBIDDEN);

        CurrentUser withoutPermission = new CurrentUser(
                vicePrincipal.userId(),
                vicePrincipal.keycloakUserId(),
                vicePrincipal.username(),
                vicePrincipal.fullName(),
                vicePrincipal.email(),
                vicePrincipal.roles(),
                Set.of());
        expectError(() -> createRoomAs(withoutPermission, campusA, "P.303", 25), ApiCode.FORBIDDEN);

        // Phân quyền động (Cần chốt 10/11/2026): PHT chỉ xem phòng cơ sở mình; HT cấp ROOM_MANAGE toàn trường thì xem
        // cả cơ sở B
        createRoom(campusB, "P.401", 25);
        assertThat(roomService.getRooms(vicePrincipal, null, null))
                .extracting(RoomResponse::campusId)
                .containsOnly(campusA);
        CurrentUser schoolWide = new CurrentUser(
                vicePrincipal.userId(),
                vicePrincipal.keycloakUserId(),
                vicePrincipal.username(),
                vicePrincipal.fullName(),
                vicePrincipal.email(),
                vicePrincipal.roles(),
                Set.of(Permission.ROOM_MANAGE),
                Set.of(Permission.ROOM_MANAGE));
        assertThat(roomService.getRooms(schoolWide, null, null))
                .extracting(RoomResponse::campusId)
                .contains(campusA, campusB);
    }

    @Test
    @DisplayName("CFG-YR-05: GV chỉ thấy lớp năm sắp diễn ra từ ngày mở chuẩn bị, kể cả khi gọi kèm năm học")
    void teacherSeesUpcomingClassOnlyFromPreparationDate() {
        long teacherId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-gv', 'gv.it', 'GV IT') RETURNING id");
        CurrentUser teacher =
                new CurrentUser(teacherId, "kc-gv", "gv.it", "GV", null, Set.of(CareNestRole.TEACHER), Set.of());
        // Hôm nay 22/10/2026: năm sau mở chuẩn bị 01/12/2026 (chưa tới) rồi đổi thành 20/10/2026 (đã tới)
        long planned = insertId("INSERT INTO school_years (name, prep_date, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2026-12-01', '2027-09-06', '2028-05-31', 'PLANNED') RETURNING id");
        long cls = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, capacity) VALUES ("
                + campusA + ", " + planned + ", 'Lớp năm sau', " + ageGroup3 + ", 25) RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to)"
                        + " VALUES (?, 'TEACHER', ?, '2026-12-01', '2028-05-31')",
                teacherId,
                cls);

        // Trước ngày mở chuẩn bị: GV chưa có lớp nào được xem ⇒ bị từ chối, không lộ lớp năm sau
        assertThatThrownBy(() -> classService.getSchoolStructure(teacher, planned, null))
                .isInstanceOf(GlobalException.class);

        jdbc.update("UPDATE school_years SET prep_date = '2026-10-20' WHERE id = ?", planned);
        jdbc.update("UPDATE staff_assignments SET valid_from = '2026-10-20' WHERE user_id = ?", teacherId);
        assertThat(classService.getSchoolStructure(teacher, planned, null).classes())
                .extracting(ClassResponse::id)
                .containsExactly(cls);
    }

    @Test
    @DisplayName("CFG-YR-05: GV năm cũ vẫn xem lớp mình đã dạy tới khi HT đóng năm (Cần chốt 17/11/2026)")
    void oldYearTeacherSeesOwnClassUntilYearClosed() {
        long teacherId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-old', 'old.it', 'GV cũ') RETURNING id");
        CurrentUser teacher =
                new CurrentUser(teacherId, "kc-old", "old.it", "GV", null, Set.of(CareNestRole.TEACHER), Set.of());
        // Hôm nay 22/10/2026: năm 2025-2026 đã qua ngày kết thúc nhưng HT chưa đóng (năm 2026-2027 chưa kích hoạt)
        jdbc.update("UPDATE school_years SET status = 'PLANNED' WHERE id = ?", year);
        long oldYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'ACTIVE') RETURNING id");
        long cls = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, capacity) VALUES ("
                + campusA + ", " + oldYear + ", 'Lớp năm cũ', " + ageGroup3 + ", 25) RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to)"
                        + " VALUES (?, 'TEACHER', ?, '2025-09-01', '2026-05-31')",
                teacherId,
                cls);

        assertThat(classService.getSchoolStructure(teacher, oldYear, null).classes())
                .extracting(ClassResponse::id)
                .containsExactly(cls);

        jdbc.update("UPDATE school_years SET status = 'CLOSED' WHERE id = ?", oldYear);
        entityManager.clear();
        assertThatThrownBy(() -> classService.getSchoolStructure(teacher, oldYear, null))
                .isInstanceOf(GlobalException.class);
    }

    private RoomResponse createRoom(long campusId, String code, int capacity) {
        return createRoomAs(principal, campusId, code, capacity);
    }

    private RoomResponse createRoomAs(CurrentUser user, long campusId, String code, int capacity) {
        return roomService.createRoom(
                user, new RoomRequest(campusId, code, "Phòng " + code, RoomType.CLASS, capacity, null, null), year);
    }

    private ClassRequest classRequest(String name, long campusId, long roomId, int capacity) {
        return new ClassRequest(name, year, campusId, ageGroup3, roomId, capacity);
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
