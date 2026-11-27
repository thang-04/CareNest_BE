package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.observation.ParentAssessmentDtos.ParentDailyAssessment;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentEvaluationsResponse;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentRewardsResponse;
import com.carenest.dto.observation.SemesterAssessmentDtos;
import com.carenest.entity.observation.BehaviourTicket;
import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.entity.observation.DailyObservation;
import com.carenest.entity.observation.RewardList;
import com.carenest.entity.observation.RewardListItem;
import com.carenest.entity.observation.SemesterAssessment;
import com.carenest.entity.observation.ThemeEvaluation;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.BehaviourTicketRepository;
import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.repository.observation.RewardListItemRepository;
import com.carenest.repository.observation.RewardListRepository;
import com.carenest.repository.observation.SemesterAssessmentRepository;
import com.carenest.repository.observation.ThemeEvaluationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ParentAssessmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.Participation;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.TicketType;
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
import tools.jackson.databind.ObjectMapper;

/** API phụ huynh xem đánh giá, khen thưởng của con (A5–A7). Hôm nay = thứ 4, 21/10/2026. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ParentAssessmentIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    static final class FixedClock extends Clock {
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
            return NOW;
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        FixedClock fixedClock() {
            return new FixedClock();
        }
    }

    @Autowired
    private ParentAssessmentService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DailyObservationRepository observations;

    @Autowired
    private ThemeEvaluationRepository themes;

    @Autowired
    private SemesterAssessmentRepository semesters;

    @Autowired
    private ChildEvaluationRepository evaluations;

    @Autowired
    private BehaviourTicketRepository tickets;

    @Autowired
    private RewardListRepository rewardLists;

    @Autowired
    private RewardListItemRepository rewardItems;

    private CurrentUser parent;
    private CurrentUser otherParent;
    private CurrentUser teacher;
    private long campus;
    private long year;
    private long closedYear;
    private long classA;
    private long classB;
    private long oldClass;
    private long child;
    private long classmate;

    @BeforeEach
    void setUp() {
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        closedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, year);
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, closedYear);
        classA = classroom(year, "Lớp A", ag3);
        classB = classroom(year, "Lớp B", ag3);
        oldClass = classroom(closedYear, "Lớp cũ", ag3);
        child = child("An");
        classmate = child("Bình");
        enroll(child, oldClass, "2025-09-01", "2026-05-31");
        enroll(child, classA, "2026-09-01", "2026-10-19");
        enroll(child, classB, "2026-10-20", null);
        enroll(classmate, classA, "2026-09-01", null);
        parent = user("parent", CareNestRole.PARENT);
        otherParent = user("parent2", CareNestRole.PARENT);
        teacher = user("teacher", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Mẹ (giả)', 'MOTHER', '0900000009', '000000000009', now())",
                parent.userId(),
                child);
    }

    @Test
    @DisplayName("Phụ huynh khác hoặc nhân viên ⇒ 4108 ở cả ba API")
    void onlyLinkedParentReads() {
        for (CurrentUser stranger : List.of(otherParent, teacher)) {
            expectCode(
                    () -> service.getDailyAssessments(stranger, child, TODAY, TODAY), ApiCode.PARENT_CHILD_FORBIDDEN);
            expectCode(() -> service.getEvaluations(stranger, child, null), ApiCode.PARENT_CHILD_FORBIDDEN);
            expectCode(() -> service.getRewards(stranger, child, TODAY, TODAY), ApiCode.PARENT_CHILD_FORBIDDEN);
        }
    }

    @Test
    @DisplayName("Khoảng quá 62 ngày hoặc vượt năm học ⇒ 4109")
    void rangeLimits() {
        expectCode(
                () -> service.getDailyAssessments(parent, child, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 2)),
                ApiCode.PARENT_RANGE_INVALID);
        expectCode(
                () -> service.getRewards(parent, child, LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 5)),
                ApiCode.PARENT_RANGE_INVALID);
        expectCode(() -> service.getRewards(parent, child, TODAY, TODAY.minusDays(1)), ApiCode.PARENT_RANGE_INVALID);
        assertThat(service.getDailyAssessments(parent, child, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 1)))
                .isNotNull();
    }

    @Test
    @DisplayName("Đánh giá ngày: trẻ chuyển lớp mang lớp từng ngày; vắng, chưa ghi; sửa trong lúc mở lại thì ẩn")
    void dailyAssessmentsPerDay() {
        LocalDate d15 = LocalDate.of(2026, 10, 15);
        LocalDate d16 = LocalDate.of(2026, 10, 16);
        LocalDate d19 = LocalDate.of(2026, 10, 19);
        LocalDate d20 = LocalDate.of(2026, 10, 20);
        attendance(classA, d15, "PRESENT");
        attendance(classA, d16, "PRESENT");
        attendance(classA, d19, "PRESENT");
        attendance(classB, d20, "UNEXCUSED");
        attendance(classB, TODAY, "PRESENT");
        DailyObservation saved = observation(classA, d19, Instant.parse("2026-10-19T09:00:00Z"));
        saved.changeComment("Bé ngoan");
        // d16: lớp đang được mở lại và cô vừa ghi sau lúc mở ⇒ chưa chốt
        jdbc.update(
                "INSERT INTO assessment_day_unlocks (classroom_id, campus_id, date, valid_until, reason, unlocked_by,"
                        + " unlocked_at) VALUES (?, ?, ?, '2026-10-21T10:00:00Z', 'Sửa', ?, '2026-10-21T02:00:00Z')",
                classA,
                campus,
                d16,
                teacher.userId());
        DailyObservation editing = observation(classA, d16, Instant.parse("2026-10-21T02:30:00Z"));
        editing.changeComment("ĐANG SỬA");
        entityManager.flush();
        entityManager.clear();

        List<ParentDailyAssessment> days = service.getDailyAssessments(parent, child, d15, TODAY);

        assertThat(days)
                .extracting(
                        ParentDailyAssessment::date, ParentDailyAssessment::status, ParentDailyAssessment::className)
                .containsExactly(
                        tuple(d15, "NOT_RECORDED", "Lớp A"),
                        tuple(d16, "NOT_RECORDED", "Lớp A"),
                        tuple(d19, "ASSESSED", "Lớp A"),
                        tuple(d20, "ABSENT", "Lớp B"),
                        tuple(TODAY, "NOT_RECORDED", "Lớp B"));
        ParentDailyAssessment assessed = days.get(2);
        assertThat(assessed.comment()).isEqualTo("Bé ngoan");
        assertThat(assessed.flag()).isTrue();
        assertThat(assessed.nap()).isEqualTo(NapLevel.NGU_TOT);
        assertThat(assessed.emotion()).isEqualTo(Emotion.HAPPY);
        assertThat(assessed.participation()).isEqualTo(Participation.TICH_CUC);
        assertThat(assessed.teacherName()).isEqualTo("teacher");
        assertThat(assessed.savedAt()).isEqualTo(Instant.parse("2026-10-19T09:00:00Z"));
        assertThat(mapper.writeValueAsString(days)).doesNotContain("ĐANG SỬA");
    }

    @Test
    @DisplayName("Đánh giá đã chốt theo kỳ: chỉ phần của trẻ, không tháng, không nháp / AI chưa xác nhận")
    void evaluationsOnlyConfirmedAndOnlyOwnChild() {
        ThemeEvaluation confirmedTheme = ThemeEvaluation.open(
                classA, campus, year, 11L, "Bản thân", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 2), NOW);
        confirmedTheme.confirm("[]", "Ghi chú cả lớp", teacher.userId(), NOW);
        confirmedTheme.setPresentationSnapshot("""
                {"themeId":11,"goals":[{"goalId":1,"goalNo":1,"text":"MT một"},{"goalId":2,"goalNo":2,"text":"MT hai"}],
                 "rows":[{"childId":%d,"fullName":"An","cells":[{"goalId":1,"met":true},{"goalId":2,"met":false}]},
                         {"childId":%d,"fullName":"Bình","cells":[{"goalId":1,"met":false},{"goalId":2,"met":false}]}]}
                """.formatted(child, classmate));
        themes.save(confirmedTheme);
        themes.save(ThemeEvaluation.open(
                classB, campus, year, 12L, "Gia đình", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 30), NOW));

        SemesterAssessment semester =
                SemesterAssessment.open(classA, campus, year, 1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        semester.confirm(
                mapper.writeValueAsString(new SemesterAssessmentDtos.Response(
                        classA,
                        "Lớp A",
                        year,
                        "2026-2027",
                        1,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 12, 31),
                        "CONFIRMED",
                        null,
                        List.of(),
                        List.of(
                                new SemesterAssessmentDtos.Child(
                                        child,
                                        "An",
                                        List.of(new SemesterAssessmentDtos.Domain(
                                                1L,
                                                "Thể chất",
                                                List.of(new SemesterAssessmentDtos.Goal(
                                                        1L, (short) 1, "Đi thăng bằng", true, 2, 2)),
                                                1,
                                                0,
                                                0,
                                                true)),
                                        null),
                                new SemesterAssessmentDtos.Child(classmate, "Bình", List.of(), null)),
                        false,
                        false,
                        "CONFIRMED",
                        NOW,
                        "teacher",
                        null)),
                teacher.userId(),
                NOW);
        semesters.save(semester);

        confirmedEvaluation(
                classA, EvaluationPeriodType.SEMESTER, "2026-09-01", "2026-12-31", "Nhận xét học kỳ I", null);
        confirmedEvaluation(classA, EvaluationPeriodType.MONTH, "2026-09-01", "2026-09-30", "Nhận xét THÁNG", null);
        ChildEvaluation aiDraft = ChildEvaluation.draft(
                child,
                classB,
                campus,
                year,
                EvaluationPeriodType.SEMESTER,
                LocalDate.of(2027, 1, 4),
                LocalDate.of(2027, 5, 31),
                "BAN NHAP AI",
                NOW);
        evaluations.save(aiDraft);
        ChildEvaluation yearEnd = confirmedEvaluation(
                classB, EvaluationPeriodType.YEAR, "2026-09-01", "2027-05-31", "Nhận xét cuối năm", """
                [{"g":1,"m":true,"x":false,"n":1,"t":"Chạy nhảy","d":5,"dn":"Thể chất","s":null},
                 {"g":2,"m":false,"x":true,"n":2,"t":"Vẽ tranh","d":6,"dn":"Thẩm mỹ","s":null}]""");
        yearEnd.saveSheet(yearEnd.getGoalResults(), null, null, null, "Gia đình cho bé tập thể dục");
        evaluations.save(yearEnd);
        entityManager.flush();
        entityManager.clear();

        ParentEvaluationsResponse result = service.getEvaluations(parent, child, year);

        assertThat(result.themes()).singleElement().satisfies(t -> {
            assertThat(t.themeName()).isEqualTo("Bản thân");
            assertThat(t.className()).isEqualTo("Lớp A");
            assertThat(t.met()).isEqualTo(1);
            assertThat(t.notMet()).isEqualTo(1);
            assertThat(t.goals())
                    .extracting(g -> g.text(), g -> g.met())
                    .containsExactly(tuple("MT một", true), tuple("MT hai", false));
        });
        assertThat(result.semesters()).singleElement().satisfies(s -> {
            assertThat(s.semester()).isEqualTo(1);
            assertThat(s.domains())
                    .singleElement()
                    .satisfies(d -> assertThat(d.goals()).hasSize(1));
            // Cần chốt 24/11/2026: bỏ nhận xét học kỳ cá nhân ⇒ không trả cho phụ huynh dù có dữ liệu cũ
            assertThat(s.comment()).isNull();
        });
        assertThat(result.yearEnd().className()).isEqualTo("Lớp B");
        assertThat(result.yearEnd().met()).isEqualTo(1);
        assertThat(result.yearEnd().notMet()).isEqualTo(1);
        assertThat(result.yearEnd().noteForParent()).isEqualTo("Gia đình cho bé tập thể dục");
        assertThat(result.yearEnd().comment()).isEqualTo("Nhận xét cuối năm");
        String json = mapper.writeValueAsString(result);
        assertThat(json).doesNotContain("THÁNG", "BAN NHAP AI", "Ghi chú cả lớp", "Bình", "Gia đình\"");
        // Mặc định năm học của lớp gần nhất
        assertThat(service.getEvaluations(parent, child, null).schoolYearId()).isEqualTo(year);
    }

    @Test
    @DisplayName("Năm học đã đóng xem được; năm trẻ không học ⇒ rỗng")
    void closedYearReadableAndUnknownYearEmpty() {
        ThemeEvaluation theme = ThemeEvaluation.open(
                oldClass,
                campus,
                closedYear,
                21L,
                "Thế giới",
                LocalDate.of(2025, 10, 1),
                LocalDate.of(2025, 10, 30),
                NOW);
        theme.confirm("[]", null, teacher.userId(), NOW);
        theme.setPresentationSnapshot("""
                {"themeId":21,"goals":[{"goalId":1,"goalNo":1,"text":"MT cũ"}],
                 "rows":[{"childId":%d,"cells":[{"goalId":1,"met":true}]}]}""".formatted(child));
        themes.save(theme);
        entityManager.flush();
        entityManager.clear();

        ParentEvaluationsResponse old = service.getEvaluations(parent, child, closedYear);
        assertThat(old.themes()).singleElement().satisfies(t -> {
            assertThat(t.className()).isEqualTo("Lớp cũ");
            assertThat(t.met()).isEqualTo(1);
        });
        ParentEvaluationsResponse none = service.getEvaluations(parent, child, 999_999L);
        assertThat(none.themes()).isEmpty();
        assertThat(none.semesters()).isEmpty();
        assertThat(none.yearEnd()).isNull();
    }

    @Test
    @DisplayName(
            "Khen thưởng: cờ, phiếu bé ngoan (người phát, ngày phát), danh hiệu đã duyệt kèm lời cô; không danh sách nháp")
    void rewards() {
        LocalDate d19 = LocalDate.of(2026, 10, 19);
        DailyObservation flagged = observation(classA, d19, Instant.parse("2026-10-19T09:00:00Z"));
        flagged.touch(teacher.userId(), Instant.parse("2026-10-19T09:00:00Z"));
        tickets.save(BehaviourTicket.issue(
                child,
                classA,
                campus,
                year,
                TicketType.WEEKLY,
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 16),
                5,
                4,
                null,
                teacher.userId(),
                Instant.parse("2026-10-16T09:00:00Z")));
        tickets.save(BehaviourTicket.issue(
                child,
                classB,
                campus,
                year,
                TicketType.MONTHLY,
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 30),
                8,
                6,
                3,
                teacher.userId(),
                Instant.parse("2026-10-20T09:00:00Z")));
        RewardList approved = RewardList.draft(classB, campus, year, teacher.userId());
        approved.submit(NOW);
        approved.review(true, teacher.userId(), NOW, null);
        approved.decide(true, teacher.userId(), NOW, null);
        rewardLists.save(approved);
        rewardItems.save(RewardListItem.of(approved.getId(), child, List.of("Bé ngoan", "Bé chăm"), "Cô khen bé"));
        rewardItems.save(RewardListItem.of(approved.getId(), classmate, List.of("Bé khỏe"), "Bạn khác"));
        RewardList draftList = RewardList.draft(classA, campus, year, teacher.userId());
        rewardLists.save(draftList);
        rewardItems.save(RewardListItem.of(draftList.getId(), child, List.of("NHÁP"), "Nháp"));
        entityManager.flush();
        entityManager.clear();

        // Cờ chỉ hiện ở ngày trẻ có mặt, như phiếu đánh giá ngày
        attendance(classA, d19, "PRESENT");
        ParentRewardsResponse rewards = service.getRewards(parent, child, LocalDate.of(2026, 10, 12), TODAY);

        assertThat(rewards.flags())
                .extracting(f -> f.date(), f -> f.className(), f -> f.flag())
                .containsExactly(tuple(d19, "Lớp A", true));
        assertThat(rewards.tickets())
                .extracting(t -> t.type(), t -> t.className(), t -> t.issuedByName())
                .containsExactly(
                        tuple(TicketType.WEEKLY, "Lớp A", "teacher"), tuple(TicketType.MONTHLY, "Lớp B", "teacher"));
        assertThat(rewards.tickets().getFirst().issuedAt()).isEqualTo(Instant.parse("2026-10-16T09:00:00Z"));
        assertThat(rewards.yearEndTitles()).singleElement().satisfies(t -> {
            assertThat(t.titles()).containsExactly("Bé ngoan", "Bé chăm");
            assertThat(t.teacherNote()).isEqualTo("Cô khen bé");
            assertThat(t.className()).isEqualTo("Lớp B");
        });
        assertThat(mapper.writeValueAsString(rewards)).doesNotContain("NHÁP", "Bạn khác");
    }

    private ChildEvaluation confirmedEvaluation(
            long classId, EvaluationPeriodType type, String from, String to, String content, String goalResults) {
        ChildEvaluation e = ChildEvaluation.draft(
                child, classId, campus, year, type, LocalDate.parse(from), LocalDate.parse(to), "nháp", NOW);
        e.confirm(content, teacher.userId(), NOW);
        if (goalResults != null) {
            e.freezeGoals(goalResults);
        }
        return evaluations.save(e);
    }

    private DailyObservation observation(long classId, LocalDate date, Instant at) {
        DailyObservation o = DailyObservation.start(child, classId, campus, date, teacher.userId(), at);
        o.recordDay(
                false,
                List.of(),
                "",
                "{\"LUNCH\":\"AN_HET\"}",
                NapLevel.NGU_TOT,
                Emotion.HAPPY,
                Participation.TICH_CUC,
                List.of(),
                "",
                true);
        return observations.save(o);
    }

    private void attendance(long classId, LocalDate date, String status) {
        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, meals, recorded_by,"
                        + " recorded_at) VALUES (?, ?, ?, ?, ?, '', ?, now())",
                child,
                classId,
                campus,
                date,
                status,
                teacher.userId());
    }

    private long child(String name) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES"
                + " ('IT' || substr(md5(random()::text), 1, 12), '" + name
                + "', '2022-01-01', 'MALE', 'ACTIVE', '2025-09-01') RETURNING id");
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions = role == CareNestRole.TEACHER
                ? Set.of(Permission.ATTENDANCE_VIEW)
                : Set.of(Permission.CHILD_PROFILE_VIEW);
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private long classroom(long yearId, String name, long ageGroupId) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + yearId + ", '" + name + "', " + ageGroupId + ") RETURNING id");
    }

    private void enroll(long childId, long classroomId, String from, String to) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, ?::date, ?::date)",
                childId,
                classroomId,
                from,
                to);
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
}
