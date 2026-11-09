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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
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

/**
 * Đánh giá cuối chủ đề (OBS-27): MT của chủ đề lấy từ kế hoạch năm học đã duyệt, mỗi ô là lần chấm KTKN cuối cùng trong
 * chủ đề ở chính lớp đó, nối MT qua dòng gốc (giáo án chấm theo MT của bản kế hoạch cũ). Hôm nay = 21/10/2026.
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
    private YearEndSheetService sheetService;

    @Autowired
    private ChildEvaluationService evaluationService;

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
        // An: MT1 đạt rồi chưa đạt (lần cuối chưa đạt), MT3 đạt; Bình: MT1 đạt, MT3 chưa chấm
        result(kid1, classA, "2026-09-08", "k1", String.valueOf(oldGoal1), "");
        result(kid1, classA, "2026-09-15", "k2", "", String.valueOf(oldGoal1));
        result(kid1, classA, "2026-09-21", "k3", String.valueOf(goal2), "");
        result(kid2, classA, "2026-09-10", "k1", String.valueOf(oldGoal1), "");
        // Kết quả ngoài thời gian chủ đề không tính
        result(kid2, classA, "2026-10-06", "k1", "", String.valueOf(oldGoal1));
    }

    @Test
    void matrixUsesLatestResultPerGoalWithinThemeAndClass() {
        ThemeEvaluationResponse view = service.get(teacher, classA, 9201L);
        assertThat(view.goals()).extracting(g -> g.goalNo()).containsExactly((short) 1, (short) 3);
        assertThat(view.goals().getFirst().goalId()).isEqualTo(oldGoal1);
        ChildRow an = row(view, kid1);
        assertThat(an.cells()).extracting(c -> c.met()).containsExactly(false, true);
        assertThat(an.rate()).isEqualTo(50);
        assertThat(an.passed()).isFalse();
        ChildRow binh = row(view, kid2);
        assertThat(binh.cells()).extracting(c -> c.met()).containsExactly(true, null);
        assertThat(binh.passed()).isTrue();
        assertThat(view.goals().getFirst().metChildren()).isEqualTo(1);
        assertThat(view.goals().getFirst().rate()).isEqualTo(50);
        assertThat(view.passedChildren()).isEqualTo(1);
        assertThat(view.notPassedChildren()).isEqualTo(1);
        assertThat(view.canEdit()).isTrue();
        assertThat(view.canConfirm()).isTrue();
        assertThat(service.listThemes(teacher, classA))
                .extracting(t -> t.period())
                .containsExactly("ENDED", "UPCOMING");
    }

    @Test
    void teacherOverridesCellsThenConfirmsAndTheTableIsFrozen() {
        var override = new ThemeEvaluationRequest(
                classA, 9201L, " Cô chú ý rèn trẻ chạy ", List.of(new CellInput(kid1, oldGoal1, true)));
        ThemeEvaluationResponse saved = service.save(teacher, override);
        ChildRow an = row(saved, kid1);
        assertThat(an.cells().getFirst().met()).isTrue();
        assertThat(an.cells().getFirst().manual()).isTrue();
        assertThat(an.passed()).isTrue();
        assertThat(saved.note()).isEqualTo("Cô chú ý rèn trẻ chạy");

        ThemeEvaluationResponse confirmed =
                service.confirm(teacher, new ThemeEvaluationRequest(classA, 9201L, saved.note(), List.of()));
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.canEdit()).isFalse();
        // Đánh giá ngày đổi sau khi xác nhận không làm đổi bảng đã xác nhận
        result(kid2, classA, "2026-09-25", "k9", "", String.valueOf(oldGoal1));
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

        // MT lấy từ kế hoạch năm theo lĩnh vực; kết quả từ chủ đề gần nhất có MT; chủ đề chưa bắt đầu thì để trống
        YearEndSheetResponse sheet = sheetService.get(teacher, yearEval);
        assertThat(sheet.domains()).extracting(d -> d.name()).containsExactly("TEST lĩnh vực chủ đề");
        List<SheetGoal> goals = sheet.domains().getFirst().goals();
        assertThat(goals).extracting(SheetGoal::goalNo).containsExactly((short) 1, (short) 3, (short) 4);
        assertThat(goals).extracting(SheetGoal::met).containsExactly(false, true, null);
        assertThat(goals.getFirst().source()).isEqualTo("Trường mầm non thân yêu");
        assertThat(sheet.metCount()).isEqualTo(1);
        assertThat(sheet.notMetCount()).isEqualTo(1);
        assertThat(sheet.canEdit()).isTrue();
        // Lần cân đo gần nhất tới hôm nay (21/10): lần 30/10 chưa tới nên chưa lấy
        assertThat(sheet.measurement().weightKg()).isEqualByComparingTo("15.2");
        assertThat(sheet.teachers()).containsExactly("teacher-t");

        // Bảng cuối chủ đề GV sửa tay thì phiếu cuối năm theo
        themeService()
                .save(
                        teacher,
                        new ThemeEvaluationRequest(classA, 9201L, null, List.of(new CellInput(kid1, oldGoal1, true))));
        assertThat(sheetService
                        .get(teacher, yearEval)
                        .domains()
                        .getFirst()
                        .goals()
                        .getFirst()
                        .met())
                .isTrue();

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
        assertThat(edited).extracting(SheetGoal::manual).containsExactly(false, true, false);
        assertThat(saved.notes().forChild()).isEqualTo("Rèn trẻ biết tên trường");
        assertThat(saved.notes().forSchool()).isNull();

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
        assertThat(fromOldClass.source()).isEqualTo("Trường mầm non thân yêu (lớp MG B)");

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

    private ThemeEvaluationService themeService() {
        return service;
    }

    @Test
    void confirmedTableSurvivesNewYearPlanVersion() {
        service.save(
                teacher,
                new ThemeEvaluationRequest(classA, 9201L, "Lưu ý", List.of(new CellInput(kid1, oldGoal1, true))));
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
        assertThat(service.childThemeResults(classA, kid1).get(oldGoal1).themeName())
                .isEqualTo(frozen.themeName());
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
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role));
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
