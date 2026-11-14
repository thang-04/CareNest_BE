package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.child.AutoPlaceRequest;
import com.carenest.dto.child.AutoPlaceResponse;
import com.carenest.dto.child.PlacementBoardResponse;
import com.carenest.dto.child.SavePlacementsRequest;
import com.carenest.dto.child.YearReadinessResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.YearTransitionService;
import com.carenest.service.organization.RoomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PlacementAction;
import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.SchoolYearStatus;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Chuyển năm học trên PostgreSQL thật (V19): xếp lớp, readiness, kích hoạt chuyển trẻ. Hôm nay = 30/08/2027. */
@SpringBootTest
@Transactional
@RecordApplicationEvents
@Testcontainers(disabledWithoutDocker = true)
class YearTransitionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2027-08-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private YearTransitionService service;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private SchoolYearService schoolYearService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private RoomService roomService;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private long oldYear;
    private long newYear;
    private long oldClass;
    private long newClass4;
    private long newClass3;
    private long child1;
    private long child2;

    @BeforeEach
    void setUp() {
        long principalId = user("principal");
        long vpId = user("vp");
        long teacher1 = user("teacher1");
        long teacher2 = user("teacher2");
        long campus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Campus A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long ag4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        oldYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        newYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2027-2028', '2027-09-05', '2028-05-31') RETURNING id");
        long room1 = room(campus, "P.1");
        long room2 = room(campus, "P.2");
        long room3 = room(campus, "P.3");
        oldClass = classroom(campus, oldYear, "Lớp cũ", ag3, room1, 25);
        newClass4 = classroom(campus, newYear, "Lớp nhỡ", ag4, room2, 25);
        newClass3 = classroom(campus, newYear, "Lớp bé", ag3, room3, 1);
        child1 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Trẻ 1', '2023-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        child2 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Trẻ 2', '2023-02-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        for (long child : List.of(child1, child2)) {
            jdbc.update(
                    "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                    child,
                    oldClass);
        }
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        // PHT theo năm học: năm 2026-2027 (hết hạn 31/05/2027) và năm 2027-2028 từ ngày mở chuẩn bị (hôm nay
        // 30/08/2027)
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from, valid_to) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01', NULL), (?, 'VICE_PRINCIPAL', ?, '2027-08-05', '2028-05-31')",
                vpId,
                campus,
                vpId,
                campus);
        for (long classId : List.of(newClass4, newClass3)) {
            teacher(teacher1, classId, true);
            teacher(teacher2, classId, false);
        }
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2027-09-05')",
                newYear);
        principal =
                new CurrentUser(principalId, "kc-principal", "principal", "P", null, Set.of(CareNestRole.PRINCIPAL));
    }

    @Test
    void boardSuggestsNextAgeGroupClass() {
        PlacementBoardResponse board = service.getPlacements(principal, newYear);

        assertThat(board.editable()).isTrue();
        PlacementBoardResponse.SourceClass source = board.classes().getFirst();
        assertThat(source.suggestedTargetId()).isEqualTo(newClass4);
        assertThat(source.children())
                .extracting(PlacementBoardResponse.ChildPlacement::fullName)
                .containsExactly("Trẻ 1", "Trẻ 2");
    }

    @Test
    void readinessBlocksUntilEveryChildIsPlaced() {
        YearReadinessResponse before = service.getReadiness(principal, newYear);
        assertThat(before.canActivate()).isFalse();
        assertThat(before.blockers()).anyMatch(b -> b.startsWith("Xếp lớp trẻ năm mới"));

        AutoPlaceResponse auto = service.autoPlace(principal, newYear, new AutoPlaceRequest(null, null));
        assertThat(auto.promoted()).isEqualTo(2);

        YearReadinessResponse after = service.getReadiness(principal, newYear);
        assertThat(after.canActivate()).isTrue();
        assertThat(after.impact().move()).isEqualTo(2);

        // CFG-YR-05: chưa tới ngày mở chuẩn bị (01/09, hôm nay 30/08) ⇒ chưa kích hoạt dù đủ điều kiện khác
        jdbc.update("UPDATE school_years SET prep_date = '2027-09-01' WHERE id = ?", newYear);
        entityManager.clear();
        YearReadinessResponse beforePrep = service.getReadiness(principal, newYear);
        assertThat(beforePrep.canActivate()).isFalse();
        assertThat(beforePrep.openFrom()).isEqualTo(LocalDate.of(2027, 9, 1));
        assertThat(beforePrep.blockers()).anyMatch(b -> b.startsWith("Chưa tới ngày mở chuẩn bị"));
        assertThatThrownBy(() -> service.activateSchoolYear(principal, newYear, "2027-2028"))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.ACTIVATION_BEFORE_PREP);
    }

    @Test
    void teacherStepWarnsForShortClassAndBlocksWithoutHomeroomOrTeacher() {
        // Lớp bé còn 1 giáo viên, chưa có chủ nhiệm ⇒ chặn
        jdbc.update("DELETE FROM staff_assignments WHERE classroom_id = ? AND is_homeroom", newClass3);
        YearReadinessResponse noHomeroom = service.getReadiness(principal, newYear);
        assertThat(teacherStep(noHomeroom).ok()).isFalse();
        assertThat(teacherStep(noHomeroom).detail()).isEqualTo("1 lớp chưa có chủ nhiệm");
        assertThat(noHomeroom.blockers()).anyMatch(b -> b.startsWith("Giáo viên lớp"));

        // Người còn lại làm chủ nhiệm ⇒ chỉ cảnh báo thiếu 1 GV
        jdbc.update("UPDATE staff_assignments SET is_homeroom = TRUE WHERE classroom_id = ?", newClass3);
        // Bỏ entity đã nạp trong transaction test để đọc lại giá trị vừa sửa bằng SQL
        entityManager.clear();
        YearReadinessResponse.Step warn = teacherStep(service.getReadiness(principal, newYear));
        assertThat(warn.ok()).isTrue();
        assertThat(warn.warning()).isTrue();
        assertThat(warn.detail()).isEqualTo("Tất cả lớp có GV và chủ nhiệm · 1 lớp thiếu 1 GV");

        // Lớp không còn giáo viên ⇒ chặn
        jdbc.update("DELETE FROM staff_assignments WHERE classroom_id = ?", newClass3);
        entityManager.clear();
        YearReadinessResponse.Step blocked = teacherStep(service.getReadiness(principal, newYear));
        assertThat(blocked.ok()).isFalse();
        assertThat(blocked.detail()).isEqualTo("1 lớp chưa có giáo viên");
    }

    private static YearReadinessResponse.Step teacherStep(YearReadinessResponse readiness) {
        return readiness.steps().stream()
                .filter(s -> s.key().equals("teachers"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("CFG-YR-05: GV năm đang chuẩn bị thấy lớp mình và trẻ đã xếp, chỉ trong giai đoạn chuẩn bị")
    void teacherSeesPrepClassesWithPlacedChildren() {
        service.savePlacements(
                principal,
                newYear,
                new SavePlacementsRequest(List.of(
                        new SavePlacementsRequest.Item(child1, PlacementAction.PROMOTE, newClass4),
                        new SavePlacementsRequest.Item(child2, PlacementAction.LEAVE, null))));
        long teacherId = jdbc.queryForObject("SELECT id FROM user_accounts WHERE username = 'teacher1'", Long.class);
        CurrentUser teacher =
                new CurrentUser(teacherId, "kc-teacher1", "teacher1", "T", null, Set.of(CareNestRole.TEACHER));

        // Hôm nay 30/08/2027 ≥ ngày mở chuẩn bị mặc định 05/08/2027; phân công năm mới tính từ ngày chuẩn bị
        // (CFG-YR-05)
        jdbc.update("UPDATE staff_assignments SET valid_from = '2027-08-05' WHERE user_id = ?", teacherId);
        var prep = service.getPrepClasses(teacher, newYear);
        assertThat(prep.prepDate()).isEqualTo(LocalDate.of(2027, 8, 5));
        assertThat(prep.classes()).extracting(c -> c.id()).containsExactlyInAnyOrder(newClass4, newClass3);
        assertThat(prep.classes())
                .filteredOn(c -> c.id().equals(newClass4))
                .singleElement()
                .satisfies(
                        c -> assertThat(c.children()).extracting(ch -> ch.id()).containsExactly(child1));

        CurrentUser stranger =
                new CurrentUser(user("stranger"), "kc-stranger", "stranger", "S", null, Set.of(CareNestRole.TEACHER));
        // GV không có phân công năm mới không thấy năm đó
        assertThatThrownBy(() -> service.getPrepClasses(stranger, newYear))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.SCHOOL_YEAR_NOT_FOUND);

        // Tổ trưởng nhóm tuổi 4 năm mới (không dạy lớp nào): thấy mọi lớp nhóm tuổi đó, chỉ xem
        long leaderId = user("leader");
        long ag4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from, valid_to)"
                        + " VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?, '2027-08-05', '2028-05-31')",
                leaderId,
                ag4,
                newYear);
        CurrentUser leader = new CurrentUser(leaderId, "kc-leader", "leader", "L", null, Set.of(CareNestRole.TEACHER));
        assertThat(service.getPrepClasses(leader, newYear).classes())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.id()).isEqualTo(newClass4);
                    assertThat(c.teaches()).isFalse();
                    assertThat(c.children()).extracting(ch -> ch.id()).containsExactly(child1);
                });
        assertThat(service.getPrepClasses(teacher, newYear).classes()).allMatch(c -> c.teaches());

        // AUTH-20: PHT còn phân công xem mọi lớp; PHT không còn phân công không thấy lớp nào
        long vpId = jdbc.queryForObject("SELECT id FROM user_accounts WHERE username = 'vp'", Long.class);
        CurrentUser vp = new CurrentUser(vpId, "kc-vp", "vp", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));
        assertThat(service.getPrepClasses(vp, newYear).classes())
                .extracting(c -> c.id())
                .contains(newClass4, newClass3);
        CurrentUser vpWithoutAssignment =
                new CurrentUser(user("vp-old"), "kc-vp-old", "vp-old", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));
        assertThat(service.getPrepClasses(vpWithoutAssignment, newYear).classes())
                .isEmpty();

        // Chưa tới ngày mở chuẩn bị: GV không thấy năm
        jdbc.update("UPDATE school_years SET prep_date = '2027-09-01' WHERE id = ?", newYear);
        entityManager.clear();
        assertThatThrownBy(() -> service.getPrepClasses(teacher, newYear))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.SCHOOL_YEAR_NOT_FOUND);
    }

    @Test
    void activationMovesChildrenAndClosesOldYear() {
        service.savePlacements(
                principal,
                newYear,
                new SavePlacementsRequest(List.of(
                        new SavePlacementsRequest.Item(child1, PlacementAction.PROMOTE, newClass4),
                        new SavePlacementsRequest.Item(child2, PlacementAction.LEAVE, null))));

        SchoolYearDetailResponse activated = service.activateSchoolYear(principal, newYear, "2027-2028");

        assertThat(activated.status()).isEqualTo(SchoolYearStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT status FROM school_years WHERE id = ?", String.class, oldYear))
                .isEqualTo("CLOSED");
        LocalDate firstDay = LocalDate.of(2027, 9, 5);
        assertThat(enrollmentService.findActiveClassroomIds(child1, firstDay)).containsExactly(newClass4);
        assertThat(enrollmentService.findActiveClassroomIds(child1, firstDay.minusDays(1)))
                .containsExactly(oldClass);
        assertThat(enrollmentService.findActiveClassroomIds(child2, firstDay)).isEmpty();
        // CHD-04: trẻ nghỉ học là "đã rời trường" từ ngày bắt đầu năm mới; module tài khoản nhận event để khóa phụ
        // huynh
        assertThat(enrollmentService.findChildIdsLeftSchool(List.of(child1, child2), firstDay))
                .containsExactly(child2);
        assertThat(enrollmentService.findChildIdsLeftSchool(List.of(child2), firstDay.minusDays(1)))
                .isEmpty();
        assertThat(events.stream(YearTransitionService.ChildrenLeftSchoolEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.childIds()).containsExactly(child2);
                    assertThat(event.effectiveDate()).isEqualTo(firstDay);
                });

        // CFG-YR-02: năm cũ giữ như lúc kết thúc dù năm mới đổi tên / xóa phòng
        jdbc.update("UPDATE rooms SET name = 'Đổi tên', status = 'DELETED', deleted_at = now() WHERE code = 'P.1'");
        entityManager.clear();
        assertThat(roomService.getRooms(principal, null, oldYear))
                .filteredOn(r -> r.room().equals("P.1"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.name()).isEqualTo("Phòng P.1");
                    assertThat(r.status()).isEqualTo(RoomStatus.ACTIVE);
                });
        assertThat(roomService.getRooms(principal, null, newYear))
                .noneMatch(r -> r.room().equals("P.1"));
    }

    @Test
    void activationRefusedWhenNotReady() {
        expectError(() -> service.activateSchoolYear(principal, newYear, "2027-2028"), ApiCode.CONFLICT);
    }

    @Test
    void wrongAgeGroupIsRejected() {
        expectError(
                () -> service.savePlacements(
                        principal,
                        newYear,
                        new SavePlacementsRequest(
                                List.of(new SavePlacementsRequest.Item(child1, PlacementAction.PROMOTE, newClass3)))),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void classCapacityIsEnforced() {
        expectError(
                () -> service.savePlacements(
                        principal,
                        newYear,
                        new SavePlacementsRequest(List.of(
                                new SavePlacementsRequest.Item(child1, PlacementAction.STAY, newClass3),
                                new SavePlacementsRequest.Item(child2, PlacementAction.STAY, newClass3)))),
                ApiCode.CONFLICT);
    }

    @Test
    void deletingPlannedYearRemovesItsPlacements() {
        jdbc.update("DELETE FROM attendance_cutoffs WHERE school_year_id = ?", newYear);
        jdbc.update("DELETE FROM staff_assignments WHERE classroom_id IN (?, ?)", newClass4, newClass3);
        jdbc.update("DELETE FROM classrooms WHERE id IN (?, ?)", newClass4, newClass3);
        service.savePlacements(
                principal,
                newYear,
                new SavePlacementsRequest(
                        List.of(new SavePlacementsRequest.Item(child1, PlacementAction.LEAVE, null))));

        schoolYearService.deleteSchoolYear(principal, newYear);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM class_placements", Integer.class))
                .isZero();
    }

    @Test
    void autoPlaceGraduatesOnlyChildrenWhoCompletedTheProgram() {
        // Lớp cũ thuộc nhóm tuổi lớn nhất: trẻ 1 hoàn thành chương trình, trẻ 2 chưa có đánh giá cuối năm
        long ag5 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-5'", Long.class);
        jdbc.update("UPDATE classrooms SET age_group_id = ? WHERE id = ?", ag5, oldClass);
        long campus = jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, oldClass);
        jdbc.update(
                "INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id, period_type, "
                        + "period_start, period_end, status, final_content, program_completed, confirmed_by, "
                        + "confirmed_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'YEAR', '2026-09-01', "
                        + "'2027-05-31', 'CONFIRMED', 'Nhận xét cuối năm', TRUE, ?, now(), now(), now())",
                child1,
                oldClass,
                campus,
                oldYear,
                principal.userId());

        PlacementBoardResponse board = service.getPlacements(principal, newYear);
        assertThat(board.classes().getFirst().children())
                .extracting(PlacementBoardResponse.ChildPlacement::programCompleted)
                .containsExactly(true, null);

        AutoPlaceResponse auto = service.autoPlace(principal, newYear, new AutoPlaceRequest(null, null));
        assertThat(auto.graduated()).isEqualTo(1);
        assertThat(auto.undecided()).isEqualTo(1);
    }

    @Test
    void childOfClassWithoutAgeGroupCanOnlyLeave() {
        jdbc.update("UPDATE classrooms SET age_group_id = NULL WHERE id = ?", oldClass);

        expectError(
                () -> service.savePlacements(
                        principal,
                        newYear,
                        new SavePlacementsRequest(
                                List.of(new SavePlacementsRequest.Item(child1, PlacementAction.GRADUATE, null)))),
                ApiCode.BAD_REQUEST);
        AutoPlaceResponse auto = service.autoPlace(principal, newYear, new AutoPlaceRequest(null, null));
        assertThat(auto.graduated()).isZero();
        assertThat(auto.unplaced())
                .extracting(AutoPlaceResponse.Unplaced::count)
                .containsExactly(2);
    }

    @Test
    void vicePrincipalCannotSave() {
        CurrentUser vicePrincipal = new CurrentUser(999L, "kc", "vp", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));

        expectError(
                () -> service.savePlacements(
                        vicePrincipal,
                        newYear,
                        new SavePlacementsRequest(
                                List.of(new SavePlacementsRequest.Item(child1, PlacementAction.LEAVE, null)))),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("Phân quyền động: PHT xếp lớp năm mới khi HT cấp YEAR_PLACEMENT_MANAGE, đúng phạm vi điểm trường")
    void vicePrincipalPlacesWithinGrantedScope() {
        long vpId = jdbc.queryForObject("SELECT id FROM user_accounts WHERE username = 'vp'", Long.class);
        CurrentUser noGrant = new CurrentUser(vpId, "kc-vp", "vp", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));
        expectError(() -> service.getPlacements(noGrant, newYear), ApiCode.FORBIDDEN);

        CurrentUser granted = new CurrentUser(
                vpId,
                "kc-vp",
                "vp",
                "VP",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.YEAR_PLACEMENT_MANAGE));
        PlacementBoardResponse board = service.getPlacements(granted, newYear);
        assertThat(board.editable()).isTrue();
        assertThat(board.classes())
                .extracting(PlacementBoardResponse.SourceClass::id)
                .containsExactly(oldClass);
        service.savePlacements(
                granted,
                newYear,
                new SavePlacementsRequest(
                        List.of(new SavePlacementsRequest.Item(child1, PlacementAction.PROMOTE, newClass4))));

        // PHT có mã nhưng không phụ trách điểm trường của lớp ⇒ không thấy, không xếp được
        long otherVp = user("vp-other");
        CurrentUser outside = new CurrentUser(
                otherVp,
                "kc-vp-other",
                "vp-other",
                "VP",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.YEAR_PLACEMENT_MANAGE));
        assertThat(service.getPlacements(outside, newYear).classes()).isEmpty();
        expectError(
                () -> service.savePlacements(
                        outside,
                        newYear,
                        new SavePlacementsRequest(
                                List.of(new SavePlacementsRequest.Item(child2, PlacementAction.LEAVE, null)))),
                ApiCode.FORBIDDEN);
    }

    @Test
    void onlyTheNextPlannedYearTakesPlacements() {
        long laterYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2028-2029', '2028-09-05', '2029-05-31') RETURNING id");

        // CFG-YR-03: năm sau năm kế tiếp chỉ xem, không lưu / tự xếp được
        assertThat(service.getPlacements(principal, laterYear).editable()).isFalse();
        expectError(
                () -> service.savePlacements(
                        principal,
                        laterYear,
                        new SavePlacementsRequest(
                                List.of(new SavePlacementsRequest.Item(child1, PlacementAction.LEAVE, null)))),
                ApiCode.CONFLICT);
        expectError(() -> service.autoPlace(principal, laterYear, new AutoPlaceRequest(null, null)), ApiCode.CONFLICT);
        assertThat(service.getPlacements(principal, newYear).editable()).isTrue();
    }

    @Test
    void stalePlacementFromAnotherSourceClassIsIgnoredThenReplaced() {
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long campus = jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, oldClass);
        long otherClass = classroom(campus, oldYear, "Lớp khác", ag3, room(campus, "P.9"), 25);
        // Bản xếp lưu lúc trẻ còn ở lớp khác (lớp nguồn cũ): không tính đã xếp, không áp khi kích hoạt
        jdbc.update(
                "INSERT INTO class_placements (school_year_id, child_id, from_classroom_id, action, to_classroom_id) "
                        + "VALUES (?, ?, ?, 'PROMOTE', ?)",
                newYear,
                child1,
                otherClass,
                newClass4);

        assertThat(service.getPlacements(principal, newYear)
                        .classes()
                        .getFirst()
                        .children())
                .allMatch(c -> c.placement() == null);
        YearReadinessResponse before = service.getReadiness(principal, newYear);
        assertThat(before.steps())
                .filteredOn(st -> st.key().equals("placement"))
                .singleElement()
                .satisfies(st -> assertThat(st.detail()).startsWith("0/2"));

        AutoPlaceResponse auto = service.autoPlace(principal, newYear, new AutoPlaceRequest(null, null));
        assertThat(auto.promoted()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM class_placements WHERE child_id = ? AND from_classroom_id = ?",
                        Integer.class,
                        child1,
                        oldClass))
                .isEqualTo(1);
    }

    @Test
    void readinessRechecksCapacityAfterPlacement() {
        service.autoPlace(principal, newYear, new AutoPlaceRequest(null, null));
        assertThat(service.getReadiness(principal, newYear).canActivate()).isTrue();

        // Lớp bị hạ sĩ số sau khi xếp (dữ liệu cũ): chặn kích hoạt
        jdbc.update("UPDATE classrooms SET capacity = 1 WHERE id = ?", newClass4);
        entityManager.clear();
        YearReadinessResponse after = service.getReadiness(principal, newYear);
        assertThat(after.canActivate()).isFalse();
        assertThat(after.blockers()).anyMatch(b -> b.contains("Vượt sĩ số"));
    }

    private long user(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
    }

    private long room(long campusId, String code) {
        return insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campusId + ", '"
                + code + "', 'Phòng " + code + "', 'CLASS', 30) RETURNING id");
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId, long roomId, int capacity) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity) "
                + "VALUES (" + campusId + ", " + yearId + ", '" + name + "', " + ageGroupId + ", " + roomId + ", "
                + capacity + ") RETURNING id");
    }

    private void teacher(long userId, long classroomId, boolean homeroom) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2027-09-05', '2028-05-31', ?)",
                userId,
                classroomId,
                homeroom);
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
