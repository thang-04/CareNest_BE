package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.ParentWeeklyCommentResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeekOption;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentBoardResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentChildRow;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentDraftRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSaveRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSendRequest;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClient;
import com.carenest.integration.ai.AiException;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.observation.WeeklyCommentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.WeeklyCommentSource;
import java.time.Clock;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Nhận xét tuần (OBS-29) trên PostgreSQL thật. Hôm nay = thứ 6, 30/10/2026; tuần đang xét 26–30/10/2026. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class WeeklyCommentIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 26);
    private static final Instant NOW = Instant.parse("2026-10-30T03:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return CLOCK;
        }
    }

    // Đổi được "hôm nay" trong từng test (vd. thứ 5, trước ngày học cuối của tuần)
    private static final MovableClock CLOCK = new MovableClock();

    static final class MovableClock extends Clock {
        private Instant now = NOW;

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @MockitoBean
    private EduLessonQueryService lessonQueryService;

    @MockitoBean
    private AiClient aiClient;

    @Autowired
    private WeeklyCommentService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private CurrentUser parent1;
    private CurrentUser parentOther;
    private long classA;
    private long classB;
    private long oldClass;
    private long child1;
    private long child2;
    private long child3;

    @BeforeEach
    void setUp() {
        CLOCK.set(NOW);
        long campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long closedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA = classroom(campusA, year, "Lớp A", ag3);
        classB = classroom(campusB, year, "Lớp B", ag3);
        oldClass = classroom(campusA, closedYear, "Lớp cũ", ag3);
        child1 = child("An");
        child2 = child("Bình");
        child3 = child("Chi");
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
        parent1 = user("parent1", CareNestRole.PARENT);
        parentOther = user("parent2", CareNestRole.PARENT);
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id, "
                        + "linked_at) VALUES (?, 0, 'Mẹ', 'MOTHER', '0918888101', '999900000581', ?, now())",
                child1,
                parent1.userId());
        // An đi học thứ 2, thứ 3; Bình chưa đi ngày nào trong tuần
        attendance(child1, classA, WEEK);
        attendance(child1, classA, WEEK.plusDays(1));
    }

    @Test
    @DisplayName("OBS-29: viết nháp cả lớp theo mẫu câu, trẻ nghỉ cả tuần có nháp hỏi thăm; AI tắt thì không ghi AI")
    void teacherWritesTemplateDraftsForWholeClass() {
        WeeklyCommentBoardResponse board =
                service.writeDrafts(teacherA, new WeeklyCommentDraftRequest(classA, WEEK, null, false, true));

        assertThat(board.aiEnabled()).isFalse();
        assertThat(board.aiFallback()).isFalse();
        assertThat(board.canEdit()).isTrue();
        WeeklyCommentChildRow an = row(board, child1);
        assertThat(an.status()).isEqualTo("DRAFT");
        assertThat(an.draftSource()).isEqualTo(WeeklyCommentSource.TEMPLATE);
        assertThat(an.content()).contains("Tuần này con đi học 2/5 ngày").doesNotContain("An");
        assertThat(an.data().presentDays()).isEqualTo(2);
        assertThat(an.data().schoolDays()).isEqualTo(5);
        WeeklyCommentChildRow binh = row(board, child2);
        assertThat(binh.status()).isEqualTo("DRAFT");
        assertThat(binh.absentAllWeek()).isTrue();
        assertThat(binh.draftSource()).isEqualTo(WeeklyCommentSource.TEMPLATE);
        assertThat(binh.content()).contains("nghỉ học cả tuần").doesNotContain("Bình");
    }

    @Test
    @DisplayName("OBS-29: bản cô đã sửa không bị viết nháp đè, trừ khi cô chọn viết lại")
    void draftKeepsTeacherTextUnlessOverwrite() {
        service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child1, "Cô tự viết nhận xét cho con."));
        WeeklyCommentBoardResponse kept =
                service.writeDrafts(teacherA, new WeeklyCommentDraftRequest(classA, WEEK, null, false, false));
        assertThat(row(kept, child1).content()).isEqualTo("Cô tự viết nhận xét cho con.");

        WeeklyCommentBoardResponse rewritten =
                service.writeDrafts(teacherA, new WeeklyCommentDraftRequest(classA, WEEK, child1, true, false));
        assertThat(row(rewritten, child1).content()).startsWith("Tuần này con đi học");
    }

    @Test
    @DisplayName("OBS-29: AI viết nháp khi bật; AI lỗi thì viết theo mẫu câu và báo aiFallback")
    void aiDraftAndFallback() {
        when(aiClient.isEnabled()).thenReturn(true);
        when(aiClient.complete(any())).thenReturn(new AiClient.AiResult("Tuần này con đi học vui vẻ.", "test"));
        WeeklyCommentBoardResponse ai =
                service.writeDrafts(teacherA, new WeeklyCommentDraftRequest(classA, WEEK, child1, false, true));
        assertThat(ai.aiEnabled()).isTrue();
        assertThat(row(ai, child1).draftSource()).isEqualTo(WeeklyCommentSource.AI);
        assertThat(row(ai, child1).content()).isEqualTo("Tuần này con đi học vui vẻ.");
        // Dữ liệu gửi AI không có họ tên, mã trẻ
        ArgumentCaptor<AiClient.AiRequest> sent = ArgumentCaptor.forClass(AiClient.AiRequest.class);
        verify(aiClient).complete(sent.capture());
        assertThat(sent.getValue().userPrompt()).contains("Đi học 2/5").doesNotContain("An");

        when(aiClient.complete(any())).thenThrow(new AiException(AiException.Reason.UNAVAILABLE, "down", null));
        WeeklyCommentBoardResponse fallback =
                service.writeDrafts(teacherA, new WeeklyCommentDraftRequest(classA, WEEK, child1, true, true));
        assertThat(fallback.aiFallback()).isTrue();
        assertThat(row(fallback, child1).draftSource()).isEqualTo(WeeklyCommentSource.TEMPLATE);
    }

    @Test
    @DisplayName("OBS-29: gửi 1 trẻ báo phụ huynh đã liên kết; đã gửi thì khóa sửa, viết nháp, gửi lại")
    void sendNotifiesLinkedParentsAndLocks() {
        WeeklyCommentBoardResponse sent = service.send(
                teacherA, new WeeklyCommentSendRequest(classA, WEEK, child1, "Tuần này con đi học chăm, vui vẻ."));
        WeeklyCommentChildRow an = row(sent, child1);
        assertThat(an.status()).isEqualTo("SENT");
        assertThat(an.sentByName()).isEqualTo("teachera");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE type = 'WEEKLY_COMMENT_SENT' AND user_id = ?",
                        Integer.class,
                        parent1.userId()))
                .isEqualTo(1);

        expectCode(
                () -> service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child1, "Sửa")),
                ApiCode.WEEKLY_COMMENT_ALREADY_SENT);
        expectCode(
                () -> service.writeDrafts(teacherA, new WeeklyCommentDraftRequest(classA, WEEK, child1, true, false)),
                ApiCode.WEEKLY_COMMENT_ALREADY_SENT);
        expectCode(
                () -> service.send(teacherA, new WeeklyCommentSendRequest(classA, WEEK, child1, null)),
                ApiCode.WEEKLY_COMMENT_ALREADY_SENT);
    }

    @Test
    @DisplayName("OBS-29: gửi cả lớp chỉ gửi bản nháp có nội dung; không có bản nào thì báo lỗi")
    void sendWholeClassSendsFilledDrafts() {
        expectCode(
                () -> service.send(teacherA, new WeeklyCommentSendRequest(classA, WEEK, null, null)),
                ApiCode.WEEKLY_COMMENT_NOTHING_TO_SEND);
        service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child1, "Con học tốt."));
        service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child2, ""));

        WeeklyCommentBoardResponse board =
                service.send(teacherA, new WeeklyCommentSendRequest(classA, WEEK, null, null));

        assertThat(row(board, child1).status()).isEqualTo("SENT");
        assertThat(row(board, child2).status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("OBS-29: nội dung trống khi gửi, quá 2000 ký tự, từ chẩn đoán đều bị chặn")
    void contentValidation() {
        expectCode(
                () -> service.send(teacherA, new WeeklyCommentSendRequest(classA, WEEK, child1, "   ")),
                ApiCode.EVALUATION_CONTENT_REQUIRED);
        expectCode(
                () -> service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child1, "a".repeat(2001))),
                ApiCode.EVALUATION_CONTENT_TOO_LONG);
        expectCode(
                () -> service.saveDraft(
                        teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child1, "Con bị tự kỷ nhẹ")),
                ApiCode.EVALUATION_DIAGNOSTIC_TERM);
    }

    @Test
    @DisplayName("OBS-29: tuần chưa bắt đầu, tuần ngoài năm học, trẻ không thuộc lớp đều bị chặn")
    void weekAndChildValidation() {
        expectCode(() -> service.getBoard(teacherA, classA, WEEK.plusWeeks(1)), ApiCode.WEEKLY_COMMENT_FUTURE_WEEK);
        expectCode(
                () -> service.getBoard(teacherA, classA, LocalDate.of(2026, 8, 3)),
                ApiCode.WEEKLY_COMMENT_WEEK_INVALID);
        expectCode(
                () -> service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child3, "Con học tốt.")),
                ApiCode.WEEKLY_COMMENT_CHILD_NOT_IN_CLASS);
        // Ngày giữa tuần quy về thứ Hai của tuần đó
        assertThat(service.getBoard(teacherA, classA, WEEK.plusDays(2)).weekStart())
                .isEqualTo(WEEK);
    }

    @Test
    @DisplayName("AUTH-03: GV lớp khác không viết; năm đã kết thúc không viết; PHT xem bản đã gửi, không thấy nháp")
    void accessRules() {
        expectCode(
                () -> service.writeDrafts(teacherB, new WeeklyCommentDraftRequest(classA, WEEK, null, false, false)),
                ApiCode.EVALUATION_EDIT_FORBIDDEN);
        expectCode(
                () -> service.writeDrafts(teacherA, new WeeklyCommentDraftRequest(oldClass, WEEK, null, false, false)),
                ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        expectCode(() -> service.getBoard(vpB, classA, WEEK), ApiCode.EVALUATION_VIEW_FORBIDDEN);

        service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child2, "Nháp của cô."));
        service.send(teacherA, new WeeklyCommentSendRequest(classA, WEEK, child1, "Con học tốt."));
        WeeklyCommentBoardResponse view = service.getBoard(vpA, classA, WEEK);
        assertThat(view.canEdit()).isFalse();
        assertThat(row(view, child1).content()).isEqualTo("Con học tốt.");
        assertThat(row(view, child2).content()).isNull();
        assertThat(service.getBoard(principal, classA, WEEK).children()).hasSize(2);
    }

    @Test
    @DisplayName("AUTH-04: phụ huynh đã liên kết xem bản đã gửi; phụ huynh khác bị chặn; bản nháp không hiện")
    void parentSeesOnlySentCommentsOfLinkedChild() {
        service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child1, "Nháp chưa gửi."));
        assertThat(service.listForParent(parent1, child1)).isEmpty();

        service.send(teacherA, new WeeklyCommentSendRequest(classA, WEEK, child1, "Con học tốt."));
        List<ParentWeeklyCommentResponse> list = service.listForParent(parent1, child1);
        assertThat(list).singleElement().satisfies(c -> {
            assertThat(c.content()).isEqualTo("Con học tốt.");
            assertThat(c.weekStart()).isEqualTo(WEEK);
            assertThat(c.weekFrom()).isEqualTo(WEEK);
            assertThat(c.weekEnd()).isEqualTo(WEEK.plusDays(4));
            assertThat(c.className()).isEqualTo("Lớp A");
            assertThat(c.teacherName()).isEqualTo("teachera");
        });
        expectCode(() -> service.listForParent(parentOther, child1), ApiCode.WEEKLY_COMMENT_PARENT_FORBIDDEN);
        expectCode(() -> service.listForParent(teacherA, child1), ApiCode.WEEKLY_COMMENT_PARENT_FORBIDDEN);
    }

    private static WeeklyCommentChildRow row(WeeklyCommentBoardResponse board, long childId) {
        return board.children().stream()
                .filter(r -> r.childId() == childId)
                .findFirst()
                .orElseThrow();
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case PRINCIPAL, VICE_PRINCIPAL -> Set.of(Permission.ASSESSMENT_VIEW);
                    case TEACHER ->
                        Set.of(Permission.ASSESSMENT_VIEW, Permission.ASSESSMENT_RECORD, Permission.ASSESSMENT_CONFIRM);
                    default -> Set.of();
                };
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    @Test
    @DisplayName("OBS-29: tuần chỉ mở từ ngày học cuối; trước đó chỉ xem, viết / lưu / gửi bị chặn")
    void weekOpensOnLastLessonDay() {
        CLOCK.set(Instant.parse("2026-10-29T03:00:00Z"));

        WeeklyCommentBoardResponse board = service.getBoard(teacherA, classA, WEEK);
        assertThat(board.week().source()).isEqualTo("CALENDAR");
        assertThat(board.week().openFrom()).isEqualTo(WEEK.plusDays(4));
        assertThat(board.week().open()).isFalse();
        assertThat(board.canEdit()).isFalse();
        assertThat(row(board, child1).status()).isEqualTo("NONE");
        expectCode(
                () -> service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child1, "Con học tốt.")),
                ApiCode.WEEKLY_COMMENT_WEEK_NOT_ENDED);
        expectCode(
                () -> service.writeDrafts(teacherA, new WeeklyCommentDraftRequest(classA, WEEK, child1, false, false)),
                ApiCode.WEEKLY_COMMENT_WEEK_NOT_ENDED);
        expectCode(
                () -> service.send(teacherA, new WeeklyCommentSendRequest(classA, WEEK, child1, "Con học tốt.")),
                ApiCode.WEEKLY_COMMENT_WEEK_NOT_ENDED);

        CLOCK.set(NOW);
        assertThat(service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child1, "Con học tốt."))
                        .canEdit())
                .isTrue();
    }

    @Test
    @DisplayName("OBS-29: tuần theo giáo án đã duyệt; nghỉ thứ 6 thì tuần là thứ 2–5 và mở từ thứ 5")
    void lessonPlanWeekShortenedByHoliday() {
        CLOCK.set(Instant.parse("2026-10-29T03:00:00Z"));
        Map<LocalDate, ApprovedDayLessonResponse> lessons = new java.util.TreeMap<>();
        for (int i = 0; i < 4; i++) {
            LocalDate d = WEEK.plusDays(i);
            lessons.put(d, new ApprovedDayLessonResponse(1L, WEEK, d, "Tuần 9", List.of()));
        }
        when(lessonQueryService.findApprovedDayLessons(eq(classA), any(), any()))
                .thenReturn(lessons);

        WeeklyCommentBoardResponse board = service.getBoard(teacherA, classA, WEEK);
        WeekOption week = board.week();
        assertThat(week.source()).isEqualTo("LESSON_PLAN");
        assertThat(week.label()).isEqualTo("Tuần 9");
        assertThat(week.from()).isEqualTo(WEEK);
        assertThat(week.to()).isEqualTo(WEEK.plusDays(3));
        assertThat(week.openFrom()).isEqualTo(WEEK.plusDays(3));
        assertThat(week.open()).isTrue();
        assertThat(board.canEdit()).isTrue();
        // Mở tuần: GV vào xem thì có sẵn nháp theo mẫu câu cho cả lớp, kể cả trẻ nghỉ cả tuần
        assertThat(row(board, child1).status()).isEqualTo("DRAFT");
        assertThat(row(board, child2).status()).isEqualTo("DRAFT");
        assertThat(row(board, child2).content()).contains("nghỉ học cả tuần");
        assertThat(row(board, child1).draftSource()).isEqualTo(WeeklyCommentSource.TEMPLATE);
        assertThat(row(board, child1).data().schoolDays()).isEqualTo(4);
        // Danh sách tuần: mới nhất trước, không có tuần tương lai; tuần chưa có giáo án theo lịch thứ 2–6
        assertThat(board.weeks().getFirst().weekStart()).isEqualTo(WEEK);
        assertThat(board.weeks().get(1).weekStart()).isEqualTo(WEEK.minusWeeks(1));
        assertThat(board.weeks().get(1).source()).isEqualTo("CALENDAR");
        assertThat(board.weeks().get(1).to()).isEqualTo(WEEK.minusDays(3));
        assertThat(board.weeks()).allMatch(w -> !w.weekStart().isAfter(WEEK));
    }

    @Test
    @DisplayName("OBS-29: việc sáng ngày học cuối tạo nháp còn thiếu, chạy lại không trùng, không đè bản cô đã sửa")
    void openWeekDraftJobIsIdempotent() {
        CLOCK.set(Instant.parse("2026-10-29T03:00:00Z"));
        service.createOpenWeekDrafts();
        assertThat(count(classA)).isZero();

        CLOCK.set(NOW);
        service.createOpenWeekDrafts();
        // Cả trẻ đi học lẫn trẻ nghỉ cả tuần đều có nháp
        assertThat(count(classA)).isEqualTo(2);
        service.saveDraft(teacherA, new WeeklyCommentSaveRequest(classA, WEEK, child1, "Cô tự viết."));
        service.createOpenWeekDrafts();
        assertThat(count(classA)).isEqualTo(2);
        assertThat(row(service.getBoard(teacherA, classA, WEEK), child1).content())
                .isEqualTo("Cô tự viết.");
    }

    @Test
    @DisplayName("OBS-29: nghỉ thứ 6 thì thông báo và nhận xét phụ huynh xem ghi tuần thứ 2–5 theo giáo án")
    void parentSeesLessonWeekDates() {
        Map<LocalDate, ApprovedDayLessonResponse> lessons = new java.util.TreeMap<>();
        for (int i = 0; i < 4; i++) {
            LocalDate d = WEEK.plusDays(i);
            lessons.put(d, new ApprovedDayLessonResponse(1L, WEEK, d, "Tuần 9", List.of()));
        }
        when(lessonQueryService.findApprovedDayLessons(eq(classA), any(), any()))
                .thenReturn(lessons);

        service.send(teacherA, new WeeklyCommentSendRequest(classA, WEEK, child1, "Con học tốt."));

        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE type = 'WEEKLY_COMMENT_SENT' AND user_id = ?",
                        String.class,
                        parent1.userId()))
                .contains("26/10 – 29/10/2026");
        assertThat(service.listForParent(parent1, child1)).singleElement().satisfies(c -> {
            assertThat(c.weekFrom()).isEqualTo(WEEK);
            assertThat(c.weekEnd()).isEqualTo(WEEK.plusDays(3));
            assertThat(c.weekLabel()).isEqualTo("Tuần 9");
        });
    }

    private int count(long classroomId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM weekly_comments WHERE classroom_id = ? AND week_start = ?",
                Integer.class,
                classroomId,
                WEEK);
    }

    private void campusRole(CurrentUser user, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
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

    private void attendance(long childId, long classroomId, LocalDate date) {
        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, recorded_by, recorded_at) "
                        + "SELECT ?, id, campus_id, ?, 'PRESENT', ?, now() FROM classrooms WHERE id = ?",
                childId,
                date,
                principal.userId(),
                classroomId);
    }

    private long child(String name) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                + "'WC' || substr(md5(random()::text), 1, 12), '" + name
                + "', '2023-01-01', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
