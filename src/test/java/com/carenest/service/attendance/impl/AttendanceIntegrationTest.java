package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.attendance.AttendanceClassResponse;
import com.carenest.dto.attendance.AttendanceSummaryResponse;
import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.DailyHealthRequest;
import com.carenest.dto.attendance.DailyHealthResponse;
import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.DayUnlockResponse;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest.Entry;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthAction;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.TemperatureLevel;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

/** Điểm danh trên PostgreSQL thật (V21). Hôm nay = thứ 4, 21/10/2026; giờ chốt năm học 08:30. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class AttendanceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant BEFORE_CUTOFF = Instant.parse("2026-10-21T01:00:00Z"); // 08:00 giờ trường
    private static final Instant AFTER_CUTOFF = Instant.parse("2026-10-21T02:00:00Z"); // 09:00 giờ trường

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    /** Đồng hồ đổi được giữa các bước test (trước/sau giờ chốt). */
    static final class MovableClock extends Clock {
        private Instant now = BEFORE_CUTOFF;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired
    private AttendanceService service;

    @Autowired
    private AttendanceDayUnlockService unlockService;

    @Autowired
    private DailyHealthService healthService;

    @Autowired
    private MovableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private long classA;
    private long classB;
    private long oldClass;
    private long child1;
    private long child2;

    @BeforeEach
    void setUp() {
        clock.now = BEFORE_CUTOFF;
        long campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long closedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = classroom(campusA, year, "Lớp A", ag3);
        classB = classroom(campusB, year, "Lớp B", ag3);
        oldClass = classroom(campusA, closedYear, "Lớp cũ", ag3);
        child1 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'An', '2023-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        child2 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Bình', '2023-02-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        long child3 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Chi', '2023-03-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        enroll(child1, classA);
        enroll(child2, classA);
        enroll(child3, classB);

        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("vpa", CareNestRole.VICE_PRINCIPAL);
        vpB = user("vpb", CareNestRole.VICE_PRINCIPAL);
        campusRole(vpA, campusA);
        campusRole(vpB, campusB);
        teacherA = user("teachera", CareNestRole.TEACHER);
        teacherB = user("teacherb", CareNestRole.TEACHER);
        teach(teacherA, classA);
        teach(teacherA, oldClass);
        teach(teacherB, classB);
    }

    @Test
    @DisplayName("HT thu hồi quyền Xem điểm danh: GV có quyền ghi vẫn thấy và ghi lớp đang dạy, không thấy lớp khác")
    void teacherWithoutViewPermissionStillRecordsOwnClass() {
        CurrentUser recordOnly = new CurrentUser(
                teacherA.userId(),
                teacherA.keycloakUserId(),
                teacherA.username(),
                teacherA.fullName(),
                null,
                teacherA.roles(),
                Set.of(Permission.ATTENDANCE_RECORD, Permission.DAILY_HEALTH_RECORD));
        assertThat(service.getClasses(recordOnly, null))
                .extracting(AttendanceClassResponse::id)
                .containsExactly(classA);
        assertThat(service.getClassAttendance(recordOnly, classA, TODAY)).isNotNull();
        service.saveClassAttendance(recordOnly, classA, request(TODAY, present(child1)));

        CurrentUser nothing = new CurrentUser(
                teacherA.userId(),
                teacherA.keycloakUserId(),
                teacherA.username(),
                teacherA.fullName(),
                null,
                teacherA.roles(),
                Set.of());
        assertThat(service.getClasses(nothing, null)).isEmpty();
    }

    @Test
    void teacherSeesOwnClassesAndRecordsTodayBeforeCutoff() {
        List<AttendanceClassResponse> classes = service.getClasses(teacherA, null);
        assertThat(classes).extracting(AttendanceClassResponse::id).containsExactly(classA);
        assertThat(classes.getFirst().canRecord()).isTrue();
        assertThat(classes.getFirst().canUnlock()).isFalse();
        // Scope SCHOOL: PHT thấy lớp mọi cơ sở nhưng quyền mở lại vẫn theo ATTENDANCE_UNLOCK riêng.
        assertThat(service.getClasses(withSchoolWide(vpA, Permission.ATTENDANCE_VIEW), null))
                .extracting(AttendanceClassResponse::id, AttendanceClassResponse::canUnlock)
                .containsExactly(tuple(classA, true), tuple(classB, false));
        assertThat(service.getClasses(principal, null)).hasSize(2);
        // Năm chọn ở header là năm đã kết thúc ⇒ lớp năm đó, chỉ xem
        long closedYear = jdbc.queryForObject("SELECT id FROM school_years WHERE name = '2025-2026'", Long.class);
        assertThat(service.getClasses(teacherA, closedYear))
                .extracting(
                        AttendanceClassResponse::id,
                        AttendanceClassResponse::teaches,
                        AttendanceClassResponse::canRecord)
                .containsExactly(tuple(oldClass, true, false));

        ClassAttendanceResponse empty = service.getClassAttendance(teacherA, classA, TODAY);
        assertThat(empty.canRecord()).isTrue();
        assertThat(empty.day().locked()).isFalse();
        assertThat(empty.mealSessions()).containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        assertThat(empty.children()).extracting(r -> r.child().fullName()).containsExactly("An", "Bình");

        var saved = service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));
        assertThat(saved.changed()).isEqualTo(2);

        ClassAttendanceResponse sheet = service.getClassAttendance(teacherA, classA, TODAY);
        assertThat(sheet.children().getFirst().record().status()).isEqualTo(AttendanceStatus.PRESENT);
        // ATT-02: có mặt là ăn mọi bữa của nhóm tuổi, dù client chỉ gửi bữa trưa
        assertThat(sheet.children().getFirst().record().meals())
                .containsEntry(MealSession.LUNCH, true)
                .containsEntry(MealSession.AFTERNOON, true);
        assertThat(sheet.children().getLast().record().meals()).doesNotContainValue(true);
        expectError(
                () -> service.saveClassAttendance(teacherB, classA, request(TODAY, present(child1))),
                ApiCode.FORBIDDEN);
        expectError(() -> service.getClassAttendance(teacherB, classA, TODAY), ApiCode.FORBIDDEN);
    }

    @Test
    void teacherViewsOwnClassOfClosedYearReadOnly() {
        // GV B từng dạy lớp năm cũ (phân công đã kết thúc) và từng dạy lớp A năm nay nhưng đã bị gỡ
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2025-09-01', '2026-05-31', TRUE)",
                teacherB.userId(),
                oldClass);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', '2026-10-01', FALSE)",
                teacherB.userId(),
                classA);
        long closedYear = jdbc.queryForObject("SELECT id FROM school_years WHERE name = '2025-2026'", Long.class);

        assertThat(service.getClasses(teacherB, closedYear))
                .extracting(
                        AttendanceClassResponse::id,
                        AttendanceClassResponse::teaches,
                        AttendanceClassResponse::canRecord)
                .containsExactly(tuple(oldClass, true, false));
        assertThat(service.getClassAttendance(teacherB, oldClass, LocalDate.of(2026, 5, 29))
                        .canRecord())
                .isFalse();
        expectError(
                () -> service.saveClassAttendance(teacherB, oldClass, request(TODAY, present(child1))),
                ApiCode.FORBIDDEN);
        // Năm đang hoạt động: chỉ lớp đang được phân công
        expectError(() -> service.getClassAttendance(teacherB, classA, TODAY), ApiCode.FORBIDDEN);
        assertThat(service.getClasses(teacherB, null))
                .extracting(AttendanceClassResponse::id)
                .containsExactly(classB);
    }

    @Test
    void savingTwiceKeepsOneRecordAndAddsHistory() {
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        var same = service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        assertThat(same.changed()).isZero();
        service.saveClassAttendance(teacherA, classA, request(TODAY, absent(child1)));

        assertThat(count("SELECT count(*) FROM attendance_records WHERE child_id = ?", child1))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT h.action FROM attendance_record_history h JOIN attendance_records r ON r.id = h.record_id"
                                + " WHERE r.child_id = ? ORDER BY h.id",
                        String.class,
                        child1))
                .containsExactly("RECORDED", "UPDATED");
    }

    @Test
    void rejectsInvalidEntries() {
        expectCode(
                () -> service.saveClassAttendance(
                        teacherA,
                        classA,
                        request(TODAY, new Entry(child2, AttendanceStatus.EXCUSED, Map.of(MealSession.LUNCH, true)))),
                ApiCode.ATTENDANCE_EXCUSED_NOT_ALLOWED);
        expectError(
                () -> service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), present(child1))),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> service.saveClassAttendance(teacherA, classA, request(TODAY.minusDays(1), present(child1))),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> service.saveClassAttendance(
                        teacherA, classA, request(LocalDate.of(2026, 10, 24), present(child1))),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> service.saveClassAttendance(teacherA, oldClass, request(TODAY, present(child1))),
                ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("ATT-02: suất tính từ trạng thái — có mặt ⇒ mọi bữa của nhóm tuổi, field meals client gửi bị bỏ qua")
    void mealsFollowAttendanceStatusAndIgnoreClientMeals() {
        service.saveClassAttendance(
                teacherA,
                classA,
                request(
                        TODAY,
                        new Entry(child1, AttendanceStatus.PRESENT, Map.of(MealSession.MORNING, true)),
                        new Entry(child2, AttendanceStatus.PRESENT, null)));

        assertThat(service.getClassAttendance(teacherA, classA, TODAY).children())
                .allSatisfy(row -> assertThat(row.record().meals())
                        .containsEntry(MealSession.LUNCH, true)
                        .containsEntry(MealSession.AFTERNOON, true)
                        .doesNotContainKey(MealSession.MORNING));
        AttendanceService.ClassMealTally tally =
                service.tallyMeals(TODAY, classCampus(classA), classYear(classA)).stream()
                        .filter(t -> t.classroomId() == classA)
                        .findFirst()
                        .orElseThrow();
        assertThat(tally.meals()).containsEntry(MealSession.LUNCH, 2).containsEntry(MealSession.AFTERNOON, 2);
        // Tắt bữa phía client (meals false) không còn bớt suất
        var resend = service.saveClassAttendance(
                teacherA,
                classA,
                request(TODAY, new Entry(child1, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, false))));
        assertThat(resend.changed()).isZero();
    }

    @Test
    @DisplayName(
            "ATT-09: GV không tự chọn Vắng có phép; trạng thái có phép đã có (từ đơn nghỉ) vẫn đọc và gửi lại được")
    void teacherCannotSetExcusedButKeepsExistingExcused() {
        expectCode(
                () -> service.saveClassAttendance(
                        teacherA,
                        classA,
                        request(TODAY, present(child1), new Entry(child2, AttendanceStatus.EXCUSED, null))),
                ApiCode.ATTENDANCE_EXCUSED_NOT_ALLOWED);
        assertThat(count("SELECT count(*) FROM attendance_records WHERE child_id = ?", child1))
                .isZero();

        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));
        // Giả lập đơn nghỉ đã đặt có phép (module đơn nghỉ chưa có)
        entityManager.flush();
        jdbc.update("UPDATE attendance_records SET status = 'EXCUSED' WHERE child_id = ?", child2);
        entityManager.clear();
        var resend = service.saveClassAttendance(
                teacherA, classA, request(TODAY, present(child1), new Entry(child2, AttendanceStatus.EXCUSED, null)));
        assertThat(resend.changed()).isZero();
        assertThat(service.getClassAttendance(teacherA, classA, TODAY)
                        .children()
                        .getLast()
                        .record()
                        .status())
                .isEqualTo(AttendanceStatus.EXCUSED);
    }

    @Test
    void lockedAfterCutoffUntilUnlockedAndAfterRevoke() {
        clock.now = AFTER_CUTOFF;
        assertThat(service.getDayInfo(teacherA, TODAY, classA).locked()).isTrue();
        expectError(
                () -> service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1))), ApiCode.CONFLICT);

        DayUnlockRequest unlockRequest =
                new DayUnlockRequest(classA, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "  GV quên lưu  ");
        expectError(() -> unlockService.unlockDay(vpB, unlockRequest), ApiCode.FORBIDDEN);
        // HT cấp ATTENDANCE_VIEW phạm vi SCHOOL nhưng ATTENDANCE_UNLOCK vẫn chỉ ở campus B.
        ClassAttendanceResponse otherCampus =
                service.getClassAttendance(withSchoolWide(vpB, Permission.ATTENDANCE_VIEW), classA, TODAY);
        assertThat(otherCampus.classId()).isEqualTo(classA);
        assertThat(otherCampus.canRecord()).isFalse();
        DayUnlockResponse unlock = unlockService.unlockDay(vpA, unlockRequest);
        assertThat(unlock.reason()).isEqualTo("GV quên lưu");
        assertThat(unlock.unlockedByName()).isEqualTo("vpa");
        expectError(() -> unlockService.unlockDay(principal, unlockRequest), ApiCode.CONFLICT);

        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        assertThat(count("SELECT count(*) FROM attendance_record_history WHERE unlock_id = ?", unlock.id()))
                .isEqualTo(1);
        assertThat(unlockService.getUnlocks(vpA)).hasSize(1);
        assertThat(unlockService.getUnlocks(vpB)).isEmpty();

        unlockService.revokeUnlock(principal, unlock.id());
        expectError(
                () -> service.saveClassAttendance(teacherA, classA, request(TODAY, absent(child1))), ApiCode.CONFLICT);
        expectError(() -> unlockService.revokeUnlock(principal, unlock.id()), ApiCode.CONFLICT);
    }

    @Test
    void unlockNeedsLockedDayAndValidDeadline() {
        expectError(
                () -> unlockService.unlockDay(
                        vpA, new DayUnlockRequest(classA, TODAY, BEFORE_CUTOFF.plusSeconds(3600), "x")),
                ApiCode.CONFLICT);
        expectError(
                () -> unlockService.unlockDay(
                        vpA, new DayUnlockRequest(classA, TODAY.minusDays(1), BEFORE_CUTOFF.minusSeconds(60), "x")),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> unlockService.unlockDay(
                        vpA,
                        new DayUnlockRequest(
                                classA, LocalDate.of(2027, 6, 1), Instant.parse("2027-06-02T00:00:00Z"), "x")),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> unlockService.unlockDay(
                        vpA,
                        new DayUnlockRequest(classA, TODAY.minusDays(1), Instant.parse("2027-06-01T00:00:00Z"), "x")),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void summaryCountsDaysChildrenAndMeals() {
        // Trẻ có khai dị ứng: lượt ăn tính là suất thay thế (giống Sổ báo ăn)
        jdbc.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state,"
                        + " self_history_state, family_history_state, tb_contact_state, declared_by)"
                        + " VALUES (?, 'REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED',"
                        + " 'NONE_REPORTED', ?)",
                child1,
                principal.userId());
        jdbc.update("INSERT INTO child_allergies (child_id, name) VALUES (?, 'Tôm')", child1);
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));

        AttendanceSummaryResponse.ClassSummary summary =
                service.getClassSummary(teacherA, classA, TODAY.minusDays(2), TODAY);
        assertThat(summary.days()).hasSize(3);
        var today = summary.days().getLast();
        assertThat(today.present()).isEqualTo(1);
        assertThat(today.unexcused()).isEqualTo(1);
        assertThat(today.mealTotal()).isEqualTo(1);
        assertThat(today.substitute()).isEqualTo(1);
        assertThat(today.meals()).containsEntry(MealSession.LUNCH, 1);
        assertThat(summary.days().getFirst().missing()).isEqualTo(2);

        AttendanceSummaryResponse.MultiSummary all = service.getSummary(principal, null, null, TODAY, TODAY);
        assertThat(all.size()).isEqualTo(3);
        assertThat(all.days().getFirst().missing()).isEqualTo(1);
        // PHT chỉ xem toàn trường khi ATTENDANCE_VIEW được cấp scope SCHOOL.
        assertThat(service.getSummary(withSchoolWide(vpB, Permission.ATTENDANCE_VIEW), null, null, TODAY, TODAY)
                        .classes())
                .extracting(AttendanceSummaryResponse.ClassTotal::classId)
                .containsExactlyElementsOf(all.classes().stream()
                        .map(AttendanceSummaryResponse.ClassTotal::classId)
                        .toList());
        expectError(() -> service.getSummary(teacherA, null, null, TODAY, TODAY), ApiCode.FORBIDDEN);
        expectError(() -> service.getClassSummary(teacherA, classA, TODAY, TODAY.plusDays(1)), ApiCode.BAD_REQUEST);
    }

    @Test
    void teacherRecordsHealthOnlyForPresentChildToday() {
        expectError(
                () -> healthService.addEntry(teacherA, health("r1", child1, HealthObservation.NO_CONCERN)),
                ApiCode.CONFLICT);
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));

        var first = healthService.addEntry(teacherA, health("r1", child1, HealthObservation.NO_CONCERN));
        var again = healthService.addEntry(teacherA, health("r1", child1, HealthObservation.NO_CONCERN));
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(first.temperature()).isEqualByComparingTo("36.8");
        assertThat(first.recordedByName()).isEqualTo("teachera");
        expectError(
                () -> healthService.addEntry(teacherA, health("r2", child2, HealthObservation.NO_CONCERN)),
                ApiCode.CONFLICT);
        expectError(
                () -> healthService.addEntry(teacherB, health("r3", child1, HealthObservation.NO_CONCERN)),
                ApiCode.FORBIDDEN);
        // Có dấu hiệu mà không có biểu hiện / ghi chú; xử trí khi không có dấu hiệu; chuyển viện thiếu nơi/giờ
        expectError(
                () -> healthService.addEntry(teacherA, health("r4", child1, HealthObservation.NOTED)),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> healthService.addEntry(
                        teacherA,
                        new DailyHealthRequest(
                                "r5",
                                classA,
                                child1,
                                TODAY,
                                null,
                                HealthObservation.NO_CONCERN,
                                List.of(),
                                "",
                                List.of(HealthAction.MONITOR),
                                null)),
                ApiCode.BAD_REQUEST);
        expectError(
                () -> healthService.addEntry(
                        teacherA,
                        new DailyHealthRequest(
                                "r6",
                                classA,
                                child1,
                                TODAY,
                                null,
                                HealthObservation.NOTED,
                                List.of(HealthSymptom.FEVER),
                                "",
                                List.of(HealthAction.REFERRAL),
                                null)),
                ApiCode.BAD_REQUEST);

        healthService.addEntry(
                teacherA,
                new DailyHealthRequest(
                        "r7",
                        classA,
                        child1,
                        TODAY,
                        new BigDecimal("38.2"),
                        HealthObservation.NOTED,
                        List.of(HealthSymptom.FEVER),
                        "Sốt",
                        List.of(HealthAction.MEDICAL_ROOM),
                        null));
        DailyHealthResponse.ChildDay day = healthService.getClassChildHealth(teacherA, classA, child1, TODAY);
        assertThat(day.canRecord()).isTrue();
        assertThat(day.entries()).hasSize(2);
        assertThat(day.entries().getFirst().observation()).isEqualTo(HealthObservation.NOTED);
        assertThat(healthService
                        .getClassChildHealth(teacherA, classA, child2, TODAY)
                        .canRecord())
                .isFalse();
        assertThat(healthService
                        .getOverview(vpA, TODAY, null, null, null, classA)
                        .attention())
                .extracting(DailyHealthResponse.Attention::childId)
                .containsExactly(child1);
        // Sức khỏe chỉ đo khi có mặt: đổi sang vắng thì không còn trong danh sách cần chú ý
        service.saveClassAttendance(teacherA, classA, request(TODAY, absent(child1), absent(child2)));
        assertThat(healthService
                        .getOverview(vpA, TODAY, null, null, null, classA)
                        .attention())
                .isEmpty();
    }

    @Test
    void healthStaysOpenAfterCutoffAndLocksWhenTheDayEnds() {
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        // HLT-15: sau giờ chốt điểm danh vẫn ghi thêm sức khỏe; sang ngày hôm sau thì ngày cũ đã khóa
        clock.now = AFTER_CUTOFF;
        assertThat(healthService
                        .addEntry(teacherA, health("r-late", child1, HealthObservation.NO_CONCERN))
                        .recordedAt())
                .isEqualTo(AFTER_CUTOFF);
        clock.now = AFTER_CUTOFF.plus(Duration.ofDays(1));
        expectError(
                () -> healthService.addEntry(teacherA, health("r-next", child1, HealthObservation.NO_CONCERN)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    void leadersSeeHealthOverviewInScope() {
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), present(child2)));
        healthService.addEntry(teacherA, health("r1", child1, HealthObservation.NO_CONCERN));
        healthService.addEntry(
                teacherA,
                new DailyHealthRequest(
                        "r2",
                        classA,
                        child2,
                        TODAY,
                        null,
                        HealthObservation.NOTED,
                        List.of(HealthSymptom.COUGH),
                        "",
                        List.of(),
                        null));

        DailyHealthResponse.Overview all = healthService.getOverview(principal, TODAY, null, null, null, null);
        assertThat(all.totals().present()).isEqualTo(2);
        assertThat(all.totals().checked()).isEqualTo(2);
        assertThat(all.totals().noted()).isEqualTo(1);
        assertThat(all.attention())
                .extracting(DailyHealthResponse.Attention::childName)
                .containsExactly("Bình");
        assertThat(all.classes()).hasSize(2);
        CurrentUser schoolViewer = withSchoolWide(vpB, Permission.ATTENDANCE_VIEW);
        // PHT cơ sở B xem sức khỏe toàn trường khi ATTENDANCE_VIEW được cấp scope SCHOOL.
        assertThat(healthService
                        .getOverview(schoolViewer, TODAY, null, null, null, null)
                        .classes())
                .extracting(DailyHealthResponse.ClassRow::classId)
                .containsExactlyElementsOf(all.classes().stream()
                        .map(DailyHealthResponse.ClassRow::classId)
                        .toList());
        assertThat(healthService
                        .getOverview(
                                schoolViewer,
                                TODAY,
                                null,
                                all.classes().getFirst().campusId(),
                                null,
                                null)
                        .classes())
                .isNotEmpty();
        expectError(() -> healthService.getOverview(teacherA, TODAY, null, null, null, null), ApiCode.FORBIDDEN);

        assertThat(healthService.getChildHistory(vpA, child2, TODAY).entries()).hasSize(1);
        assertThat(healthService.getChildHistory(schoolViewer, child2, TODAY).entries())
                .hasSize(1);
    }

    @Test
    void attendanceSheetShowsLatestHealthCheckPerChild() {
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1), absent(child2)));
        ClassAttendanceResponse before = service.getClassAttendance(teacherA, classA, TODAY);
        assertThat(before.children()).allSatisfy(row -> {
            assertThat(row.health().checked()).isFalse();
            assertThat(row.health().temperatureLevel()).isNull();
        });

        healthService.addEntry(
                teacherA,
                new DailyHealthRequest(
                        "r-fever",
                        classA,
                        child1,
                        TODAY,
                        new BigDecimal("39.0"),
                        HealthObservation.NOTED,
                        List.of(HealthSymptom.FEVER),
                        "",
                        List.of(HealthAction.MONITOR),
                        null));
        ClassAttendanceResponse.HealthSummary fever = service.getClassAttendance(teacherA, classA, TODAY)
                .children()
                .getFirst()
                .health();
        assertThat(fever.checked()).isTrue();
        assertThat(fever.observation()).isEqualTo(HealthObservation.NOTED);
        assertThat(fever.temperature()).isEqualByComparingTo("39.0");
        assertThat(fever.temperatureLevel()).isEqualTo(TemperatureLevel.HIGH);
        assertThat(fever.symptoms()).containsExactly(HealthSymptom.FEVER);

        // Khám lại trong ngày: nhãn theo lần mới nhất
        clock.now = clock.now.plus(Duration.ofMinutes(30));
        healthService.addEntry(teacherA, health("r-ok", child1, HealthObservation.NO_CONCERN));
        ClassAttendanceResponse.HealthSummary latest = service.getClassAttendance(teacherA, classA, TODAY)
                .children()
                .getFirst()
                .health();
        assertThat(latest.entryCount()).isEqualTo(2);
        assertThat(latest.observation()).isEqualTo(HealthObservation.NO_CONCERN);
        assertThat(latest.temperatureLevel()).isEqualTo(TemperatureLevel.NORMAL);
        assertThat(latest.symptoms()).isEmpty();
    }

    @Test
    void healthCheckJustAfterMidnightSchoolTimeCountsForThatDay() {
        // 00:30 giờ trường ngày 21/10 = 17:30 UTC ngày 20/10
        clock.now = Instant.parse("2026-10-20T17:30:00Z");
        service.saveClassAttendance(teacherA, classA, request(TODAY, present(child1)));
        healthService.addEntry(teacherA, health("r-midnight", child1, HealthObservation.NO_CONCERN));
        ClassAttendanceResponse.HealthSummary health = service.getClassAttendance(teacherA, classA, TODAY)
                .children()
                .getFirst()
                .health();
        assertThat(health.checked()).isTrue();
        assertThat(health.temperatureLevel()).isEqualTo(TemperatureLevel.NORMAL);
        assertThat(service.getClassAttendance(teacherA, classA, TODAY.minusDays(1))
                        .children())
                .allSatisfy(row -> assertThat(row.health().checked()).isFalse());
    }

    @Test
    void temperatureLevelsFollowAgreedThresholds() {
        // Bộ Y tế: sốt khi nách ≥ 37,5 °C; nhẹ / vừa / cao / rất cao tách ở 38 / 39 / 40 °C
        assertThat(TemperatureLevel.of(null)).isNull();
        assertThat(TemperatureLevel.of(new BigDecimal("35.4"))).isEqualTo(TemperatureLevel.LOW);
        assertThat(TemperatureLevel.of(new BigDecimal("35.5"))).isEqualTo(TemperatureLevel.NORMAL);
        assertThat(TemperatureLevel.of(new BigDecimal("37.4"))).isEqualTo(TemperatureLevel.NORMAL);
        assertThat(TemperatureLevel.of(new BigDecimal("37.5"))).isEqualTo(TemperatureLevel.MILD);
        assertThat(TemperatureLevel.of(new BigDecimal("37.9"))).isEqualTo(TemperatureLevel.MILD);
        assertThat(TemperatureLevel.of(new BigDecimal("38.0"))).isEqualTo(TemperatureLevel.MODERATE);
        assertThat(TemperatureLevel.of(new BigDecimal("38.9"))).isEqualTo(TemperatureLevel.MODERATE);
        assertThat(TemperatureLevel.of(new BigDecimal("39.0"))).isEqualTo(TemperatureLevel.HIGH);
        assertThat(TemperatureLevel.of(new BigDecimal("39.9"))).isEqualTo(TemperatureLevel.HIGH);
        assertThat(TemperatureLevel.of(new BigDecimal("40.0"))).isEqualTo(TemperatureLevel.VERY_HIGH);
    }

    private DailyHealthRequest health(String requestId, long childId, HealthObservation observation) {
        return new DailyHealthRequest(
                requestId, classA, childId, TODAY, new BigDecimal("36.8"), observation, List.of(), "", List.of(), null);
    }

    private static SaveAttendanceRequest request(LocalDate date, Entry... entries) {
        return new SaveAttendanceRequest(date, List.of(entries));
    }

    private static Entry present(long childId) {
        return new Entry(childId, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true));
    }

    private static Entry absent(long childId) {
        return new Entry(childId, AttendanceStatus.UNEXCUSED, Map.of());
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case PRINCIPAL, VICE_PRINCIPAL -> Set.of(Permission.ATTENDANCE_VIEW, Permission.ATTENDANCE_UNLOCK);
                    case TEACHER, TEAM_LEADER ->
                        Set.of(
                                Permission.ATTENDANCE_VIEW,
                                Permission.ATTENDANCE_RECORD,
                                Permission.DAILY_HEALTH_RECORD,
                                Permission.PICKUP_RECORD);
                    default -> Set.of();
                };
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private static CurrentUser withSchoolWide(CurrentUser user, Permission... permissions) {
        return new CurrentUser(
                user.userId(),
                user.keycloakUserId(),
                user.username(),
                user.fullName(),
                user.email(),
                user.roles(),
                user.permissions(),
                Set.of(permissions));
    }

    private void campusRole(CurrentUser user, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                user.userId(),
                campusId);
    }

    private void teach(CurrentUser user, long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                user.userId(),
                classroomId);
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusId
                + ", " + yearId + ", '" + name + "', " + ageGroupId + ") RETURNING id");
    }

    private void enroll(long childId, long classroomId) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childId,
                classroomId);
    }

    private long count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Long.class, arg);
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

    private long classCampus(long classroomId) {
        return count("SELECT campus_id FROM classrooms WHERE id = ?", classroomId);
    }

    private long classYear(long classroomId) {
        return count("SELECT school_year_id FROM classrooms WHERE id = ?", classroomId);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
