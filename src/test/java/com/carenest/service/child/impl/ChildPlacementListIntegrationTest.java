package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.child.ChildClassResponse;
import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.ChildListItemResponse;
import com.carenest.dto.child.ChildPlacementResponse;
import com.carenest.dto.child.PlaceChildRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.ChildListService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * Xếp / chuyển lớp và danh sách trẻ trên PostgreSQL thật (V86): enrollment theo ngày, lịch sử xếp lớp, thông báo
 * {@code CHILD_PLACED}, chuyển điểm trường, quy tắc năm học của danh sách (CHD-09). Hôm nay = 10/11/2026.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ChildPlacementListIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final Set<ChildStatus> ALL = EnumSet.allOf(ChildStatus.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private ChildRecordService recordService;

    @Autowired
    private ChildListService listService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vicePrincipalA;
    private CurrentUser vicePrincipalB;
    private CurrentUser teacherA1;
    private long teacherA2Id;
    private long yearOld;
    private long yearActive;
    private long oldA;
    private long classA1;
    private long classA2;
    private long classB1;
    private long campusA;
    private long campusB;
    private long an;
    private long binh;
    private long cuong;
    private long dung;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        long principalId = staff("ht.pl" + n, "PRINCIPAL");
        long vpAId = staff("pht.a.pl" + n, "VICE_PRINCIPAL");
        long vpBId = staff("pht.b.pl" + n, "VICE_PRINCIPAL");
        long teacherA1Id = staff("gv.a1.pl" + n, "TEACHER");
        teacherA2Id = staff("gv.a2.pl" + n, "TEACHER");
        campusA = insertId(
                "INSERT INTO campuses (code, name, is_main) VALUES ('PLA" + n + "', 'Cơ sở A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name) VALUES ('PLB" + n + "', 'Cơ sở B') RETURNING id");
        yearOld = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        yearActive = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        oldA = classroom(campusA, yearOld, "Mầm A cũ");
        classA1 = classroom(campusA, yearActive, "Chồi A1");
        classA2 = classroom(campusA, yearActive, "Chồi A2");
        classB1 = classroom(campusB, yearActive, "Chồi B1");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2025-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2025-01-01'), (?, 'VICE_PRINCIPAL', ?, '2025-01-01')",
                vpAId,
                campusA,
                vpBId,
                campusB);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE), (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacherA1Id,
                classA1,
                teacherA2Id,
                classA2);

        an = child("Nguyễn An", "ACTIVE", "2025-09-01", null, null);
        enroll(an, oldA, "2025-09-01", "2026-05-31");
        enroll(an, classA1, "2026-09-01", null);
        binh = child("Trần Bình", "PENDING_PLACEMENT", "2026-11-01", campusA, null);
        cuong = child("Lê Cường", "LEFT", "2026-09-01", null, "2026-10-15");
        enroll(cuong, classA2, "2026-09-01", "2026-10-14");
        dung = child("Phạm Dung", "LEFT", "2025-11-01", campusA, "2025-12-01");
        for (long id : List.of(an, binh, cuong, dung)) {
            declare(id, principalId);
        }

        principal = user(principalId, CareNestRole.PRINCIPAL);
        vicePrincipalA = user(vpAId, CareNestRole.VICE_PRINCIPAL);
        vicePrincipalB = user(vpBId, CareNestRole.VICE_PRINCIPAL);
        teacherA1 = user(teacherA1Id, CareNestRole.TEACHER);
    }

    @Test
    @DisplayName("CHD-10: xếp trẻ chờ xếp lớp ⇒ Đang học từ hôm nay, bỏ điểm trường tiếp nhận, lịch sử, báo GV lớp")
    void placePendingChild() {
        recordService.place(vicePrincipalA, binh, new PlaceChildRequest(classA1, ""));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT status FROM children WHERE id = ?", String.class, binh))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT intake_campus_id FROM children WHERE id = ?", Long.class, binh))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT valid_from FROM enrollments WHERE child_id = ? AND valid_to IS NULL",
                        LocalDate.class,
                        binh))
                .isEqualTo(TODAY);
        assertThat(recordService.findPlacements(binh)).singleElement().satisfies(p -> {
            assertThat(p.fromClassId()).isNull();
            assertThat(p.toClassName()).isEqualTo("Chồi A1");
            assertThat(p.reason()).isNull();
        });
        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE type = 'CHILD_PLACED' AND child_id = ?",
                        String.class,
                        binh))
                .isEqualTo("Trần Bình đã được xếp vào Chồi A1.");
    }

    @Test
    @DisplayName(
            "CHD-10: chuyển lớp ⇒ lớp cũ hết hôm qua; chuyển lại trong ngày ⇒ bỏ lớp vừa vào; lịch sử mới nhất trước")
    void transferTwiceSameDay() {
        recordService.place(vicePrincipalA, an, new PlaceChildRequest(classA2, "Đổi lớp theo độ tuổi"));
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                        "SELECT valid_to FROM enrollments WHERE child_id = ? AND classroom_id = ?",
                        LocalDate.class,
                        an,
                        classA1))
                .isEqualTo(TODAY.minusDays(1));
        ChildClassResponse cls = recordService.findClass(an);
        assertThat(cls.id()).isEqualTo(classA2);
        assertThat(cls.teacherIds()).containsExactly(teacherA2Id);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE type = 'CHILD_PLACED' AND user_id = ?",
                        Long.class,
                        teacherA2Id))
                .isEqualTo(1);

        recordService.place(vicePrincipalA, an, new PlaceChildRequest(classA1, "Nhập nhầm lớp"));
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM enrollments WHERE child_id = ? AND classroom_id = ?",
                        Long.class,
                        an,
                        classA2))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT classroom_id FROM enrollments WHERE child_id = ? AND valid_to IS NULL", Long.class, an))
                .isEqualTo(classA1);
        assertThat(recordService.findPlacements(an))
                .extracting(ChildPlacementResponse::fromClassName, ChildPlacementResponse::toClassName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Chồi A2", "Chồi A1"),
                        org.assertj.core.groups.Tuple.tuple("Chồi A1", "Chồi A2"));
    }

    @Test
    @DisplayName(
            "G94-180/CHD-02: chuyển điểm trường cần quản lý cả hai điểm trường; sau chuyển PHT cũ hết thấy, PHT mới thấy")
    void crossCampusTransfer() {
        assertThatThrownBy(() -> recordService.place(vicePrincipalA, an, new PlaceChildRequest(classB1, "Chuyển nhà")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                                        "classId", "Chỉ xếp được vào lớp thuộc điểm trường bạn được giao")));

        recordService.place(principal, an, new PlaceChildRequest(classB1, "Chuyển nhà"));
        entityManager.flush();

        assertThat(ids(listService.searchChildren(vicePrincipalA, filter(null, null, null))))
                .doesNotContain(an);
        assertThat(listService
                        .searchChildren(vicePrincipalB, filter(null, null, null))
                        .items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(an);
                    assertThat(item.campusId()).isEqualTo(campusB);
                    assertThat(item.className()).isEqualTo("Chồi B1");
                });
        // Năm cũ theo lớp tại thời điểm đó (CMR-04): PHT điểm trường A vẫn thấy An ở lớp năm trước
        assertThat(ids(listService.searchChildren(vicePrincipalA, filter(yearOld, null, ALL))))
                .contains(an);
    }

    @Test
    @DisplayName(
            "D-4, CHD-09: mặc định bỏ trẻ đã nghỉ; theo năm học: lớp cuối trong năm, chờ xếp lớp chỉ năm đang hoạt động")
    void listFollowsSchoolYearRules() {
        assertThat(listService
                        .searchChildren(principal, filter(null, null, null))
                        .items())
                .extracting(ChildListItemResponse::id, ChildListItemResponse::className)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(binh, null),
                        org.assertj.core.groups.Tuple.tuple(an, "Chồi A1"));

        assertThat(listService
                        .searchChildren(principal, filter(null, null, ALL))
                        .items())
                .extracting(ChildListItemResponse::id, ChildListItemResponse::className)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(binh, null),
                        org.assertj.core.groups.Tuple.tuple(dung, null),
                        org.assertj.core.groups.Tuple.tuple(an, "Chồi A1"),
                        org.assertj.core.groups.Tuple.tuple(cuong, "Chồi A2"));

        assertThat(listService
                        .searchChildren(principal, filter(yearOld, null, ALL))
                        .items())
                .extracting(ChildListItemResponse::id, ChildListItemResponse::className)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(an, "Mầm A cũ"),
                        org.assertj.core.groups.Tuple.tuple(dung, null));

        assertThat(ids(listService.searchChildren(principal, filter(yearActive, null, ALL))))
                .containsExactlyInAnyOrder(an, binh, cuong);
    }

    @Test
    @DisplayName("AUTH-08: GV chỉ thấy trẻ lớp mình; lọc lớp khác ⇒ 403; từ khóa theo mã trẻ")
    void teacherScopeAndKeyword() {
        assertThat(ids(listService.searchChildren(teacherA1, filter(null, null, null))))
                .containsExactly(an);
        assertThatThrownBy(() -> listService.searchChildren(
                        teacherA1, new ChildListFilter(null, null, classA2, null, null, 0, 20)))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CLASS_ACCESS_DENIED));
        String code = jdbc.queryForObject("SELECT code FROM children WHERE id = ?", String.class, binh);
        assertThat(ids(listService.searchChildren(
                        principal, new ChildListFilter(null, null, null, null, code.toLowerCase(), 0, 20))))
                .containsExactly(binh);
    }

    // ---------- dữ liệu ----------

    private static ChildListFilter filter(Long yearId, Long campusId, Set<ChildStatus> statuses) {
        return new ChildListFilter(yearId, campusId, null, statuses, null, 0, 50);
    }

    private static List<Long> ids(com.carenest.dto.common.PageResponse<ChildListItemResponse> page) {
        return page.items().stream().map(ChildListItemResponse::id).toList();
    }

    private long classroom(long campusId, long yearId, String name) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (" + campusId + ", " + yearId
                + ", '" + name + "') RETURNING id");
    }

    private long child(String name, String status, String enrolledOn, Long intakeCampusId, String leftOn) {
        return jdbc.queryForObject(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, intake_campus_id,"
                        + " left_on, left_reason) VALUES (?, ?, '2022-03-01', 'FEMALE', ?, CAST(? AS DATE), ?,"
                        + " CAST(? AS DATE), ?) RETURNING id",
                Long.class,
                "PL" + SEQ.incrementAndGet(),
                name,
                status,
                enrolledOn,
                intakeCampusId,
                leftOn,
                leftOn == null ? null : "Chuyển nhà (giả)");
    }

    private void enroll(long childId, long classroomId, String from, String to) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to)"
                        + " VALUES (?, ?, CAST(? AS DATE), CAST(? AS DATE))",
                childId,
                classroomId,
                from,
                to);
    }

    private void declare(long childId, long userId) {
        jdbc.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state,"
                        + " self_history_state, family_history_state, tb_contact_state, declared_by, declared_at)"
                        + " VALUES (?, 'NONE_REPORTED', 'NONE_REPORTED', 'NOT_PROVIDED', 'NONE_REPORTED',"
                        + " 'NONE_REPORTED', 'NONE_REPORTED', ?, now())",
                childId,
                userId);
    }

    private long staff(String username, String role) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role) VALUES ('kc-"
                + username + "', '" + username + "', '" + username + "', '" + role + "') RETURNING id");
    }

    private static CurrentUser user(long id, CareNestRole role) {
        return new CurrentUser(
                id, "kc-" + id, "user" + id, "User " + id, null, Set.of(role), TestPermissions.defaultsOf(role));
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
