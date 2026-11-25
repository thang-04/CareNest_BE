package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.child.SavePlacementsRequest;
import com.carenest.dto.child.WithdrawChildRequest;
import com.carenest.dto.child.YearReadinessResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildRecordService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.YearTransitionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.PlacementAction;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

/**
 * Đặt lịch nghỉ học khi năm sau đang chuẩn bị (G94-180/CHD-03, user chốt 2026-11-25): bảng xếp lớp năm mới ghi Nghỉ
 * học, kích hoạt năm trước ngày nghỉ không đưa trẻ vào lớp năm mới. PostgreSQL thật, hôm nay = thứ 2 30/08/2027. Dữ liệu
 * giả.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class WithdrawNewYearBoardIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

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
    private ChildRecordService recordService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private long newYear;
    private long oldClass;
    private long newClass;
    private long child1;
    private long child2;

    // Năm 2026-2027 đang học (Lớp cũ, 2 trẻ), năm 2027-2028 sắp tới đủ điều kiện kích hoạt trừ bước xếp lớp
    @BeforeEach
    void setUp() {
        long principalId = user("principal");
        long vpId = user("vp");
        long teacher1 = user("teacher1");
        long teacher2 = user("teacher2");
        long campus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('WB-A', 'Campus A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long ag4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        // Năm đang học kết thúc 04/09/2027: ngày nghỉ học phải trong năm học đang hoạt động (user chốt 2026-11-25)
        long oldYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-09-04', 'ACTIVE') RETURNING id");
        newYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2027-2028', '2027-09-05', '2028-05-31') RETURNING id");
        long room1 = room(campus, "P.1");
        long room2 = room(campus, "P.2");
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address,"
                        + " phone, status) SELECT y.id, c.id, c.code, c.name, c.short_name, c.address, c.phone, c.status"
                        + " FROM school_years y CROSS JOIN campuses c WHERE y.id IN (?, ?)",
                oldYear,
                newYear);
        jdbc.update(
                "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name,"
                        + " age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note)"
                        + " SELECT y.id, g.id, g.name, g.short_name, g.age_from_months, g.age_to_months,"
                        + " g.meal_sessions, g.max_class_size, g.nutrition_note"
                        + " FROM school_years y CROSS JOIN age_groups g WHERE y.id IN (?, ?)",
                oldYear,
                newYear);
        jdbc.update(
                "INSERT INTO school_year_room_members (school_year_id, room_id, campus_id, code, name, room_type,"
                        + " capacity, manager_user_id, status, note) SELECT y.id, r.id, r.campus_id, r.code, r.name,"
                        + " r.room_type, r.capacity, r.manager_user_id, r.status, r.note"
                        + " FROM school_years y CROSS JOIN rooms r WHERE y.id IN (?, ?)",
                oldYear,
                newYear);
        oldClass = classroom(campus, oldYear, "Lớp cũ", ag3, room1);
        newClass = classroom(campus, newYear, "Lớp nhỡ", ag4, room2);
        child1 = child("Trẻ 1", "2023-01-01");
        child2 = child("Trẻ 2", "2023-02-01");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from, valid_to) VALUES"
                        + " (?, 'VICE_PRINCIPAL', ?, '2026-09-01', NULL), (?, 'VICE_PRINCIPAL', ?, '2027-08-05',"
                        + " '2028-05-31')",
                vpId,
                campus,
                vpId,
                campus);
        teacher(teacher1, newClass, true);
        teacher(teacher2, newClass, false);
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30',"
                        + " '2027-09-05')",
                newYear);
        principal =
                new CurrentUser(principalId, "kc-principal", "principal", "P", null, Set.of(CareNestRole.PRINCIPAL));
    }

    @Test
    @DisplayName("G94-180/CHD-03: trẻ đã xếp Lên lớp rồi đặt lịch nghỉ từ ngày mai ⇒ trẻ vẫn Đang học, bảng năm mới ghi"
            + " Nghỉ học, lịch sử LEAVE_SCHEDULED; kích hoạt năm ngay hôm nay (trước ngày nghỉ) ⇒ trẻ không vào lớp năm"
            + " mới, phụ huynh không nhận báo lớp mới")
    void promotedChildWithScheduledLeaveIsNotMovedOnActivation() {
        long parent = user("parent-leaving");
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id,"
                        + " linked_at) VALUES (?, 0, 'Mẹ', 'MOTHER', '0918888102', '999900000582', ?, now())",
                child1,
                parent);
        service.savePlacements(
                principal,
                newYear,
                new SavePlacementsRequest(List.of(
                        new SavePlacementsRequest.Item(child1, PlacementAction.PROMOTE, newClass),
                        new SavePlacementsRequest.Item(child2, PlacementAction.PROMOTE, newClass))));

        // Hôm nay thứ 2 30/08/2027, nghỉ từ thứ 3 31/08 ⇒ lịch nghỉ, job 00:05 ngày 31/08 mới cho nghỉ
        recordService.withdraw(principal, child1, new WithdrawChildRequest("2027-08-31", "Chuyển trường"));
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                        "SELECT status || '|' || scheduled_leave_on FROM children WHERE id = ?", String.class, child1))
                .isEqualTo("ACTIVE|2027-08-31");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM child_history WHERE child_id = ? AND action = 'LEAVE_SCHEDULED'",
                        Integer.class,
                        child1))
                .isEqualTo(1);
        service.activateSchoolYear(principal, newYear, "2027-2028");
        entityManager.flush();

        LocalDate firstDay = LocalDate.of(2027, 9, 5);
        assertThat(enrollmentService.findActiveClassroomIds(child1, firstDay)).isEmpty();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE type = 'CHILD_PLACED' AND user_id = ?",
                        Integer.class,
                        parent))
                .isZero();
        assertThat(enrollmentService.findActiveClassroomIds(child1, LocalDate.of(2027, 8, 30)))
                .containsExactly(oldClass);
        assertThat(enrollmentService.findActiveClassroomIds(child2, firstDay)).containsExactly(newClass);
        assertThat(placementOf(child1)).isEqualTo("LEAVE:" + oldClass + ":-");
    }

    @Test
    @DisplayName("G94-180/CHD-03: trẻ chưa xếp lớp năm mới mà đặt lịch nghỉ từ ngày mai ⇒ ghi Nghỉ học, không chặn"
            + " kích hoạt năm hôm nay, không vào lớp năm mới")
    void unplacedChildWithScheduledLeaveDoesNotBlockActivation() {
        service.savePlacements(
                principal,
                newYear,
                new SavePlacementsRequest(
                        List.of(new SavePlacementsRequest.Item(child2, PlacementAction.PROMOTE, newClass))));

        recordService.withdraw(principal, child1, new WithdrawChildRequest("2027-08-31", "Chuyển trường"));
        entityManager.flush();

        YearReadinessResponse readiness = service.getReadiness(principal, newYear);
        assertThat(readiness.steps())
                .filteredOn(st -> st.key().equals("placement"))
                .singleElement()
                .satisfies(st -> assertThat(st.detail()).startsWith("2/2"));
        assertThat(readiness.canActivate()).isTrue();
        assertThat(placementOf(child1)).isEqualTo("LEAVE:" + oldClass + ":-");
        service.activateSchoolYear(principal, newYear, "2027-2028");
        entityManager.flush();
        assertThat(enrollmentService.findActiveClassroomIds(child1, LocalDate.of(2027, 9, 5)))
                .isEmpty();
    }

    @Test
    @DisplayName("G94-180/CHD-03, CHD-04: đặt lịch nghỉ từ ngày mai rồi kích hoạt năm mới hôm nay (trước ngày nghỉ) ⇒"
            + " hủy lịch bị chặn 409 CHILD_LEAVE_LOCKED_BY_NEW_YEAR, lịch và dòng Nghỉ học giữ nguyên")
    void cancelAfterActivationBeforeLeaveDayIsRejected() {
        service.savePlacements(
                principal,
                newYear,
                new SavePlacementsRequest(
                        List.of(new SavePlacementsRequest.Item(child2, PlacementAction.PROMOTE, newClass))));
        recordService.withdraw(principal, child1, new WithdrawChildRequest("2027-08-31", "Chuyển trường"));
        entityManager.flush();
        service.activateSchoolYear(principal, newYear, "2027-2028");
        entityManager.flush();

        assertThatThrownBy(() -> recordService.cancelScheduledLeave(principal, child1))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_LEAVE_LOCKED_BY_NEW_YEAR);
                    assertThat(ex.getMessage())
                            .isEqualTo("Năm học mới đã kích hoạt khi trẻ có lịch nghỉ nên trẻ không có lớp năm mới."
                                    + " Không hủy được lịch nghỉ; sau ngày nghỉ dùng \"Mở lại hồ sơ\" để nhận lại trẻ");
                });

        entityManager.flush();
        assertThat(jdbc.queryForObject(
                        "SELECT status || '|' || scheduled_leave_on FROM children WHERE id = ?", String.class, child1))
                .isEqualTo("ACTIVE|2027-08-31");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM child_history WHERE child_id = ? AND action = 'LEAVE_CANCELLED'",
                        Integer.class,
                        child1))
                .isZero();
        assertThat(placementOf(child1)).isEqualTo("LEAVE:" + oldClass + ":-");
    }

    // Quyết định xếp lớp năm mới của trẻ: hành động:lớp nguồn:lớp đích ("-" khi không có lớp đích)
    private String placementOf(long childId) {
        return jdbc.queryForObject(
                "SELECT action || ':' || from_classroom_id || ':' || coalesce(to_classroom_id::text, '-')"
                        + " FROM class_placements WHERE school_year_id = ? AND child_id = ?",
                String.class,
                newYear,
                childId);
    }

    // Trẻ đang học Lớp cũ từ đầu năm 2026-2027
    private long child(String fullName, String dateOfBirth) {
        long id = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES"
                + " ('WB' || substr(md5(random()::text), 1, 12), '" + fullName + "', '" + dateOfBirth
                + "', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                id,
                oldClass);
        return id;
    }

    private long user(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
    }

    private long room(long campusId, String code) {
        return insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campusId + ", '"
                + code + "', 'Phòng " + code + "', 'CLASS', 30) RETURNING id");
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId, long roomId) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity) "
                + "VALUES (" + campusId + ", " + yearId + ", '" + name + "', " + ageGroupId + ", " + roomId
                + ", 25) RETURNING id");
    }

    private void teacher(long userId, long classroomId, boolean homeroom) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2027-09-05', '2028-05-31', ?)",
                userId,
                classroomId,
                homeroom);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
