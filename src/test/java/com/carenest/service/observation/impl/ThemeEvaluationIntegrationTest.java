package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.observation.ThemeEvaluationDtos.CellInput;
import com.carenest.dto.observation.ThemeEvaluationDtos.ChildRow;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationRequest;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationResponse;
import com.carenest.dto.observation.YearEndSheetDtos.SheetGoal;
import com.carenest.dto.observation.YearEndSheetDtos.SheetGoalInput;
import com.carenest.dto.observation.YearEndSheetDtos.YearEndSheetRequest;
import com.carenest.dto.observation.YearEndSheetDtos.YearEndSheetResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ChildEvaluationService;
import com.carenest.service.observation.ThemeEvaluationService;
import com.carenest.service.observation.YearEndSheetService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.Permission;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
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
 * Đánh giá cuối chủ đề (OBS-27): MT của chủ đề lấy từ kế hoạch năm học đã duyệt, mỗi ô đạt khi có ít nhất một hoạt động
 * trong chủ đề được chấm Đạt; nối MT qua dòng gốc (giáo án có thể chấm theo MT của bản kế hoạch cũ). Hôm nay = 21/10/2026.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ThemeEvaluationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

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
            return Instant.parse("2026-10-21T02:00:00Z");
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
    private ThemeEvaluationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private YearEndSheetService sheetService;

    @Autowired
    private ChildEvaluationService evaluationService;

    @Autowired
    private com.carenest.service.observation.SemesterAssessmentService semesters;

    private CurrentUser teacher;
    private CurrentUser otherTeacher;
    private CurrentUser principal;
    private long classA;
    private long classB;
    private long nurseryClass;
    private long kid1;
    private long kid2;
    private long oldGoal1;
    private long goal1;
    private long goal2;
    private long doc;

    @BeforeEach
    void setUp() {
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-T', 'T', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long ag2 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-2'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'MG A', " + ag3 + ") RETURNING id");
        classB = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'MG B', " + ag3 + ") RETURNING id");
        nurseryClass = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campus + ", " + year + ", 'NT', " + ag2 + ") RETURNING id");
        principal = user("principal-t", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        teacher = teacherOf("teacher-t", classA);
        otherTeacher = teacherOf("teacher-b", classB);
        teacherOf("teacher-nt", nurseryClass);

        // Kế hoạch năm học bản 1 (MT cũ) và bản 2 đã duyệt: MT 1 của bản 2 có dòng gốc là MT 1 bản 1
        jdbc.update(
                "INSERT INTO edu_domains (id, name, sort_order) OVERRIDING SYSTEM VALUE VALUES (9100, 'TEST lĩnh vực chủ đề', 99)");
        long owner = teacher.userId();
        jdbc.update(
                "INSERT INTO edu_team_plans (legacy_team_without_source, id, code, school_year_id, age_group_id, title, status, owner_id, version_no)"
                        + " OVERRIDING SYSTEM VALUE VALUES (true, 9001, 'KH-T1', ?, ?, 'KH 3-4 tuổi', 'APPROVED', ?, 1)",
                year,
                ag3,
                owner);
        jdbc.update(
                "INSERT INTO edu_team_plans (legacy_team_without_source, id, code, school_year_id, age_group_id, title, status, owner_id, version_no,"
                        + " source_plan_id)"
                        + " OVERRIDING SYSTEM VALUE VALUES (true, 9002, 'KH-T2', ?, ?, 'KH 3-4 tuổi', 'APPROVED', ?, 2, 9001)",
                year,
                ag3,
                owner);
        oldGoal1 = insertId("INSERT INTO edu_plan_goals (team_plan_id, domain_id, sort_order, text, goal_no)"
                + " OVERRIDING SYSTEM VALUE VALUES (9001, 9100, 1, 'Trẻ chạy được 15m', 1) RETURNING id");
        goal1 = insertId("INSERT INTO edu_plan_goals (team_plan_id, domain_id, sort_order, text, goal_no, lineage_id)"
                + " VALUES (9002, 9100, 1, 'Trẻ chạy được 15m', 1, " + oldGoal1 + ") RETURNING id");
        goal2 = insertId("INSERT INTO edu_plan_goals (team_plan_id, domain_id, sort_order, text, goal_no)"
                + " VALUES (9002, 9100, 2, 'Trẻ biết tên trường', 3) RETURNING id");
        long goal3 = insertId("INSERT INTO edu_plan_goals (team_plan_id, domain_id, sort_order, text, goal_no)"
                + " VALUES (9002, 9100, 3, 'MT chủ đề sau', 4) RETURNING id");
        jdbc.update(
                "INSERT INTO edu_themes (id, team_plan_id, sort_order, name, start_date, end_date)"
                        + " OVERRIDING SYSTEM VALUE VALUES (9201, 9002, 1, 'Trường mầm non thân yêu', '2026-09-07', '2026-10-02')");
        jdbc.update("INSERT INTO edu_themes (id, team_plan_id, sort_order, name, start_date, end_date)"
                + " OVERRIDING SYSTEM VALUE VALUES (9202, 9002, 2, 'Gia đình', '2026-11-02', '2026-11-27')");
        tick(9301, goal1, 9201);
        tick(9302, goal2, 9201);
        tick(9303, goal3, 9202);
        doc = insertId("INSERT INTO edu_class_docs (doc_key, kind, classroom_id, team_plan_id, created_by, updated_by)"
                + " VALUES ('branch-t', 'branch', " + classA + ", 9001, " + owner + ", " + owner + ") RETURNING id");

        kid1 = child("An");
        kid2 = child("Bình");
        enroll(kid1, classA);
        enroll(kid2, classA);
        // An: MT1 đạt 1/2 hoạt động nên đạt, MT3 đạt; Bình: MT1 đạt, MT3 chưa chấm
        result(kid1, classA, "2026-09-08", "k1", String.valueOf(oldGoal1), "");
        result(kid1, classA, "2026-09-15", "k2", "", String.valueOf(oldGoal1));
        result(kid1, classA, "2026-09-21", "k3", String.valueOf(goal2), "");
        result(kid2, classA, "2026-09-10", "k1", String.valueOf(oldGoal1), "");
        // Kết quả ngoài thời gian chủ đề không tính
        result(kid2, classA, "2026-10-06", "k1", "", String.valueOf(oldGoal1));
    }

    @Test
    void matrixUsesHalfOfAssessedActivitiesWithinThemeAndClass() {
        ThemeEvaluationResponse view = service.get(teacher, classA, 9201L);
        assertThat(view.goals()).extracting(g -> g.goalNo()).containsExactly((short) 1, (short) 3);
        assertThat(view.goals().getFirst().goalId()).isEqualTo(oldGoal1);
        ChildRow an = row(view, kid1);
        assertThat(an.cells()).extracting(c -> c.met()).containsExactly(true, true);
        assertThat(an.rate()).isEqualTo(100);
        assertThat(an.passed()).isTrue();
        ChildRow binh = row(view, kid2);
        // kid2: MT1 đạt (1 hoạt động), MT3 chưa chấm trong khoảng chủ đề → Chưa đạt (rule any-met, nghỉ=chưa đạt)
        assertThat(binh.cells()).extracting(c -> c.met()).containsExactly(true, false);
        assertThat(binh.passed()).isFalse();
        assertThat(view.goals().getFirst().metChildren()).isEqualTo(2);
        assertThat(view.goals().getFirst().rate()).isEqualTo(100);
        assertThat(view.passedChildren()).isEqualTo(1);
        assertThat(view.notPassedChildren()).isOne();
        assertThat(view.canEdit()).isTrue();
        assertThat(view.canConfirm()).isTrue();
        assertThat(service.listThemes(teacher, classA))
                .extracting(t -> t.period())
                .containsExactly("ENDED", "UPCOMING");
    }

    @Test
    @DisplayName("OBS-27: 1 hoạt động đạt là Đạt; ngày vắng không tính; mục tiêu chưa chấm là Chưa đạt")
    void anyMetMakesGoalMetAndAbsentDaysNotCounted() {
        // k4 (09-15): chấm MT1 chưa đạt → any-met còn k1 (09-08) đạt từ setUp nên MT1 vẫn Đạt
        result(kid1, classA, "2026-09-15", "k4", "", String.valueOf(oldGoal1));
        assertThat(row(service.get(teacher, classA, 9201L), kid1)
                        .cells()
                        .getFirst()
                        .met())
                .isTrue();
        result(kid1, classA, "2026-09-15", "k5", String.valueOf(oldGoal1), "");
        assertThat(row(service.get(teacher, classA, 9201L), kid1)
                        .cells()
                        .getFirst()
                        .met())
                .isTrue();
        // Cùng hoạt động chấm lại: xoá hết cả đạt lẫn chưa đạt của k5 → vẫn còn k1 đạt
        jdbc.update(
                "UPDATE daily_activity_results SET goals_met = '', goals_not_met = ''"
                        + " WHERE child_id = ? AND date = '2026-09-15' AND activity_key = 'k5'",
                kid1);
        entityManager.clear();
        assertThat(row(service.get(teacher, classA, 9201L), kid1)
                        .cells()
                        .getFirst()
                        .met())
                .isTrue();
        result(kid2, classA, "2026-09-16", "blank", "", "");
        result(kid2, classA, "2026-09-17", "stale-absent", "", String.valueOf(oldGoal1));
        result(kid2, classA, "2026-09-17", "stale-absent-2", "", String.valueOf(oldGoal1));
        jdbc.update(
                "UPDATE attendance_records SET status = 'EXCUSED' WHERE child_id = ? AND date = '2026-09-17'", kid2);
        entityManager.clear();
        // kid2: k1 (09-10) đạt MT1 → MT1=true (any-met). MT3 không có hoạt động → Chưa đạt.
        assertThat(row(service.get(teacher, classA, 9201L), kid2).cells())
                .extracting(c -> c.met())
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("OBS-27: 1 hoạt động đạt là Đạt; mục tiêu cùng dòng gốc không đếm trùng")
    void anyMetMakesGoalMetAndDeduplicatesGoalLineagePerActivity() {
        // extra-pass (một dòng hai ID cùng dòng gốc) không xoá được k1 (09-08) đạt từ setUp
        result(kid1, classA, "2026-09-12", "extra-pass", oldGoal1 + "," + goal1, "");
        assertThat(row(service.get(teacher, classA, 9201L), kid1)
                        .cells()
                        .getFirst()
                        .met())
                .isTrue();
        // any-met: 2 lần chưa đạt không xoá được trạng thái Đạt (vẫn còn 1 hoạt động đạt)
        result(kid1, classA, "2026-09-16", "fail-1", "", String.valueOf(oldGoal1));
        result(kid1, classA, "2026-09-17", "fail-2", "", String.valueOf(oldGoal1));
        assertThat(row(service.get(teacher, classA, 9201L), kid1)
                        .cells()
                        .getFirst()
                        .met())
                .isTrue();
    }

    @Test
    @DisplayName("OBS-25: dữ liệu demo học kỳ là nháp đọc được, chạy lại không nhân bản")
    void seededSemesterBoardIsReadableDraft() throws java.io.IOException {
        jdbc.update(
                "UPDATE school_years SET semester1_start='2026-09-01', semester1_end='2027-01-15'"
                        + " WHERE id=(SELECT school_year_id FROM classrooms WHERE id=?)",
                classA);
        String script =
                java.nio.file.Files.readString(java.nio.file.Path.of("scripts/dev/seed-test-three-modules.sql"));
        int start = script.indexOf("INSERT INTO semester_assessments");
        int end = script.indexOf("-- ============ 4.", start);
        String semesterSeed =
                script.substring(start, end).replace("WHERE c.school_year_id = 1", "WHERE c.id = " + classA);
        jdbc.execute(semesterSeed);
        jdbc.execute(semesterSeed);
        var board = semesters.get(teacher, classA, 1);
        assertThat(board.status()).isEqualTo("DRAFT");
        assertThat(board.confirmedAt()).isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM semester_assessments WHERE classroom_id=? AND semester_no=1",
                        Integer.class,
                        classA))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("OBS-25: học kỳ lấy cấu hình, hai tầng 50%, thiếu không xác nhận, chốt cả lớp")
    void semesterBoardUsesConfiguredPeriodAndFreezesResults() {
        jdbc.update(
                "UPDATE school_years SET semester1_start='2026-09-01', semester1_end='2026-10-02',"
                        + " semester2_start='2026-10-03', semester2_end='2027-05-31' WHERE id=(SELECT school_year_id FROM classrooms WHERE id=?)",
                classA);
        var initial = semesters.get(teacher, classA, 1);
        assertThat(initial.startDate()).isEqualTo(java.time.LocalDate.parse("2026-09-01"));
        assertThat(initial.endDate()).isEqualTo(java.time.LocalDate.parse("2026-10-02"));
        // Bản in học kỳ có tên người ký như cuối chủ đề: GV chủ nhiệm lớp
        assertThat(initial.signers().teachers()).containsExactly("teacher-t");
        assertThat(initial.domains()).hasSize(1);
        assertThat(initial.rows().stream()
                        .filter(c -> c.childId().equals(kid1))
                        .findFirst()
                        .orElseThrow()
                        .domains()
                        .getFirst()
                        .met())
                .isTrue();
        // OBS-25 (Cần chốt 10/11/2026): MT học kỳ lấy từ kết quả cuối chủ đề đã xong; chủ đề đã có kết quả cho mọi MT
        // (không có lần Đạt là Chưa đạt) nên bảng đủ dữ liệu, xác nhận được ngay
        assertThat(initial.canConfirm()).isTrue();
        var req = new com.carenest.dto.observation.SemesterAssessmentDtos.Request(classA, 1, "Lưu ý lớp");
        expectCode(() -> semesters.save(principal, req), ApiCode.EVALUATION_EDIT_FORBIDDEN);
        expectCode(() -> semesters.get(otherTeacher, classA, 1), ApiCode.EVALUATION_VIEW_FORBIDDEN);
        // Phân quyền động: HT thu hồi quyền Ghi / Xác nhận đánh giá ⇒ GV gọi thẳng API cũng bị chặn
        CurrentUser noRecord = new CurrentUser(
                teacher.userId(),
                teacher.keycloakUserId(),
                teacher.username(),
                teacher.fullName(),
                null,
                teacher.roles(),
                Set.of(Permission.ASSESSMENT_VIEW, Permission.ASSESSMENT_CONFIRM));
        CurrentUser noConfirm = new CurrentUser(
                teacher.userId(),
                teacher.keycloakUserId(),
                teacher.username(),
                teacher.fullName(),
                null,
                teacher.roles(),
                Set.of(Permission.ASSESSMENT_VIEW, Permission.ASSESSMENT_RECORD));
        expectCode(() -> semesters.save(noRecord, req), ApiCode.EVALUATION_EDIT_FORBIDDEN);
        expectCode(() -> semesters.confirm(noConfirm, req), ApiCode.EVALUATION_EDIT_FORBIDDEN);
        result(kid2, classA, "2026-09-21", "second-goal", "", String.valueOf(goal2));
        var saved = semesters.save(teacher, req);
        assertThat(saved.status()).isEqualTo("DRAFT");
        assertThat(saved.canConfirm()).isTrue();
        var binh = saved.rows().stream()
                .filter(c -> c.childId().equals(kid2))
                .findFirst()
                .orElseThrow()
                .domains()
                .getFirst();
        assertThat(binh.metCount()).isEqualTo(1);
        assertThat(binh.notMetCount()).isEqualTo(1);
        assertThat(binh.met()).isTrue();
        var confirmed = semesters.confirm(teacher, req);
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.canEdit()).isFalse();
        result(kid2, classA, "2026-09-22", "extra-fail", "", String.valueOf(oldGoal1));
        result(kid2, classA, "2026-09-23", "extra-fail-2", "", String.valueOf(oldGoal1));
        assertThat(semesters.get(principal, classA, 1).rows()).isEqualTo(confirmed.rows());
        expectCode(() -> semesters.save(teacher, req), ApiCode.EVALUATION_ALREADY_CONFIRMED);
        var second = semesters.get(teacher, classA, 2);
        assertThat(second.startDate()).isEqualTo(java.time.LocalDate.parse("2026-10-03"));
        assertThat(second.rows().stream()
                        .filter(c -> c.childId().equals(kid2))
                        .findFirst()
                        .orElseThrow()
                        .domains()
                        .getFirst()
                        .goals())
                .anySatisfy(g -> {
                    if (g.goalId().equals(oldGoal1)) assertThat(g.met()).isFalse();
                });
        expectCode(
                () -> semesters.confirm(
                        teacher, new com.carenest.dto.observation.SemesterAssessmentDtos.Request(classA, 2, "")),
                ApiCode.SEMESTER_ASSESSMENT_NOT_ENDED);
    }

    @Test
    @DisplayName("OBS-28: MT cuối năm đạt khi ít nhất 50% chủ đề có gắn MT đạt")
    void annualResultAggregatesOneResultPerTheme() {
        // Chủ đề 1: MT1 đã Đạt trong dữ liệu nền. Cho chủ đề 2 kết thúc, gắn cùng MT1 và chỉ chấm Chưa đạt.
        jdbc.update("UPDATE edu_themes SET start_date='2026-10-03', end_date='2026-10-10' WHERE id=9202");
        tick(9390, goal1, 9202);
        result(kid1, classA, "2026-10-05", "theme-2-fail", "", String.valueOf(oldGoal1));

        var half = service.childThemeResults(classA, kid1).get(oldGoal1);
        assertThat(half.metThemes()).isEqualTo(1);
        assertThat(half.totalThemes()).isEqualTo(2);
        assertThat(half.met()).isTrue();

        // Chủ đề 3 cũng Chưa đạt: 1/3 dưới 50% nên MT cuối năm Chưa đạt.
        jdbc.update("INSERT INTO edu_themes (id, team_plan_id, sort_order, name, start_date, end_date)"
                + " OVERRIDING SYSTEM VALUE VALUES (9203, 9002, 3, 'Bản thân', '2026-10-11', '2026-10-20')");
        tick(9391, goal1, 9203);
        result(kid1, classA, "2026-10-15", "theme-3-fail", "", String.valueOf(oldGoal1));

        var belowHalf = service.childThemeResults(classA, kid1).get(oldGoal1);
        assertThat(belowHalf.metThemes()).isEqualTo(1);
        assertThat(belowHalf.totalThemes()).isEqualTo(3);
        assertThat(belowHalf.met()).isFalse();

        // Chủ đề đang dạy (chưa tới ngày kết thúc) không tính, dù đã có lần Đạt
        jdbc.update("INSERT INTO edu_themes (id, team_plan_id, sort_order, name, start_date, end_date)"
                + " OVERRIDING SYSTEM VALUE VALUES (9204, 9002, 4, 'Nghề nghiệp', '2026-10-21', '2026-10-31')");
        tick(9392, goal1, 9204);
        result(kid1, classA, "2026-10-21", "theme-4-pass", String.valueOf(oldGoal1), "");

        var ongoing = service.childThemeResults(classA, kid1).get(oldGoal1);
        assertThat(ongoing.metThemes()).isEqualTo(1);
        assertThat(ongoing.totalThemes()).isEqualTo(3);
    }

    @Test
    @DisplayName("OBS-25: MT học kỳ lấy từ kết quả cuối các chủ đề kết thúc trong kỳ, >=50% chủ đề Đạt")
    void semesterGoalUsesThemeResultsEndingInSemester() {
        jdbc.update(
                "UPDATE school_years SET semester1_start='2026-09-01', semester1_end='2026-10-15',"
                        + " semester2_start='2026-10-16', semester2_end='2027-05-31' WHERE id=(SELECT school_year_id FROM classrooms WHERE id=?)",
                classA);
        // Chủ đề 1 (kết thúc 02/10): MT1 Đạt. Chủ đề 2 (kết thúc 10/10, cùng học kỳ I): MT1 chỉ Chưa đạt
        jdbc.update("UPDATE edu_themes SET start_date='2026-10-03', end_date='2026-10-10' WHERE id=9202");
        tick(9390, goal1, 9202);
        result(kid1, classA, "2026-10-05", "theme-2-fail", "", String.valueOf(oldGoal1));

        var goal = semesters.get(teacher, classA, 1).rows().stream()
                .filter(c -> c.childId().equals(kid1))
                .findFirst()
                .orElseThrow()
                .domains()
                .getFirst()
                .goals()
                .stream()
                .filter(g -> g.goalId().equals(oldGoal1))
                .findFirst()
                .orElseThrow();
        assertThat(goal.metActivities()).isEqualTo(1);
        assertThat(goal.assessedActivities()).isEqualTo(2);
        assertThat(goal.met()).isTrue();
    }

    @Test
    void teacherOverridesCellsThenConfirmsAndTheTableIsFrozen() {
        var override = new ThemeEvaluationRequest(
                classA, 9201L, " Cô chú ý rèn trẻ chạy ", List.of(new CellInput(kid1, oldGoal1, false)));
        ThemeEvaluationResponse saved = service.save(teacher, override);
        ChildRow an = row(saved, kid1);
        assertThat(an.cells().getFirst().met()).isFalse();
        assertThat(an.cells().getFirst().manual()).isTrue();
        assertThat(an.passed()).isFalse();
        assertThat(saved.note()).isEqualTo("Cô chú ý rèn trẻ chạy");

        ThemeEvaluationResponse confirmed =
                service.confirm(teacher, new ThemeEvaluationRequest(classA, 9201L, saved.note(), List.of()));
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.canEdit()).isFalse();
        // Đánh giá ngày đổi sau khi xác nhận không làm đổi bảng đã xác nhận
        result(kid2, classA, "2026-09-25", "k9", "", String.valueOf(oldGoal1));
        result(kid2, classA, "2026-09-26", "k10", "", String.valueOf(oldGoal1));
        assertThat(row(service.get(teacher, classA, 9201L), kid2)
                        .cells()
                        .getFirst()
                        .met())
                .isTrue();
        expectCode(() -> service.save(teacher, override), ApiCode.EVALUATION_ALREADY_CONFIRMED);
    }

    @Test
    void yearEndSheetAggregatesThemesThenTeacherEditsAndConfirmFreezesIt() {
        long yearEval = insertId("INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id,"
                + " period_type, period_start, period_end, status, draft_content, program_completed, created_at,"
                + " updated_at) SELECT " + kid1 + ", c.id, c.campus_id, c.school_year_id, 'YEAR', '2026-09-01',"
                + " '2027-05-31', 'AWAITING_REVIEW', 'Nháp', TRUE, now(), now() FROM classrooms c WHERE c.id = "
                + classA
                + " RETURNING id");
        String campaignSql = "INSERT INTO health_check_campaigns (kind, campus_id, name, start_date, end_date,"
                + " created_by) SELECT 'MEASUREMENT', c.campus_id, 'Cân đo', '2026-09-01', '2026-10-30', "
                + teacher.userId() + " FROM classrooms c WHERE c.id = " + classA + " RETURNING id";
        long campaign = insertId(campaignSql);
        // Mỗi điểm trường chỉ một đợt đang mở: đóng đợt 1 rồi mở đợt 2
        jdbc.update(
                "UPDATE health_check_campaigns SET closed_at = now(), closed_by = ? WHERE id = ?",
                teacher.userId(),
                campaign);
        long campaign2 = insertId(campaignSql);
        jdbc.update(
                "INSERT INTO health_measurements (child_id, campaign_id, classroom_id, date, height_cm, weight_kg,"
                        + " measured_by, published_at) VALUES (?, ?, ?, '2026-09-20', 98.5, 15.2, ?, now()),"
                        + " (?, ?, ?, '2026-10-30', 99.0, 15.6, ?, now())",
                kid1,
                campaign,
                classA,
                teacher.userId(),
                kid1,
                campaign2,
                classA,
                teacher.userId());

        // OBS-06 (Cần chốt 10/11/2026): trước ngày kết thúc HK II phiếu chỉ xem, mọi thao tác sửa bị chặn
        assertThat(sheetService.get(teacher, yearEval).canEdit()).isFalse();
        expectCode(
                () -> sheetService.save(teacher, yearEval, new YearEndSheetRequest(List.of(), "Sớm", null, null, null)),
                ApiCode.YEAR_END_NOT_OPEN);
        expectCode(
                () -> evaluationService.saveContent(teacher, yearEval, "Nhận xét cuối năm viết sớm trước khi mở."),
                ApiCode.YEAR_END_NOT_OPEN);
        expectCode(
                () -> evaluationService.confirm(
                        teacher, yearEval, "Trẻ phát triển bình thường, tham gia tích cực các hoạt động."),
                ApiCode.YEAR_END_NOT_OPEN);
        endSemesterTwo();

        // MT lấy từ kế hoạch năm theo lĩnh vực; kết quả tổng hợp từng chủ đề có gắn MT; chủ đề chưa bắt đầu để trống
        YearEndSheetResponse sheet = sheetService.get(teacher, yearEval);
        assertThat(sheet.domains()).extracting(d -> d.name()).containsExactly("TEST lĩnh vực chủ đề");
        List<SheetGoal> goals = sheet.domains().getFirst().goals();
        assertThat(goals).extracting(SheetGoal::goalNo).containsExactly((short) 1, (short) 3, (short) 4);
        assertThat(goals).extracting(SheetGoal::met).containsExactly(true, true, null);
        assertThat(goals.getFirst().source()).contains("1/1 chủ đề đạt: Trường mầm non thân yêu");
        assertThat(sheet.metCount()).isEqualTo(2);
        assertThat(sheet.notMetCount()).isZero();
        assertThat(sheet.canEdit()).isTrue();
        // Lần cân đo gần nhất tới hôm nay (21/10): lần 30/10 chưa tới nên chưa lấy
        assertThat(sheet.measurement().weightKg()).isEqualByComparingTo("15.2");
        assertThat(sheet.teachers()).containsExactly("teacher-t");

        // Phiếu năm lấy kết quả cuối chủ đề, gồm cả ô giáo viên sửa tay khi bảng chủ đề chưa xác nhận
        themeService()
                .save(
                        teacher,
                        new ThemeEvaluationRequest(classA, 9201L, null, List.of(new CellInput(kid1, oldGoal1, false))));
        assertThat(sheetService
                        .get(teacher, yearEval)
                        .domains()
                        .getFirst()
                        .goals()
                        .getFirst()
                        .met())
                .isFalse();

        // GV sửa tay trên phiếu và ghi lưu ý; trùng kết quả tổng hợp thì không tính sửa tay
        YearEndSheetResponse saved = sheetService.save(
                teacher,
                yearEval,
                new YearEndSheetRequest(
                        List.of(new SheetGoalInput(goal2, false), new SheetGoalInput(oldGoal1, true)),
                        "  Rèn trẻ biết tên trường ",
                        null,
                        " ",
                        "Cha mẹ cùng trò chuyện với trẻ"));
        List<SheetGoal> edited = saved.domains().getFirst().goals();
        assertThat(edited).extracting(SheetGoal::met).containsExactly(true, false, null);
        assertThat(edited).extracting(SheetGoal::manual).containsExactly(true, true, false);
        assertThat(saved.notes().forChild()).isEqualTo("Rèn trẻ biết tên trường");
        assertThat(saved.notes().forSchool()).isNull();

        // Nhận xét dưới từng lĩnh vực: lưu, giữ khi request không gửi, xóa bằng chuỗi rỗng; lĩnh vực lạ bị từ chối
        YearEndSheetResponse commented = sheetService.save(
                teacher,
                yearEval,
                new YearEndSheetRequest(
                        List.of(),
                        "Rèn trẻ biết tên trường",
                        null,
                        null,
                        "Cha mẹ cùng trò chuyện với trẻ",
                        java.util.Map.of(9100L, "  Trẻ vận động tốt ")));
        assertThat(commented.domains().getFirst().comment()).isEqualTo("Trẻ vận động tốt");
        YearEndSheetResponse kept = sheetService.save(
                teacher,
                yearEval,
                new YearEndSheetRequest(
                        List.of(), "Rèn trẻ biết tên trường", null, null, "Cha mẹ cùng trò chuyện với trẻ"));
        assertThat(kept.domains().getFirst().comment()).isEqualTo("Trẻ vận động tốt");
        expectCode(
                () -> sheetService.save(
                        teacher,
                        yearEval,
                        new YearEndSheetRequest(List.of(), null, null, null, null, java.util.Map.of(424242L, "x"))),
                ApiCode.YEAR_SHEET_DOMAIN_INVALID);

        // Ngoài phạm vi / sai dữ liệu
        expectCode(() -> sheetService.get(otherTeacher, yearEval), ApiCode.EVALUATION_VIEW_FORBIDDEN);
        expectCode(() -> sheetService.get(principal, yearEval), ApiCode.EVALUATION_VIEW_FORBIDDEN);
        expectCode(
                () -> sheetService.save(
                        teacher,
                        yearEval,
                        new YearEndSheetRequest(List.of(new SheetGoalInput(424242L, true)), null, null, null, null)),
                ApiCode.YEAR_SHEET_GOAL_INVALID);
        expectCode(
                () -> sheetService.save(
                        teacher, yearEval, new YearEndSheetRequest(List.of(), "x".repeat(1001), null, null, null)),
                ApiCode.YEAR_SHEET_NOTE_TOO_LONG);
        long monthEval = insertId("INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id,"
                + " period_type, period_start, period_end, status, created_at, updated_at) SELECT " + kid1
                + ", c.id, c.campus_id, c.school_year_id, 'MONTH', '2026-09-01', '2026-09-30', 'AWAITING_REVIEW',"
                + " now(), now() FROM classrooms c WHERE c.id = " + classA + " RETURNING id");
        expectCode(() -> sheetService.get(teacher, monthEval), ApiCode.YEAR_SHEET_ONLY_YEAR_END);

        // Xác nhận đánh giá cuối năm: chốt cả phiếu; kết quả ngày đổi sau đó không đổi phiếu
        evaluationService.confirm(teacher, yearEval, "Trẻ phát triển bình thường, tham gia tích cực các hoạt động.");
        result(kid1, classA, "2026-10-20", "k8", "", String.valueOf(goal2));
        YearEndSheetResponse frozen = sheetService.get(principal, yearEval);
        assertThat(frozen.canEdit()).isFalse();
        assertThat(frozen.domains().getFirst().goals())
                .extracting(SheetGoal::met)
                .containsExactly(true, false, null);
        assertThat(frozen.notes().forParent()).isEqualTo("Cha mẹ cùng trò chuyện với trẻ");
        assertThat(frozen.domains().getFirst().comment()).isEqualTo("Trẻ vận động tốt");
        expectCode(
                () -> sheetService.save(teacher, yearEval, new YearEndSheetRequest(List.of(), null, null, null, null)),
                ApiCode.EVALUATION_ALREADY_CONFIRMED);

        // Đánh giá cuối năm xác nhận trước khi có phiếu (chưa chốt ô nào): vẫn hiện kết quả tổng hợp, chỉ xem
        long legacy = insertId("INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id,"
                + " period_type, period_start, period_end, status, final_content, confirmed_by, confirmed_at,"
                + " created_at, updated_at) SELECT " + kid2 + ", c.id, c.campus_id, c.school_year_id, 'YEAR',"
                + " '2026-09-01', '2027-05-31', 'CONFIRMED', 'Đã xác nhận', " + teacher.userId() + ", now(), now(),"
                + " now() FROM classrooms c WHERE c.id = " + classA + " RETURNING id");
        YearEndSheetResponse old = sheetService.get(principal, legacy);
        assertThat(old.canEdit()).isFalse();
        assertThat(old.domains().getFirst().goals().getFirst().met()).isTrue();
        // Kế hoạch năm có bản mới (đổi câu MT, thêm MT): phiếu đã xác nhận giữ nguyên danh sách và câu MT đã chốt
        jdbc.update(
                "INSERT INTO edu_team_plans (id, code, school_year_id, age_group_id, title, status, owner_id,"
                        + " version_no, source_plan_id, legacy_team_without_source) OVERRIDING SYSTEM VALUE SELECT 9004, 'KH-T4', school_year_id,"
                        + " age_group_id, title, 'APPROVED', owner_id, 4, 9002, legacy_team_without_source FROM edu_team_plans WHERE id = 9002");
        jdbc.update("INSERT INTO edu_plan_goals (team_plan_id, domain_id, sort_order, text, goal_no, lineage_id)"
                + " VALUES (9004, 9100, 1, 'Câu MT đã sửa', 1, " + oldGoal1 + ")");
        jdbc.update("INSERT INTO edu_plan_goals (team_plan_id, domain_id, sort_order, text, goal_no)"
                + " VALUES (9004, 9100, 2, 'MT mới thêm', 9)");
        List<SheetGoal> afterNewPlan =
                sheetService.get(principal, yearEval).domains().getFirst().goals();
        assertThat(afterNewPlan).extracting(SheetGoal::text).doesNotContain("Câu MT đã sửa", "MT mới thêm");
        assertThat(afterNewPlan).hasSize(3);
        assertThat(afterNewPlan).extracting(SheetGoal::met).containsExactly(true, false, null);
    }

    @Test
    void yearEndSheetTakesResultsFromPreviousClassAndTeamLeaderReadsConfirmedSheets() {
        // Bé Cường học MG B (cùng nhóm tuổi) tới 30/9 rồi chuyển sang MG A
        long kid3 = child("Cường");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2026-09-01',"
                        + " '2026-09-30'), (?, ?, '2026-10-01', NULL)",
                kid3,
                classB,
                kid3,
                classA);
        result(kid3, classB, "2026-09-20", "k1", String.valueOf(goal2), "");
        long yearEval = insertId("INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id,"
                + " period_type, period_start, period_end, status, draft_content, program_completed, created_at,"
                + " updated_at) SELECT " + kid3 + ", c.id, c.campus_id, c.school_year_id, 'YEAR', '2026-09-01',"
                + " '2027-05-31', 'AWAITING_REVIEW', 'Nháp', TRUE, now(), now() FROM classrooms c WHERE c.id = "
                + classA + " RETURNING id");

        SheetGoal fromOldClass =
                sheetService.get(teacher, yearEval).domains().getFirst().goals().get(1);
        assertThat(fromOldClass.met()).isTrue();
        assertThat(fromOldClass.source()).isEqualTo("1/1 chủ đề đạt: Trường mầm non thân yêu (lớp MG B)");

        // Tổ trưởng nhóm tuổi (không dạy MG A): chỉ xem khi đã xác nhận, không sửa
        CurrentUser leader = user("leader-y", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) "
                        + "SELECT ?, 'AGE_GROUP_TEAM_LEADER', age_group_id, '2026-01-01' FROM classrooms WHERE id = ?",
                leader.userId(),
                classA);
        expectCode(() -> sheetService.get(leader, yearEval), ApiCode.EVALUATION_VIEW_FORBIDDEN);
        assertThat(evaluationService.list(leader, EvaluationPeriodType.YEAR, null, null, null))
                .extracting(r -> r.id())
                .doesNotContain(yearEval);
        endSemesterTwo();
        evaluationService.confirm(teacher, yearEval, "Trẻ phát triển bình thường, tham gia tích cực các hoạt động.");
        assertThat(sheetService.get(leader, yearEval).canEdit()).isFalse();
        assertThat(evaluationService.get(leader, yearEval).evaluation().id()).isEqualTo(yearEval);
        assertThat(evaluationService.list(leader, EvaluationPeriodType.YEAR, null, null, null))
                .extracting(r -> r.id())
                .contains(yearEval);
        // Tổ trưởng không xem đánh giá tháng của lớp không dạy
        expectCode(
                () -> evaluationService.list(leader, EvaluationPeriodType.MONTH, null, classA, null),
                ApiCode.EVALUATION_VIEW_FORBIDDEN);
    }

    @Test
    @DisplayName("AUTH-23: tổ trưởng xem (chỉ xem) bảng học kỳ, hồ sơ phát triển các lớp nhóm tuổi mình")
    void teamLeaderViewsSemesterAndDevelopmentOfOwnAgeGroup() {
        jdbc.update(
                "UPDATE school_years SET semester1_start='2026-09-01', semester1_end='2027-01-15'"
                        + " WHERE id=(SELECT school_year_id FROM classrooms WHERE id=?)",
                classA);
        CurrentUser leader = user("leader-z", CareNestRole.TEACHER);
        expectCode(() -> semesters.get(leader, classA, 1), ApiCode.EVALUATION_VIEW_FORBIDDEN);
        assertThat(evaluationService.developmentChildren(leader, null, null)).isEmpty();

        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) "
                        + "SELECT ?, 'AGE_GROUP_TEAM_LEADER', age_group_id, '2026-01-01' FROM classrooms WHERE id = ?",
                leader.userId(),
                classA);
        var board = semesters.get(leader, classA, 1);
        assertThat(board.canEdit()).isFalse();
        assertThat(board.canConfirm()).isFalse();
        assertThat(evaluationService.developmentChildren(leader, null, null))
                .filteredOn(r -> r.classId().equals(classA))
                .isNotEmpty()
                .allMatch(r -> !r.teaches());
        assertThat(evaluationService.developmentChildren(teacher, null, classA)).allMatch(r -> r.teaches());
        assertThat(evaluationService
                        .development(
                                leader,
                                kid1,
                                java.time.LocalDate.parse("2026-09-01"),
                                java.time.LocalDate.parse("2026-10-21"))
                        .child()
                        .id())
                .isEqualTo(kid1);
    }

    /** Học kỳ II kết thúc trước hôm nay (21/10/2026) để mở đánh giá cuối năm. */
    private void endSemesterTwo() {
        jdbc.update(
                "UPDATE school_years SET semester1_start='2026-09-01', semester1_end='2026-09-30',"
                        + " semester2_start='2026-10-01', semester2_end='2026-10-20'"
                        + " WHERE id=(SELECT school_year_id FROM classrooms WHERE id=?)",
                classA);
        entityManager.clear();
    }

    private ThemeEvaluationService themeService() {
        return service;
    }

    @Test
    void confirmedTableSurvivesNewYearPlanVersion() {
        service.save(
                teacher,
                new ThemeEvaluationRequest(classA, 9201L, "Lưu ý", List.of(new CellInput(kid1, oldGoal1, false))));
        service.confirm(teacher, new ThemeEvaluationRequest(classA, 9201L, "Lưu ý", List.of()));
        // PHT ban hành bản 3: chủ đề được chép với id mới, giữ dòng gốc
        jdbc.update(
                "INSERT INTO edu_team_plans (id, code, school_year_id, age_group_id, title, status, owner_id,"
                        + " version_no, source_plan_id, legacy_team_without_source) OVERRIDING SYSTEM VALUE SELECT 9003, 'KH-T3', school_year_id,"
                        + " age_group_id, title, 'APPROVED', owner_id, 3, 9002, legacy_team_without_source FROM edu_team_plans WHERE id = 9002");
        jdbc.update("INSERT INTO edu_themes (id, team_plan_id, sort_order, name, start_date, end_date, lineage_id)"
                + " OVERRIDING SYSTEM VALUE VALUES (9211, 9003, 1, 'Trường mầm non thân yêu', '2026-09-07',"
                + " '2026-10-02', 9201)");
        long copiedGoal = insertId("INSERT INTO edu_plan_goals (team_plan_id, domain_id, sort_order, text, goal_no,"
                + " lineage_id) VALUES (9003, 9100, 1, 'Trẻ chạy được 15m', 1, " + oldGoal1 + ") RETURNING id");
        tick(9311, copiedGoal, 9211);

        assertThat(service.listThemes(teacher, classA))
                .extracting(t -> t.themeId() + ":" + t.status())
                .containsExactly("9211:CONFIRMED");
        ThemeEvaluationResponse view = service.get(teacher, classA, 9211L);
        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(row(view, kid1).cells().getFirst().manual()).isTrue();
        // Link cũ (id chủ đề bản trước) vẫn mở được bảng; không tạo được bảng thứ hai
        assertThat(service.get(teacher, classA, 9201L).status()).isEqualTo("CONFIRMED");
        expectCode(
                () -> service.save(teacher, new ThemeEvaluationRequest(classA, 9211L, null, List.of())),
                ApiCode.EVALUATION_ALREADY_CONFIRMED);
    }

    @Test
    void frozenPresentationSurvivesRemovedThemeAndChangedRosterAndGoals() {
        ThemeEvaluationResponse frozen = service.confirm(
                teacher,
                new ThemeEvaluationRequest(classA, 9201L, "Lịch sử", List.of(new CellInput(kid1, oldGoal1, true))));
        jdbc.update("UPDATE edu_team_plans SET status = 'DRAFT' WHERE id = 9002");
        jdbc.update("UPDATE edu_plan_goals SET text = 'Đã thay đổi' WHERE id = ?", goal1);
        assertThat(service.get(teacher, classA, 9201L)).isEqualTo(frozen);
        assertThat(service.listThemes(teacher, classA))
                .anyMatch(t -> t.themeId().equals(9201L)
                        && t.name().equals(frozen.themeName())
                        && t.status().equals("CONFIRMED"));
        assertThat(service.childThemeResults(classA, kid1).get(oldGoal1).themeNames())
                .contains(frozen.themeName());
    }

    @Test
    void approvedClassCalendarControlsEvaluationDatesWithoutChangingOtherClass() {
        jdbc.update(
                "UPDATE edu_class_docs SET team_plan_id = 9002, status = 'APPROVED', content = ?::jsonb WHERE id = ?",
                "{\"schemaVersion\":2,\"themeSchedules\":[{\"themeId\":9201,\"startDate\":\"2026-10-26\",\"endDate\":\"2026-11-06\"}]}",
                doc);
        assertThat(service.listThemes(teacher, classA).getFirst().startDate())
                .isEqualTo(java.time.LocalDate.of(2026, 10, 26));
        assertThat(service.listThemes(principal, classB).getFirst().startDate())
                .isEqualTo(java.time.LocalDate.of(2026, 9, 7));
        expectCode(
                () -> service.save(teacher, new ThemeEvaluationRequest(classA, 9201L, null, List.of())),
                ApiCode.THEME_EVALUATION_NOT_STARTED);
    }

    @Test
    void scopeAndRulesAreEnforced() {
        // Mọi nhóm tuổi đều có đánh giá cuối chủ đề (Cần chốt 04/11/2026): nhà trẻ chưa có kế hoạch năm thì chưa có chủ
        // đề
        assertThat(service.listThemes(principal, nurseryClass)).isEmpty();
        // GV lớp khác không xem; HT xem nhưng không sửa
        expectCode(() -> service.get(otherTeacher, classA, 9201L), ApiCode.EVALUATION_VIEW_FORBIDDEN);
        assertThat(service.get(principal, classA, 9201L).canEdit()).isFalse();
        // Chủ đề chưa bắt đầu không sửa; ô của trẻ / MT không thuộc bảng bị từ chối
        expectCode(
                () -> service.save(teacher, new ThemeEvaluationRequest(classA, 9202L, null, List.of())),
                ApiCode.THEME_EVALUATION_NOT_STARTED);
        expectCode(
                () -> service.save(
                        teacher,
                        new ThemeEvaluationRequest(classA, 9201L, null, List.of(new CellInput(kid1, 1L, true)))),
                ApiCode.THEME_EVALUATION_CELL_INVALID);
        expectCode(() -> service.get(teacher, classA, 424242L), ApiCode.THEME_EVALUATION_THEME_NOT_FOUND);
        // Tổ trưởng nhóm tuổi xem được lớp trong nhóm
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) "
                        + "SELECT ?, 'AGE_GROUP_TEAM_LEADER', age_group_id, '2026-01-01' FROM classrooms WHERE id = ?",
                otherTeacher.userId(),
                classA);
        assertThat(service.get(otherTeacher, classA, 9201L).rows()).hasSize(2);
        // Danh sách lớp: tổ trưởng thấy cả lớp trong nhóm (không dạy), HT thấy mọi lớp mọi nhóm tuổi
        assertThat(service.listClasses(otherTeacher, null))
                .extracting(c -> c.classId() + ":" + c.teaches())
                .containsExactly(classB + ":true", classA + ":false");
        assertThat(service.listClasses(teacher, null))
                .extracting(c -> c.classId())
                .containsExactly(classA);
        // GV thường chỉ xem lớp mình: lớp khác cùng nhóm tuổi vẫn bị chặn ở server
        expectCode(() -> service.listThemes(teacher, classB), ApiCode.EVALUATION_VIEW_FORBIDDEN);
        assertThat(service.listClasses(principal, null))
                .extracting(c -> c.classId())
                .containsExactlyInAnyOrder(classA, classB, nurseryClass);
        // Tên in dưới chỗ ký: GV lớp, tổ trưởng nhóm tuổi, PHT ban hành kế hoạch năm (không có thì để trống)
        assertThat(service.get(principal, classA, 9201L).signers().teachers()).containsExactly("teacher-t");
        assertThat(service.get(principal, classA, 9201L).signers().teamLeaders())
                .containsExactly("teacher-b");
        assertThat(service.get(principal, classA, 9201L).signers().vicePrincipal())
                .isNull();
        CurrentUser vp = user("vp-t", CareNestRole.VICE_PRINCIPAL);
        jdbc.update("UPDATE edu_team_plans SET issued_by = ? WHERE status = 'APPROVED'", vp.userId());
        assertThat(service.get(principal, classA, 9201L).signers().vicePrincipal())
                .isEqualTo("vp-t");
    }

    private static ChildRow row(ThemeEvaluationResponse view, long childId) {
        return view.rows().stream()
                .filter(r -> r.childId() == childId)
                .findFirst()
                .orElseThrow();
    }

    private void tick(long contentId, long goalId, long themeId) {
        jdbc.update(
                "INSERT INTO edu_goal_contents (id, goal_id, sort_order, text) OVERRIDING SYSTEM VALUE VALUES (?, ?, 1, 'Nội dung')",
                contentId,
                goalId);
        jdbc.update("INSERT INTO edu_content_theme_ticks (content_id, theme_id) VALUES (?, ?)", contentId, themeId);
    }

    private void result(long childId, long classroomId, String date, String key, String met, String notMet) {
        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, recorded_by, recorded_at)"
                        + " SELECT ?, c.id, c.campus_id, ?::date, 'PRESENT', ?, now() FROM classrooms c WHERE c.id = ?"
                        + " ON CONFLICT (child_id, date) DO NOTHING",
                childId,
                date,
                teacher.userId(),
                classroomId);
        jdbc.update(
                "INSERT INTO daily_activity_results (child_id, classroom_id, campus_id, date, activity_key, class_doc_id,"
                        + " goals_met, goals_not_met, recorded_by, recorded_at)"
                        + " SELECT ?, c.id, c.campus_id, ?::date, ?, ?, ?, ?, ?, now() FROM classrooms c WHERE c.id = ?",
                childId,
                date,
                key,
                doc,
                met,
                notMet,
                teacher.userId(),
                classroomId);
    }

    private static void expectCode(Runnable call, ApiCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(code);
    }

    private long child(String name) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                + "'TE' || substr(md5(random()::text), 1, 12), '" + name
                + "', '2023-01-01', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
    }

    private void enroll(long childId, long classroomId) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childId,
                classroomId);
    }

    private CurrentUser teacherOf(String username, long classroomId) {
        CurrentUser t = user(username, CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                t.userId(),
                classroomId);
        return t;
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case PRINCIPAL, VICE_PRINCIPAL -> Set.of(Permission.ASSESSMENT_VIEW);
                    case TEACHER, TEAM_LEADER ->
                        Set.of(Permission.ASSESSMENT_VIEW, Permission.ASSESSMENT_RECORD, Permission.ASSESSMENT_CONFIRM);
                    default -> Set.of();
                };
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
