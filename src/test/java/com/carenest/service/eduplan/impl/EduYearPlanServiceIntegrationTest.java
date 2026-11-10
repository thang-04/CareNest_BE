package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Luồng kế hoạch năm học SD-01..SD-08 qua service + policy thật trên
 * PostgreSQL. Mỗi test rollback. Cần Docker.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduYearPlanServiceIntegrationTest {

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
    private EduYearPlanService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EduPlanAccessPolicy policy;

    private long schoolYear;
    private long ageGroup3;
    private long ageGroup4;
    private long domainPhysical;
    private long domainLanguage;
    private CurrentUser vicePrincipal;
    private CurrentUser principal;
    private CurrentUser leader3;
    private CurrentUser leader4;
    private CurrentUser teacher3;
    private Long annualPlan3Id;
    private Long annualPlan4Id;
    private final Map<Long, UUID> sessions = new HashMap<>();

    @Test
    void deletedAnnualDraftCannotBeReadOrUpdatedAndReleasesItsCreationSlot() {
        var request = new YearPlanRequest(schoolYear, ageGroup3, "Năm học nháp", "", List.of(),
                List.of(), List.of(), com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR, null, List.of());
        var first = service.createYearPlan(vicePrincipal, request, new YearPlanEditRequest(null, 0L, UUID.randomUUID()));
        service.deleteYearPlan(vicePrincipal, first.id(), new YearPlanEditRequest(null, first.revision(), UUID.randomUUID()));
        assertErrorCode(() -> service.getYearPlan(vicePrincipal, first.id()), ApiCode.YEAR_PLAN_NOT_FOUND);
        assertErrorCode(() -> service.updateYearPlan(vicePrincipal, first.id(), request,
                new YearPlanEditRequest(null, first.revision(), UUID.randomUUID())), ApiCode.YEAR_PLAN_NOT_FOUND);
        var replacement = service.createYearPlan(vicePrincipal, request, new YearPlanEditRequest(null, 0L, UUID.randomUUID()));
        assertThat(replacement.id()).isNotEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT disabled FROM edu_year_plans WHERE id=?", Boolean.class, first.id())).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{\"mode\":\"MISSING_OR_UNKNOWN\"}", "null"})
    void savingAndSubmittingDoNotInventMissingDurationSnapshot(String snapshot) {
        var themes = new java.util.ArrayList<YearPlanRequest.Theme>();
        for (int i=0;i<9;i++) {
            var start = LocalDate.of(2026,9,7).plusWeeks(i);
            themes.add(new YearPlanRequest.Theme("s"+i,null,"Chủ đề "+i,"S"+i,start,start.plusDays(4),"",""));
        }
        var req = new YearPlanRequest(schoolYear, ageGroup3, "Năm học cần kiểm tra", "", List.of(),
                themes, List.of(), com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR, null, List.of());
        var created = service.createYearPlan(vicePrincipal, req, new YearPlanEditRequest(null, 0L, UUID.randomUUID()));
        entityManager.flush();
        jdbc.update("UPDATE edu_year_plans SET duration_policy_snapshot = ?::jsonb WHERE id=?", snapshot, created.id());
        entityManager.clear();
        var before = service.getYearPlan(vicePrincipal, created.id());
        service.updateYearPlan(vicePrincipal, created.id(), req,
                new YearPlanEditRequest(null, before.revision(), UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();
        var saved = service.getYearPlan(vicePrincipal, created.id());
        assertThat(saved.durationPolicySnapshot()).doesNotContain("BOUNDED");
        assertThatThrownBy(() -> service.submitYearPlan(vicePrincipal, created.id(), null,
                new YearPlanEditRequest(null, saved.revision(), UUID.randomUUID())))
                .isInstanceOf(GlobalException.class).hasMessageContaining("giới hạn");
        assertThat(service.getYearPlan(vicePrincipal, created.id()).status()).isEqualTo(EduPlanStatus.DRAFT);
    }

    @Test
    void annualSnapshotSurvivesReloadAndFiveWeeksCannotBeSubmitted() {
        var start = LocalDate.of(2026, 9, 7);
        var themes = new java.util.ArrayList<YearPlanRequest.Theme>();
        for (int i = 0; i < 9; i++) {
            var from = i == 0 ? start : start.plusWeeks(5L + (i - 1) * 2L);
            var end = from.plusWeeks(i == 0 ? 4 : 1).plusDays(4);
            themes.add(new YearPlanRequest.Theme("k" + i, null, "Chủ đề " + i, "C" + i, from, end, "", ""));
        }
        var req = new YearPlanRequest(schoolYear, ageGroup3, "Năm học", "", List.of(
                new YearPlanRequest.Domain(domainPhysical, List.of(
                        new YearPlanRequest.Row(null, "group", "Kết quả", List.of()),
                        new YearPlanRequest.Row(null, "goal", "Mục tiêu", List.of(
                                new YearPlanRequest.Content(null, "Nội dung", List.of())))))),
                themes, List.of(), com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR, null, List.of());
        var created = service.createYearPlan(vicePrincipal, req, new YearPlanEditRequest(null, 0L, UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();
        var reloaded = service.getYearPlan(vicePrincipal, created.id());
        assertThat(reloaded.durationPolicySnapshot()).contains("BOUNDED");
        assertThatThrownBy(() -> service.submitYearPlan(vicePrincipal, created.id(), null,
                new YearPlanEditRequest(null, reloaded.revision(), UUID.randomUUID())))
                .isInstanceOf(GlobalException.class).hasMessageContaining("5 tuần");
        assertThat(service.getYearPlan(vicePrincipal, created.id()).status()).isEqualTo(EduPlanStatus.DRAFT);
    }

    @Test
    @DisplayName("EDU-22: tự lưu thiếu tiêu đề được nhưng không gửi duyệt mục tiêu trống")
    void incompleteDraftSavesButCannotBeSubmitted() {
        YearPlanRequest draft = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "",
                "",
                List.of(new YearPlanRequest.Domain(
                        domainPhysical, List.of(new YearPlanRequest.Row(null, "goal", "", List.of())))),
                List.of(),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of());
        YearPlanDetailResponse created = service.createYearPlan(
                vicePrincipal, draft, new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        assertThat(created.title()).isEmpty();
        assertErrorCode(
                () -> service.submitYearPlan(
                        vicePrincipal, created.id(), null, edit(vicePrincipal, created.id(), true)),
                ApiCode.YEAR_PLAN_TITLE_REQUIRED);
        YearPlanRequest titled = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Title",
                "",
                draft.domains(),
                withMinimumThemes(List.of()),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of());
        service.updateYearPlan(vicePrincipal, created.id(), titled, edit(vicePrincipal, created.id(), true));
        assertErrorCode(
                () -> service.submitYearPlan(
                        vicePrincipal, created.id(), null, edit(vicePrincipal, created.id(), true)),
                ApiCode.PLAN_GOAL_REQUIRED);
    }

    @Test
    @DisplayName("EDU-22: tạo đã lưu nhưng mất phản hồi được thử lại cùng request mà không tạo trùng")
    void retryFirstCreateReturnsTheSamePlan() {
        // Bản tổ do hệ thống tạo khi ban hành năm; kiểm lặp tạo trên kế hoạch năm (PHT soạn)
        YearPlanEditRequest create = new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID());
        YearPlanRequest req = sampleSchoolYearRequest(ageGroup3);
        YearPlanDetailResponse first = service.createYearPlan(vicePrincipal, req, create);
        YearPlanDetailResponse again = service.createYearPlan(vicePrincipal, req, create);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.revision()).isEqualTo(first.revision());
        assertThat(again.domains()).isEqualTo(first.domains());
    }

    private UUID session(CurrentUser user) {
        return sessions.computeIfAbsent(user.userId(), ignored -> UUID.randomUUID());
    }

    private YearPlanEditRequest edit(CurrentUser user, Long id, boolean acquire) {
        YearPlanDetailResponse detail = service.getYearPlan(user, id);
        YearPlanEditRequest context = new YearPlanEditRequest(session(user), detail.revision(), UUID.randomUUID());
        if (acquire && detail.editing() == null && detail.permissions().canEdit()) {
            service.startEditing(user, id, context);
        }
        return context;
    }

    @BeforeEach
    void setUp() {
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-A', 'Campus A') RETURNING id");
        schoolYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31') RETURNING id");
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        ageGroup4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        domainPhysical = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 1", Long.class);
        domainLanguage = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 3", Long.class);
        long class3 = insertId(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'MGB1', ?) "
                        + "RETURNING id",
                campus,
                schoolYear,
                ageGroup3);

        vicePrincipal = user("vp", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
                vicePrincipal.userId(),
                campus);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-09-01')",
                principal.userId());
        leader3 = user("leader3", CareNestRole.TEACHER);
        insertLeader(leader3, ageGroup3);
        leader4 = user("leader4", CareNestRole.TEACHER);
        insertLeader(leader4, ageGroup4);
        teacher3 = user("teacher3", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01')",
                teacher3.userId(),
                class3);

        jdbc.update(
                "UPDATE edu_settings SET duration_policy = '{\"theme\":{\"minWeeks\":1,\"maxWeeks\":4},\"branch\":{\"minWeeks\":1,\"maxWeeks\":2}}'::jsonb");
    }

    private Long createApprovedAnnualPlan(long ageGroupId) {
        List<Long> existing = jdbc.query(
                "SELECT id FROM edu_year_plans WHERE school_year_id = ? AND age_group_id = ? AND status = 'APPROVED'",
                (rs, rowNum) -> rs.getLong(1),
                schoolYear,
                ageGroupId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String abbr = (ageGroupId == ageGroup3 ? "TMN3" : "TMN4") + "-" + System.nanoTime();
        YearPlanRequest req = new YearPlanRequest(
                schoolYear,
                ageGroupId,
                "Định hướng năm học",
                "Định hướng",
                annualDomains(),
                withMinimumThemes(List.of(datedTheme("t-1", abbr, 7, 25))),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of(annualTemplate()));
        UUID session = UUID.randomUUID();
        YearPlanDetailResponse draft =
                service.createYearPlan(vicePrincipal, req, new YearPlanEditRequest(session, 0L, UUID.randomUUID()));
        YearPlanDetailResponse sub = service.submitYearPlan(
                vicePrincipal, draft.id(), null, new YearPlanEditRequest(session, draft.revision(), UUID.randomUUID()));
        return service.issueYearPlan(
                        principal,
                        draft.id(),
                        "Approved",
                        new YearPlanEditRequest(null, sub.revision(), UUID.randomUUID()))
                .id();
    }

    @Test
    @DisplayName("EDU-01: kế hoạch thứ hai cho cùng (năm học, nhóm tuổi) bị 409")
    void secondPlanForSameAgeGroupIsConflict() {
        YearPlanRequest req = sampleSchoolYearRequest(ageGroup3);
        service.createYearPlan(
                vicePrincipal, req, new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));

        assertApiError(
                () -> service.createYearPlan(
                        vicePrincipal, req, new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID())),
                ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("AUTH-20: PHT có role nhưng không có hoặc đã hết phân công thì không lập, không xem được kế hoạch")
    void vicePrincipalWithoutActiveAssignmentIsDenied() {
        CurrentUser unassigned = user("vp-unassigned", CareNestRole.VICE_PRINCIPAL);
        CurrentUser ended = user("vp-ended", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from, valid_to) "
                        + "SELECT ?, 'VICE_PRINCIPAL', id, '2026-09-01', '2026-09-30' FROM campuses WHERE code = 'CS-A'",
                ended.userId());

        for (CurrentUser vp : List.of(unassigned, ended)) {
            assertApiError(
                    () -> service.createYearPlan(
                            vp,
                            sampleSchoolYearRequest(ageGroup3),
                            new YearPlanEditRequest(session(vp), 0L, UUID.randomUUID())),
                    ApiCode.FORBIDDEN);
            assertThat(policy.isVicePrincipal(vp)).isFalse();
        }
    }

    @Test
    @DisplayName("EDU-02: Tổ trưởng không lập được kế hoạch cho nhóm tuổi khác")
    void teamLeaderCannotCreateForOtherAgeGroup() {
        Long annual4 = createApprovedAnnualPlan(ageGroup4);
        Long annual3 = createApprovedAnnualPlan(ageGroup3);
        assertApiError(
                () -> service.createYearPlan(
                        leader3,
                        sampleTeamRequest(ageGroup4, annual4),
                        new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID())),
                ApiCode.FORBIDDEN);
        assertApiError(
                () -> service.createYearPlan(
                        teacher3,
                        sampleTeamRequest(ageGroup3, annual3),
                        new YearPlanEditRequest(session(teacher3), 0L, UUID.randomUUID())),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("EDU-09: lưu cây mục tiêu, đánh số MT theo thứ tự lĩnh vực, định hướng không nhận tick")
    void savesGoalTreeAndNumbersGoalsAcrossDomains() {
        YearPlanDetailResponse created = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup3,
                        List.of(
                                new YearPlanRequest.Domain(
                                        domainLanguage,
                                        List.of(new YearPlanRequest.Row(
                                                null, "goal", "Trẻ nghe hiểu lời nói", List.of()))),
                                new YearPlanRequest.Domain(
                                        domainPhysical,
                                        List.of(
                                                new YearPlanRequest.Row(null, "sub", "Phát triển vận động", List.of()),
                                                new YearPlanRequest.Row(
                                                        null,
                                                        "goal",
                                                        "Trẻ thực hiện đủ động tác",
                                                        List.of(new YearPlanRequest.Content(
                                                                null, "Hô hấp: hít vào, thở ra", List.of("t-1"))))))),
                        List.of(theme("t-1", null))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));

        assertThat(created.code()).isEqualTo("KHN-KHNH-2627-MGB");
        assertThat(created.status()).isEqualTo(EduPlanStatus.DRAFT);
        YearPlanDetailResponse.Domain physical = domain(created, domainPhysical);
        YearPlanDetailResponse.Domain language = domain(created, domainLanguage);
        // Request gửi lĩnh vực ngôn ngữ trước nhưng MT đánh theo thứ tự lĩnh vực đã cấu hình
        assertThat(physical.rows()).extracting(YearPlanDetailResponse.Row::kind).containsExactly("sub", "goal");
        assertThat(physical.rows().get(1).goalNo()).isEqualTo((short) 1);
        assertThat(language.rows().get(0).goalNo()).isEqualTo((short) 2);
        // Định hướng năm học không nhận tick từ request
        assertThat(physical.rows().get(1).contents())
                .singleElement()
                .satisfies(content -> assertThat(content.themeIds()).isEmpty());

        // Lần tự lưu sau gửi id đã cấp: dòng giữ id, nội dung bị bỏ thì xóa
        YearPlanDetailResponse.Row goal = physical.rows().get(1);
        YearPlanRequest edit = requestSchoolYear(
                ageGroup3,
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(new YearPlanRequest.Row(goal.id(), "goal", "Trẻ khỏe mạnh", List.of())))),
                List.of(theme("t-1", created.themes().get(0).id())));
        YearPlanDetailResponse updated =
                service.updateYearPlan(vicePrincipal, created.id(), edit, edit(vicePrincipal, created.id(), true));

        assertThat(domain(updated, domainPhysical).rows()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(goal.id());
            assertThat(row.contents()).isEmpty();
            assertThat(row.goalNo()).isEqualTo((short) 1);
        });
        assertThat(domain(updated, domainLanguage).rows()).isEmpty();
    }

    @Test
    @DisplayName("EDU-42: tên viết tắt chủ đề trùng (bỏ khoảng trắng, không phân biệt hoa thường) bị chặn ngay khi lưu")
    void rejectsDuplicateThemeAbbrOnSave() {
        // Nhóm tuổi khác để lần tạo hợp lệ phía dưới không vướng kiểm tra trùng (năm học, nhóm tuổi)
        assertApiError(
                () -> service.createYearPlan(
                        vicePrincipal,
                        requestSchoolYear(
                                ageGroup4,
                                List.of(),
                                List.of(
                                        namedTheme("t-1", null, "Trường mầm non", "TMN"),
                                        namedTheme("t-2", null, "Thế giới động vật", " tmn "))),
                        new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID())),
                ApiCode.THEME_ABBR_DUPLICATE);

        // Ô viết tắt còn trống chưa tính là trùng (nháp chưa điền xong)
        YearPlanDetailResponse created = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup3,
                        List.of(),
                        List.of(
                                namedTheme("t-1", null, "Trường mầm non", ""),
                                namedTheme("t-2", null, "Bản thân", ""))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        Long first = created.themes().get(0).id();
        Long second = created.themes().get(1).id();
        assertApiError(
                () -> service.updateYearPlan(
                        vicePrincipal,
                        created.id(),
                        requestSchoolYear(
                                ageGroup3,
                                List.of(),
                                List.of(
                                        namedTheme(String.valueOf(first), first, "Trường mầm non", "BT"),
                                        namedTheme(String.valueOf(second), second, "Bản thân", "BT"))),
                        edit(vicePrincipal, created.id(), true)),
                ApiCode.THEME_ABBR_DUPLICATE);
    }

    @Test
    @DisplayName("EDU-09: đổi thứ tự hoặc xóa chủ đề, đợt nghỉ thì lưu theo thứ tự gửi lên, giữ id và dấu tích")
    void reordersThemesAndBreaksKeepingIdsAndTicks() {
        YearPlanDetailResponse created = teamDraft(ageGroup3, leader3);
        jdbc.update(
                "UPDATE edu_team_plans SET legacy_team_without_source = true, curriculum_source_plan_id = NULL WHERE id = ?",
                created.id());
        entityManager.clear();

        YearPlanRequest initReq = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Kế hoạch MGB",
                "",
                List.of(goalTickedTo("t-3", null, null)),
                List.of(
                        namedTheme("t-1", null, "Trường mầm non", "TMN"),
                        namedTheme("t-2", null, "Bản thân", "BT"),
                        namedTheme("t-3", null, "Gia đình", "GĐ")),
                List.of(
                        new YearPlanRequest.Break(null, "Tết", null, null),
                        new YearPlanRequest.Break(null, "Ôn tập", null, null)));
        created = service.updateYearPlan(leader3, created.id(), initReq, edit(leader3, created.id(), true));
        List<Long> themeIds =
                created.themes().stream().map(YearPlanDetailResponse.Theme::id).toList();
        List<Long> breakIds =
                created.breaks().stream().map(YearPlanDetailResponse.Break::id).toList();
        YearPlanDetailResponse.Row goal = domain(created, domainPhysical).rows().get(0);
        Long contentId = goal.contents().get(0).id();

        // Chủ đề "Gia đình" chuyển lên đầu, đợt nghỉ đảo chỗ; dấu tích gửi theo key là
        // id cũ
        String family = String.valueOf(themeIds.get(2));
        YearPlanDetailResponse reordered = service.updateYearPlan(
                leader3,
                created.id(),
                new YearPlanRequest(
                        null,
                        null,
                        "Kế hoạch MGB",
                        "",
                        List.of(goalTickedTo(family, goal.id(), contentId)),
                        List.of(
                                namedTheme(family, themeIds.get(2), "Gia đình", "GĐ"),
                                namedTheme(String.valueOf(themeIds.get(0)), themeIds.get(0), "Trường mầm non", "TMN"),
                                namedTheme(String.valueOf(themeIds.get(1)), themeIds.get(1), "Bản thân", "BT")),
                        List.of(
                                new YearPlanRequest.Break(breakIds.get(1), "Ôn tập", null, null),
                                new YearPlanRequest.Break(breakIds.get(0), "Tết", null, null))),
                edit(leader3, created.id(), true));

        assertThat(reordered.themes())
                .extracting(YearPlanDetailResponse.Theme::id)
                .containsExactly(themeIds.get(2), themeIds.get(0), themeIds.get(1));
        assertThat(reordered.breaks())
                .extracting(YearPlanDetailResponse.Break::label)
                .containsExactly("Ôn tập", "Tết");
        assertThat(domain(reordered, domainPhysical)
                        .rows()
                        .get(0)
                        .contents()
                        .get(0)
                        .themeIds())
                .containsExactly(themeIds.get(2));

        // Xóa chủ đề giữa: chủ đề sau dồn lên, vẫn giữ id
        YearPlanDetailResponse removed = service.updateYearPlan(
                leader3,
                created.id(),
                new YearPlanRequest(
                        null,
                        null,
                        "Kế hoạch MGB",
                        "",
                        List.of(goalTickedTo(family, goal.id(), contentId)),
                        List.of(
                                namedTheme(family, themeIds.get(2), "Gia đình", "GĐ"),
                                namedTheme(String.valueOf(themeIds.get(1)), themeIds.get(1), "Bản thân", "BT")),
                        List.of()),
                edit(leader3, created.id(), true));

        assertThat(removed.themes())
                .extracting(YearPlanDetailResponse.Theme::id)
                .containsExactly(themeIds.get(2), themeIds.get(1));
        assertThat(service.getYearPlan(leader3, created.id()).themes())
                .extracting(YearPlanDetailResponse.Theme::name)
                .containsExactly("Gia đình", "Bản thân");
    }

    @Test
    @DisplayName(
            "EDU-03, EDU-04, EDU-05: gửi duyệt sang REVIEW, người soạn & reviewer cấm sửa khi REVIEW, ban hành sang APPROVED")
    void submitEditIssueFlow() {
        YearPlanDetailResponse plan = teamDraft(ageGroup3, leader3);

        YearPlanDetailResponse sent = service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));
        assertThat(sent.status()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(sent.sentBy().userId()).isEqualTo(leader3.userId());
        // Đã gửi thì Tổ trưởng không sửa được (409 CONFLICT)
        assertApiError(
                () -> service.updateYearPlan(leader3, plan.id(), toUpdateRequest(plan), edit(leader3, plan.id(), true)),
                ApiCode.CONFLICT);
        // Reviewer cũng không sửa nội dung khi đang REVIEW (409 CONFLICT)
        assertApiError(
                () -> service.updateYearPlan(
                        vicePrincipal, plan.id(), toUpdateRequest(plan), edit(vicePrincipal, plan.id(), true)),
                ApiCode.CONFLICT);

        YearPlanDetailResponse issued =
                service.issueYearPlan(vicePrincipal, plan.id(), "Đạt", edit(vicePrincipal, plan.id(), true));
        assertThat(issued.status()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(issued.issued().userId()).isEqualTo(vicePrincipal.userId());
        assertThat(issued.issued().at()).isNotNull();
        assertApiError(
                () -> service.updateYearPlan(
                        vicePrincipal, plan.id(), toUpdateRequest(plan), edit(vicePrincipal, plan.id(), true)),
                ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("EDU-04: kế hoạch chưa có MT không gửi được")
    void cannotSubmitWithoutGoals() {
        YearPlanDetailResponse plan = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(ageGroup3, List.of(), List.of()),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));

        assertApiError(
                () -> service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true)),
                ApiCode.PLAN_GOAL_REQUIRED);
    }

    @Test
    @DisplayName("EDU-05: trả lại cần lý do, chỉ khi chờ duyệt; Tổ trưởng thấy lý do")
    void returnRequiresPendingAndReason() {
        YearPlanDetailResponse plan = teamDraft(ageGroup3, leader3);
        // Nháp: người duyệt chưa mở được bản (EDU-73(3) ⇒ 7384)
        assertErrorCode(
                () -> service.returnYearPlan(vicePrincipal, plan.id(), "Sửa MT", edit(vicePrincipal, plan.id(), true)),
                ApiCode.EDU_PLAN_NOT_RELEASED);
        service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));
        assertApiError(
                () -> service.returnYearPlan(vicePrincipal, plan.id(), " ", edit(vicePrincipal, plan.id(), true)),
                ApiCode.BAD_REQUEST);
        assertApiError(
                () -> service.returnYearPlan(leader3, plan.id(), "Sửa MT", edit(leader3, plan.id(), true)),
                ApiCode.FORBIDDEN);

        service.returnYearPlan(vicePrincipal, plan.id(), "Sửa MT 2", edit(vicePrincipal, plan.id(), true));
        YearPlanDetailResponse seenByLeader = service.getYearPlan(leader3, plan.id());

        assertThat(seenByLeader.status()).isEqualTo(EduPlanStatus.DRAFT);
        assertThat(seenByLeader.lastReturnNote()).isEqualTo("Sửa MT 2");
        assertThat(seenByLeader.permissions().canSubmit()).isTrue();
        assertThat(seenByLeader.permissions().canDelete()).isFalse();
    }

    @Test
    @DisplayName("EDU-05: tác giả gửi duyệt, không tự ban hành được; người duyệt ban hành thành công")
    void authorSubmitsAndReviewerIssues() {
        YearPlanDetailResponse plan = teamDraft(ageGroup3, leader3);

        // Chưa gửi duyệt (DRAFT): người duyệt chưa mở được bản (EDU-73(3) ⇒ 7384)
        assertErrorCode(
                () -> service.issueYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true)),
                ApiCode.EDU_PLAN_NOT_RELEASED);

        service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));

        // Tác giả không tự ban hành được
        assertApiError(
                () -> service.issueYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true)),
                ApiCode.FORBIDDEN);
        assertThat(service.issueYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true))
                        .status())
                .isEqualTo(EduPlanStatus.APPROVED);
    }

    @Test
    @DisplayName("EDU-64: tổ trưởng chỉ xem kế hoạch tổ của nhóm tuổi được phân công")
    void viewScopeByRole() {
        YearPlanDetailResponse plan = teamDraft(ageGroup3, leader3);

        assertApiError(() -> service.getYearPlan(teacher3, plan.id()), ApiCode.FORBIDDEN);
        assertApiError(() -> service.getYearPlan(principal, plan.id()), ApiCode.FORBIDDEN);
        assertApiError(() -> service.getYearPlan(leader4, plan.id()), ApiCode.FORBIDDEN);
        assertThat(list(leader4).items()).isEmpty();
        assertThat(list(teacher3).items()).isEmpty();
        // EDU-73(3): PHT không phụ trách chỉ thấy bản tổ đã ban hành
        assertThat(list(vicePrincipal).items()).isEmpty();

        service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));
        service.issueYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true));
        assertThat(list(vicePrincipal).items())
                .extracting(YearPlanSummaryResponse::id)
                .containsExactly(plan.id());

        assertThat(service.getYearPlan(teacher3, plan.id()).permissions().canEdit())
                .isFalse();
        assertApiError(() -> service.getYearPlan(leader4, plan.id()), ApiCode.FORBIDDEN);
        assertThat(list(leader4).items()).isEmpty();
        PageResponse<YearPlanSummaryResponse> teacherList = list(teacher3);
        assertThat(teacherList.items()).singleElement().satisfies(summary -> {
            assertThat(summary.goalCount()).isEqualTo(2);
            assertThat(summary.themeCount()).isEqualTo(9);
            assertThat(summary.owner().name()).isEqualTo("Demo leader3");
        });
    }

    @Test
    @DisplayName("EDU-07: chỉ Phó HT xóa nháp định hướng năm; kế hoạch tổ không ai được xóa")
    void onlyVicePrincipalDeletesDraft() {
        YearPlanDetailResponse teamPlan = teamDraft(ageGroup3, leader3);
        // Kế hoạch tổ: cả Tổ trưởng lẫn Phó HT đều không được xóa (FR-018)
        assertApiError(
                () -> service.deleteYearPlan(leader3, teamPlan.id(), edit(leader3, teamPlan.id(), true)),
                ApiCode.FORBIDDEN);
        assertApiError(
                () -> service.deleteYearPlan(vicePrincipal, teamPlan.id(), edit(vicePrincipal, teamPlan.id(), true)),
                ApiCode.FORBIDDEN);

        // Kế hoạch định hướng năm học (SCHOOL_YEAR): Phó HT được xóa nháp
        YearPlanDetailResponse plan = service.createYearPlan(
                vicePrincipal,
                // Mục tiêu có nhóm kết quả + nội dung để gửi được (EDU-66)
                requestSchoolYear(ageGroup4, annualDomains(), List.of(theme("t-1", null))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));

        // Tổ trưởng không được xóa định hướng
        assertApiError(
                () -> service.deleteYearPlan(leader3, plan.id(), edit(leader3, plan.id(), true)), ApiCode.FORBIDDEN);

        // Đang ở REVIEW thì không được xóa
        YearPlanDetailResponse sent =
                service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true));
        assertApiError(
                () -> service.deleteYearPlan(vicePrincipal, plan.id(), edit(vicePrincipal, plan.id(), true)),
                ApiCode.CONFLICT);

        // Trả lại về DRAFT
        YearPlanDetailResponse returned = service.returnYearPlan(
                principal, plan.id(), "Sửa", new YearPlanEditRequest(null, sent.revision(), UUID.randomUUID()));

        // Phó HT đang mở sửa bằng session hiện tại vẫn xóa được bản nháp của mình
        assertThat(service.getYearPlan(vicePrincipal, plan.id()).permissions().canDelete())
                .isTrue();
        service.deleteYearPlan(
                vicePrincipal,
                plan.id(),
                new YearPlanEditRequest(session(vicePrincipal), returned.revision(), UUID.randomUUID()));
        assertApiError(() -> service.getYearPlan(vicePrincipal, plan.id()), ApiCode.NOT_FOUND);
    }

    @Test
    @DisplayName("EDU-10: bị trả lại thì về DRAFT, tác giả sửa tiếp được và gửi lại")
    void rejectedPlanEditableByBothAndKeepsStatus() {
        YearPlanDetailResponse plan = teamDraft(ageGroup3, leader3);
        service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));
        service.returnYearPlan(vicePrincipal, plan.id(), "Sửa MT", edit(vicePrincipal, plan.id(), true));

        // Reviewer (vicePrincipal) không được sửa kế hoạch năm do reviewer precedence
        assertApiError(
                () -> service.startEditing(vicePrincipal, plan.id(), edit(vicePrincipal, plan.id(), false)),
                ApiCode.YEAR_PLAN_EDIT_FORBIDDEN);

        YearPlanDetailResponse editing = service.startEditing(leader3, plan.id(), edit(leader3, plan.id(), false));
        assertThat(editing.status()).isEqualTo(EduPlanStatus.DRAFT);
        assertThat(editing.lastReturnNote()).isEqualTo("Sửa MT");

        assertThat(service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true))
                        .status())
                .isEqualTo(EduPlanStatus.REVIEW);
    }

    @Test
    @DisplayName("EDU-23: chủ đề, đợt nghỉ có ngày ngoài năm học thì không lưu được")
    void datesOutsideSchoolYearAreRejected() {
        YearPlanDetailResponse plan = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup3,
                        List.of(new YearPlanRequest.Domain(
                                domainPhysical, List.of(new YearPlanRequest.Row(null, "goal", "MT", List.of())))),
                        List.of(theme("t-1", null))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        YearPlanRequest lateBreak = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Định hướng MGB",
                "",
                List.of(new YearPlanRequest.Domain(
                        domainPhysical, List.of(new YearPlanRequest.Row(null, "goal", "MT", List.of())))),
                List.of(theme("t-1", null)),
                List.of(new YearPlanRequest.Break(
                        null, "Nghỉ hè", LocalDate.of(2027, 5, 31), LocalDate.of(2027, 6, 5))),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of());
        service.updateYearPlan(vicePrincipal, plan.id(), lateBreak, edit(vicePrincipal, plan.id(), true));
        assertApiError(
                () -> service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true)),
                ApiCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("EDU-39/40: chủ đề phải trọn tuần Thứ 2 → Thứ 6 và không trùng thời gian; nháp vẫn lưu được")
    void themesMustCoverWholeWeeksWithoutOverlap() {
        YearPlanDetailResponse plan = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup3,
                        List.of(new YearPlanRequest.Domain(
                                domainPhysical, List.of(new YearPlanRequest.Row(null, "goal", "MT", List.of())))),
                        List.of(datedTheme("t-1", "TMN", 7, 25), datedTheme("t-2", "BT", 27, 30))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        assertErrorCode(
                () -> service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true)),
                ApiCode.THEME_WEEK_ALIGNMENT);

        YearPlanRequest overlapping = requestSchoolYear(
                ageGroup3,
                List.of(new YearPlanRequest.Domain(
                        domainPhysical, List.of(new YearPlanRequest.Row(null, "goal", "MT", List.of())))),
                List.of(datedTheme("t-1", "TMN", 7, 25), datedTheme("t-2", "BT", 21, 25)));
        service.updateYearPlan(vicePrincipal, plan.id(), overlapping, edit(vicePrincipal, plan.id(), true));
        assertErrorCode(
                () -> service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true)),
                ApiCode.THEME_DATES_OVERLAP);
    }

    @Test
    @DisplayName("EDU-51: trùng tên trong cùng lĩnh vực con chặn gửi; khác lĩnh vực con thì được; nháp vẫn lưu được")
    void duplicateNamesInSubDomainBlockSubmit() {
        YearPlanDetailResponse plan = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup3,
                        List.of(physical(
                                subRow("Phát triển vận động"),
                                groupRow("Kết quả mong đợi"),
                                goalRow("Trẻ thực hiện đủ động tác", "Hô hấp"),
                                goalRow(" trẻ thực hiện  đủ ĐỘNG tác ", "Tay"))),
                        List.of(theme("t-1", null))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        assertErrorCode(
                () -> service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true)),
                ApiCode.GOAL_NAME_DUPLICATE);

        YearPlanRequest duplicateContent = requestSchoolYear(
                ageGroup3,
                List.of(physical(
                        subRow("Phát triển vận động"),
                        groupRow("Kết quả mong đợi"),
                        goalRow("Trẻ thực hiện đủ động tác", "Hô hấp", "hô  hấp"))),
                List.of(theme("t-1", null)));
        service.updateYearPlan(vicePrincipal, plan.id(), duplicateContent, edit(vicePrincipal, plan.id(), true));
        assertErrorCode(
                () -> service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true)),
                ApiCode.GOAL_NAME_DUPLICATE);

        YearPlanRequest separateSubs = requestSchoolYear(
                ageGroup3,
                List.of(physical(
                        subRow("Phát triển vận động"),
                        groupRow("Kết quả mong đợi"),
                        goalRow("Trẻ thực hiện đủ động tác", "Hô hấp"),
                        subRow("Dinh dưỡng"),
                        groupRow("Kết quả mong đợi"),
                        goalRow("Trẻ thực hiện đủ động tác", "Hô hấp"))),
                List.of(theme("t-1", null)));
        service.updateYearPlan(vicePrincipal, plan.id(), separateSubs, edit(vicePrincipal, plan.id(), true));
        assertThat(service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true))
                        .status())
                .isEqualTo(EduPlanStatus.REVIEW);
    }

    @Test
    @DisplayName("EDU-53: lĩnh vực có lĩnh vực con mà còn mục tiêu nằm ngoài lĩnh vực con thì chặn gửi; nháp vẫn lưu")
    void mixedSubDomainBlocksSubmit() {
        YearPlanDetailResponse plan = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup3,
                        List.of(physical(
                                goalRow("Trẻ giữ được thăng bằng", "Đi trên ghế thể dục"),
                                subRow("Phát triển vận động"),
                                groupRow("Kết quả mong đợi"),
                                goalRow("Trẻ thực hiện đủ động tác", "Hô hấp"))),
                        List.of(theme("t-1", null))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        assertErrorCode(
                () -> service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true)),
                ApiCode.GOAL_SUB_DOMAIN_MIXED);

        YearPlanRequest allInSubs = requestSchoolYear(
                ageGroup3,
                List.of(physical(
                        subRow("Phát triển vận động"),
                        groupRow("Kết quả mong đợi"),
                        goalRow("Trẻ giữ được thăng bằng", "Đi trên ghế thể dục"),
                        goalRow("Trẻ thực hiện đủ động tác", "Hô hấp"))),
                List.of(theme("t-1", null)));
        service.updateYearPlan(vicePrincipal, plan.id(), allInSubs, edit(vicePrincipal, plan.id(), true));
        assertThat(service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true))
                        .status())
                .isEqualTo(EduPlanStatus.REVIEW);
    }

    @Test
    @DisplayName("Bỏ thanh lĩnh vực con, giữ nhóm và mục tiêu: lưu lại không mất dòng, dòng không còn lĩnh vực con")
    void removingSubDomainKeepsItsRows() {
        YearPlanDetailResponse created = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup3,
                        List.of(physical(
                                subRow("Lỡ tay"),
                                new YearPlanRequest.Row(null, "group", "Giữ được thăng bằng", List.of()),
                                goalRow("Trẻ thực hiện đủ động tác", "Hô hấp"))),
                        List.of(theme("t-1", null))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        List<YearPlanDetailResponse.Row> rows = domain(created, domainPhysical).rows();
        YearPlanDetailResponse.Row group =
                rows.stream().filter(r -> r.kind().equals("group")).findFirst().orElseThrow();
        YearPlanDetailResponse.Row goal =
                rows.stream().filter(r -> r.kind().equals("goal")).findFirst().orElseThrow();
        YearPlanRequest withoutSub = requestSchoolYear(
                ageGroup3,
                List.of(physical(
                        new YearPlanRequest.Row(group.id(), "group", group.text(), List.of()),
                        new YearPlanRequest.Row(
                                goal.id(),
                                "goal",
                                goal.text(),
                                List.of(new YearPlanRequest.Content(
                                        goal.contents().getFirst().id(), "Hô hấp", List.of()))))),
                List.of(theme("t-1", null)));

        YearPlanDetailResponse saved = service.updateYearPlan(
                vicePrincipal, created.id(), withoutSub, edit(vicePrincipal, created.id(), true));

        assertThat(domain(saved, domainPhysical).rows())
                .extracting(YearPlanDetailResponse.Row::id)
                .containsExactly(group.id(), goal.id());
        assertThat(domain(saved, domainPhysical).rows().get(1).contents()).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_plan_goals WHERE id = ? AND sub_domain_id IS NULL",
                        Integer.class,
                        goal.id()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("EDU-52/EDU-66 (Tiền 10/11/2026): chủ đề chưa chọn ngày bị chặn ngay từ bước gửi duyệt")
    void themeDatesRequiredToIssue() {
        YearPlanDetailResponse plan = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup3,
                        sampleSchoolYearRequest(ageGroup3).domains(),
                        List.of(namedTheme("t-1", null, "Trường mầm non", "TMN"))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        assertErrorCode(
                () -> service.submitYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true)),
                ApiCode.THEME_DATES_REQUIRED);
    }

    @Test
    @DisplayName("EDU-28: mục tiêu lấy từ danh mục của Bộ giữ liên kết, trả câu gốc; sai nhóm tuổi bị từ chối")
    void curriculumGoalLinkKeptAndValidated() {
        Long ministryGoal = jdbc.queryForObject(
                "SELECT g.id FROM edu_curriculum_goals g JOIN age_groups a ON a.id = g.age_group_id "
                        + "WHERE a.code = 'ag-3' AND g.goal_no = '2.1' AND g.section_ref LIKE '%› I › a) › 2 › 2.1'",
                Long.class);
        String ministryText = jdbc.queryForObject(
                "SELECT goal_text FROM edu_curriculum_goals WHERE id = ?", String.class, ministryGoal);
        YearPlanRequest linked = requestSchoolYear(
                ageGroup3,
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(
                                groupRow("Kết quả mong đợi"),
                                new YearPlanRequest.Row(null, "goal", ministryText, contentOf("Nội dung"), ministryGoal)))),
                List.of(theme("t-1", null)));
        YearPlanDetailResponse created = service.createYearPlan(
                vicePrincipal, linked, new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));

        YearPlanDetailResponse.Row row = goalOf(created);
        assertThat(row.curriculumGoalId()).isEqualTo(ministryGoal);
        // Câu gốc theo danh mục Bộ hiện hành trong DB (seed đã đổi), không hard-code
        assertThat(row.curriculumGoalText()).isEqualTo(ministryText);
        assertThat(row.curriculumSectionRef()).isEqualTo("Phần ba › D › I › a) › 2 › 2.1");

        Long otherAgeGoal = jdbc.queryForObject(
                "SELECT g.id FROM edu_curriculum_goals g JOIN age_groups a ON a.id = g.age_group_id "
                        + "WHERE a.code = 'ag-5' ORDER BY g.sort_order LIMIT 1",
                Long.class);
        YearPlanRequest wrongAge = requestSchoolYear(
                ageGroup3,
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(new YearPlanRequest.Row(row.id(), "goal", ministryText, List.of(), otherAgeGoal)))),
                List.of(theme("t-1", null)));
        assertErrorCode(
                () -> service.updateYearPlan(
                        vicePrincipal, created.id(), wrongAge, edit(vicePrincipal, created.id(), true)),
                ApiCode.CURRICULUM_GOAL_INVALID);

        // Phiên bản điều chỉnh (EDU-25) chép cả liên kết nguồn của Bộ
        service.submitYearPlan(vicePrincipal, created.id(), null, edit(vicePrincipal, created.id(), true));
        service.issueYearPlan(principal, created.id(), "Đạt", edit(principal, created.id(), true));
        YearPlanDetailResponse v2 =
                service.createVersion(vicePrincipal, created.id(), edit(vicePrincipal, created.id(), false));
        YearPlanDetailResponse.Row copied = goalOf(v2);
        assertThat(copied.curriculumGoalId()).isEqualTo(ministryGoal);
        assertThat(copied.curriculumSectionRef()).isEqualTo("Phần ba › D › I › a) › 2 › 2.1");
    }

    @Test
    @DisplayName(
            "EDU-28: lĩnh vực con và nhóm lấy từ Bộ giữ liên kết, trả câu gốc, chép sang phiên bản; sai nhóm tuổi bị từ chối")
    void curriculumHeadingLinksKeptAndValidated() {
        Long ministryGoal = jdbc.queryForObject(
                "SELECT g.id FROM edu_curriculum_goals g JOIN age_groups a ON a.id = g.age_group_id "
                        + "WHERE a.code = 'ag-3' AND g.goal_no = '2.1' AND g.section_ref LIKE '%› I › a) › 2 › 2.1'",
                Long.class);
        Long ministryGroup = jdbc.queryForObject(
                "SELECT expected_result_id FROM edu_curriculum_goals WHERE id = ?", Long.class, ministryGoal);
        Long ministrySub = jdbc.queryForObject(
                "SELECT sub_domain_id FROM edu_curriculum_expected_results WHERE id = ?", Long.class, ministryGroup);
        String groupText = jdbc.queryForObject(
                "SELECT text FROM edu_curriculum_expected_results WHERE id = ?", String.class, ministryGroup);
        YearPlanRequest linked = requestSchoolYear(
                ageGroup3,
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(
                                new YearPlanRequest.Row(
                                        null, "sub", "Vận động (trường sửa)", List.of(), null, ministrySub, null),
                                new YearPlanRequest.Row(null, "group", groupText, List.of(), null, null, ministryGroup),
                                new YearPlanRequest.Row(null, "goal", "Giữ thăng bằng", contentOf("Nội dung"), ministryGoal)))),
                List.of(theme("t-1", null)));
        YearPlanDetailResponse created = service.createYearPlan(
                vicePrincipal, linked, new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));

        List<YearPlanDetailResponse.Row> rows = domain(created, domainPhysical).rows();
        assertThat(rows.get(0).curriculumSubDomainId()).isEqualTo(ministrySub);
        assertThat(rows.get(0).curriculumText()).isEqualTo("Phát triển vận động");
        assertThat(rows.get(0).curriculumGoalId()).isNull();
        assertThat(rows.get(1).curriculumExpectedResultId()).isEqualTo(ministryGroup);
        assertThat(rows.get(1).curriculumText()).isEqualTo(groupText);
        assertThat(rows.get(2).curriculumGoalId()).isEqualTo(ministryGoal);
        assertThat(rows.get(2).curriculumText()).isNull();

        Long otherAgeGroup = jdbc.queryForObject(
                "SELECT r.id FROM edu_curriculum_expected_results r JOIN age_groups a ON a.id = r.age_group_id "
                        + "WHERE a.code = 'ag-5' ORDER BY r.sort_order LIMIT 1",
                Long.class);
        YearPlanRequest wrongAge = requestSchoolYear(
                ageGroup3,
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(new YearPlanRequest.Row(
                                rows.get(1).id(), "group", groupText, List.of(), null, null, otherAgeGroup)))),
                List.of(theme("t-1", null)));
        assertErrorCode(
                () -> service.updateYearPlan(
                        vicePrincipal, created.id(), wrongAge, edit(vicePrincipal, created.id(), true)),
                ApiCode.CURRICULUM_GOAL_INVALID);

        service.submitYearPlan(vicePrincipal, created.id(), null, edit(vicePrincipal, created.id(), true));
        service.issueYearPlan(principal, created.id(), "Đạt", edit(principal, created.id(), true));
        YearPlanDetailResponse v2 =
                service.createVersion(vicePrincipal, created.id(), edit(vicePrincipal, created.id(), false));
        List<YearPlanDetailResponse.Row> copied = domain(v2, domainPhysical).rows();
        assertThat(copied.get(0).curriculumSubDomainId()).isEqualTo(ministrySub);
        assertThat(copied.get(1).curriculumExpectedResultId()).isEqualTo(ministryGroup);
    }

    @Test
    @DisplayName("EDU-09: chuyển nhóm và MT giữ lại trước khi xóa lĩnh vực con cũ, không mất nội dung/dấu tích")
    void storesThreeTablesAndReparentsBeforeDeletingHeadings() {
        var rows = List.of(
                new YearPlanRequest.Row(null, "sub", "Lĩnh vực con A", List.of()),
                new YearPlanRequest.Row(null, "group", "Nhóm A1", List.of()),
                new YearPlanRequest.Row(
                        null, "goal", "MT 1", List.of(new YearPlanRequest.Content(null, "Nội dung demo", List.of()))),
                new YearPlanRequest.Row(null, "group", "Nhóm A2", List.of()),
                new YearPlanRequest.Row(null, "goal", "MT 2", contentOf("Nội dung 2")),
                new YearPlanRequest.Row(null, "sub", "Lĩnh vực con B", List.of()),
                new YearPlanRequest.Row(null, "group", "Nhóm B1", List.of()),
                new YearPlanRequest.Row(null, "goal", "MT 3", contentOf("Nội dung 3")));
        var created = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup3,
                        List.of(new YearPlanRequest.Domain(domainPhysical, rows)),
                        List.of(theme("t-1", null))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        var saved = domain(created, domainPhysical).rows();
        var content = saved.get(2).contents().get(0);
        long subB = saved.get(5).id();
        long groupA2 = saved.get(3).id();
        long goal1 = saved.get(2).id();
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT expected_result_id FROM edu_plan_goals WHERE id=?", Long.class, goal1))
                .isEqualTo(saved.get(1).id());
        var changed = List.of(
                new YearPlanRequest.Row(subB, "sub", "Lĩnh vực con B", List.of()),
                new YearPlanRequest.Row(groupA2, "group", "Nhóm A2", List.of()),
                new YearPlanRequest.Row(
                        goal1,
                        "goal",
                        "MT 1",
                        List.of(new YearPlanRequest.Content(content.id(), content.text(), List.of()))),
                new YearPlanRequest.Row(saved.get(4).id(), "goal", "MT 2", keepContents(saved.get(4))),
                new YearPlanRequest.Row(saved.get(6).id(), "group", "Nhóm B1", List.of()),
                new YearPlanRequest.Row(saved.get(7).id(), "goal", "MT 3", keepContents(saved.get(7))));
        var updated = service.updateYearPlan(
                vicePrincipal,
                created.id(),
                requestSchoolYear(
                        ageGroup3,
                        List.of(new YearPlanRequest.Domain(domainPhysical, changed)),
                        List.of(theme("t-1", created.themes().get(0).id()))),
                edit(vicePrincipal, created.id(), true));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT sub_domain_id FROM edu_plan_goals WHERE id=?", Long.class, goal1))
                .isEqualTo(subB);
        assertThat(jdbc.queryForObject("SELECT expected_result_id FROM edu_plan_goals WHERE id=?", Long.class, goal1))
                .isEqualTo(groupA2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_plan_sub_domains WHERE school_year_plan_id=?", Long.class, created.id()))
                .isEqualTo(1L);
        assertThat(domain(updated, domainPhysical).rows().get(2).contents())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.id()).isEqualTo(content.id());
                });
        entityManager.clear();
        assertThat(domain(service.getYearPlan(vicePrincipal, created.id()), domainPhysical)
                        .rows())
                .extracting(YearPlanDetailResponse.Row::id)
                .containsExactlyElementsOf(
                        changed.stream().map(YearPlanRequest.Row::id).toList());
        service.submitYearPlan(vicePrincipal, created.id(), null, edit(vicePrincipal, created.id(), true));
        service.issueYearPlan(principal, created.id(), "Đạt", edit(principal, created.id(), true));
        var copied = service.createVersion(vicePrincipal, created.id(), edit(vicePrincipal, created.id(), false));
        var copiedRows = domain(copied, domainPhysical).rows();
        entityManager.flush();
        assertThat(copiedRows)
                .extracting(YearPlanDetailResponse.Row::kind)
                .containsExactly("sub", "group", "goal", "goal", "group", "goal");
        assertThat(jdbc.queryForObject(
                        "SELECT sub_domain_id FROM edu_plan_expected_results WHERE id=?",
                        Long.class,
                        copiedRows.get(1).id()))
                .isEqualTo(copiedRows.get(0).id());
        assertThat(jdbc.queryForObject(
                        "SELECT expected_result_id FROM edu_plan_goals WHERE id=?",
                        Long.class,
                        copiedRows.get(2).id()))
                .isEqualTo(copiedRows.get(1).id());
        assertThat(jdbc.queryForObject(
                        "SELECT sub_domain_id FROM edu_plan_goals WHERE id=?",
                        Long.class,
                        copiedRows.get(2).id()))
                .isEqualTo(copiedRows.get(0).id());
        assertThat(jdbc.queryForObject(
                        "SELECT lineage_id FROM edu_plan_sub_domains WHERE id=?",
                        Long.class,
                        copiedRows.get(0).id()))
                .isEqualTo(subB);
        assertThat(jdbc.queryForObject(
                        "SELECT lineage_id FROM edu_plan_expected_results WHERE id=?",
                        Long.class,
                        copiedRows.get(1).id()))
                .isEqualTo(groupA2);
        assertThat(jdbc.queryForObject(
                        "SELECT lineage_id FROM edu_plan_goals WHERE id=?",
                        Long.class,
                        copiedRows.get(2).id()))
                .isEqualTo(goal1);
        assertThat(jdbc.queryForObject("SELECT sub_domain_id FROM edu_plan_goals WHERE id=?", Long.class, goal1))
                .isEqualTo(subB);
    }

    @Test
    @DisplayName("EDU-09: từ chối tái sử dụng ID mục tiêu cho dòng nhóm")
    void rejectsRowIdOfDifferentKind() {
        var created = service.createYearPlan(
                vicePrincipal,
                sampleSchoolYearRequest(ageGroup3),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        var row = goalOf(created);
        var wrongKind = new YearPlanRequest.Row(row.id(), "group", "Không đổi loại ID", List.of());
        assertErrorCode(
                () -> service.updateYearPlan(
                        vicePrincipal,
                        created.id(),
                        requestSchoolYear(
                                ageGroup3,
                                List.of(new YearPlanRequest.Domain(domainPhysical, List.of(wrongKind))),
                                List.of(theme("t-1", null))),
                        edit(vicePrincipal, created.id(), true)),
                ApiCode.INVALID_VALUE);
    }

    @Test
    @DisplayName("EDU-09: từ chối ID dòng lặp trong một request")
    void rejectsDuplicateRowIds() {
        var created = service.createYearPlan(
                vicePrincipal,
                sampleSchoolYearRequest(ageGroup3),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        var row = goalOf(created);
        var existing = new YearPlanRequest.Row(row.id(), "goal", row.text(), List.of());
        assertErrorCode(
                () -> service.updateYearPlan(
                        vicePrincipal,
                        created.id(),
                        requestSchoolYear(
                                ageGroup3,
                                List.of(new YearPlanRequest.Domain(domainPhysical, List.of(existing, existing))),
                                List.of(theme("t-1", null))),
                        edit(vicePrincipal, created.id(), true)),
                ApiCode.INVALID_VALUE);
    }

    @Test
    @DisplayName("EDU-09: từ chối ID không thuộc kế hoạch")
    void rejectsForeignRowIds() {
        var created = service.createYearPlan(
                vicePrincipal,
                sampleSchoolYearRequest(ageGroup3),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        var row = goalOf(created);
        var other = service.createYearPlan(
                vicePrincipal,
                requestSchoolYear(
                        ageGroup4,
                        List.of(new YearPlanRequest.Domain(
                                domainPhysical,
                                List.of(new YearPlanRequest.Row(null, "goal", "MT nguồn khác", List.of())))),
                        List.of(theme("t-1", null))),
                new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        long foreignId = domain(other, domainPhysical).rows().getFirst().id();
        assertErrorCode(
                () -> service.updateYearPlan(
                        vicePrincipal,
                        created.id(),
                        requestSchoolYear(
                                ageGroup3,
                                List.of(new YearPlanRequest.Domain(
                                        domainPhysical,
                                        List.of(new YearPlanRequest.Row(foreignId, "goal", "Demo", List.of())))),
                                List.of(theme("t-1", null))),
                        edit(vicePrincipal, created.id(), true)),
                ApiCode.INVALID_VALUE);
    }

    /** Kế hoạch năm cần tối thiểu 9 chủ đề liền nhau: thêm chủ đề phụ 1 tuần ngay sau chủ đề có ngày cuối cùng. */
    private static List<YearPlanRequest.Theme> withMinimumThemes(List<YearPlanRequest.Theme> themes) {
        var all = new java.util.ArrayList<>(themes);
        LocalDate first = themes.stream()
                .map(YearPlanRequest.Theme::endDate)
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder())
                .map(end -> end.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)))
                .orElse(LocalDate.of(2026, 9, 7));
        for (int i = 0; all.size() < 9; i++) {
            LocalDate monday = first.plusWeeks(i);
            all.add(new YearPlanRequest.Theme(
                    "px-" + i, null, "Chủ đề phụ " + i, "PX" + i, monday, monday.plusDays(4), "", ""));
        }
        return all;
    }

    /** Mục tiêu năm hợp lệ: thuộc nhóm kết quả và có nội dung. */
    private List<YearPlanRequest.Domain> annualDomains() {
        return List.of(
                new YearPlanRequest.Domain(
                        domainLanguage,
                        List.of(groupRow("Nghe hiểu"), goalRow("Trẻ nghe hiểu lời nói", "Nghe kể chuyện"))),
                new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(
                                subRow("Phát triển vận động"),
                                groupRow("Vận động cơ bản"),
                                goalRow("Trẻ thực hiện đủ động tác", "Hô hấp: hít vào, thở ra"))));
    }

    // Mẫu nhánh cần đủ ngày bắt đầu/kết thúc
    private static YearPlanRequest.BranchTemplate annualTemplate() {
        return new YearPlanRequest.BranchTemplate(
                "tmpl-1", null, "t-1", "Mẫu nhánh 1", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11));
    }

    /** Ban hành kế hoạch năm tự tạo bản nháp tổ (tổ trưởng soạn); tick nội dung vào chủ đề đầu để gửi duyệt được. */
    private YearPlanDetailResponse teamDraft(long ageGroupId, CurrentUser leader) {
        createApprovedAnnualPlan(ageGroupId);
        Long teamId = jdbc.queryForObject(
                "SELECT id FROM edu_team_plans WHERE school_year_id = ? AND age_group_id = ? ORDER BY version_no LIMIT 1",
                Long.class,
                schoolYear,
                ageGroupId);
        jdbc.update(
                "INSERT INTO edu_content_theme_ticks (content_id, theme_id) "
                        + "SELECT c.id, (SELECT id FROM edu_themes WHERE team_plan_id = ? ORDER BY sort_order LIMIT 1) "
                        + "FROM edu_goal_contents c WHERE c.goal_id IN (SELECT id FROM edu_plan_goals WHERE team_plan_id = ?) "
                        + "ON CONFLICT DO NOTHING",
                teamId,
                teamId);
        entityManager.clear();
        return service.getYearPlan(leader, teamId);
    }

    private static YearPlanRequest.Row groupRow(String text) {
        return new YearPlanRequest.Row(null, "group", text, List.of());
    }

    private static List<YearPlanRequest.Content> contentOf(String text) {
        return List.of(new YearPlanRequest.Content(null, text, List.of()));
    }

    private static List<YearPlanRequest.Content> keepContents(YearPlanDetailResponse.Row row) {
        return row.contents().stream().map(c -> new YearPlanRequest.Content(c.id(), c.text(), List.of())).toList();
    }

    private YearPlanDetailResponse.Row goalOf(YearPlanDetailResponse plan) {
        return domain(plan, domainPhysical).rows().stream()
                .filter(r -> r.kind().equals("goal"))
                .findFirst()
                .orElseThrow();
    }

    private YearPlanRequest sampleSchoolYearRequest(long ageGroupId) {
        String abbr = ageGroupId == ageGroup3 ? "TMN3" : "TMN4";
        return new YearPlanRequest(
                schoolYear,
                ageGroupId,
                "Định hướng năm học",
                "",
                annualDomains(),
                withMinimumThemes(List.of(datedTheme("t-1", abbr, 7, 25))),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of(annualTemplate()));
    }

    private YearPlanRequest requestSchoolYear(
            long ageGroupId, List<YearPlanRequest.Domain> domains, List<YearPlanRequest.Theme> themes) {
        return new YearPlanRequest(
                schoolYear,
                ageGroupId,
                "Định hướng năm học",
                "",
                domains,
                withMinimumThemes(themes),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of());
    }

    private YearPlanRequest sampleTeamRequest(long ageGroupId, Long sourcePlanId) {
        return new YearPlanRequest(
                schoolYear,
                ageGroupId,
                "Kế hoạch MGB",
                "",
                List.of(),
                List.of(),
                List.of(),
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                sourcePlanId,
                List.of());
    }

    private YearPlanRequest sampleRequest() {
        Long sourceId = createApprovedAnnualPlan(ageGroup3);
        return sampleTeamRequest(ageGroup3, sourceId);
    }

    private YearPlanRequest toUpdateRequest(YearPlanDetailResponse detail) {
        List<YearPlanRequest.Domain> updateDomains = detail.domains().stream()
                .map(d -> new YearPlanRequest.Domain(
                        d.domainId(),
                        d.rows().stream()
                                .map(r -> new YearPlanRequest.Row(
                                        r.id(),
                                        r.kind(),
                                        r.text(),
                                        r.contents().stream()
                                                .map(c -> new YearPlanRequest.Content(
                                                        c.id(),
                                                        c.text(),
                                                        c.themeIds().stream()
                                                                .map(String::valueOf)
                                                                .toList()))
                                                .toList(),
                                        r.curriculumGoalId(),
                                        r.curriculumSubDomainId(),
                                        r.curriculumExpectedResultId()))
                                .toList()))
                .toList();

        List<YearPlanRequest.Theme> updateThemes = detail.themes().stream()
                .map(t -> new YearPlanRequest.Theme(
                        String.valueOf(t.id()),
                        t.id(),
                        t.name(),
                        t.abbr(),
                        t.startDate(),
                        t.endDate(),
                        t.festivals(),
                        t.note()))
                .toList();

        List<YearPlanRequest.Break> updateBreaks = detail.breaks().stream()
                .map(b -> new YearPlanRequest.Break(b.id(), b.label(), b.startDate(), b.endDate()))
                .toList();

        List<YearPlanRequest.BranchTemplate> updateTemplates = detail.branchTemplates() == null
                ? List.of()
                : detail.branchTemplates().stream()
                        .map(bt -> new YearPlanRequest.BranchTemplate(
                                bt.id() != null ? String.valueOf(bt.id()) : null,
                                bt.id(),
                                String.valueOf(bt.themeId()),
                                bt.name(),
                                bt.startDate(),
                                bt.endDate()))
                        .toList();

        return new YearPlanRequest(
                detail.schoolYearId(),
                detail.ageGroupId(),
                detail.title(),
                detail.description(),
                updateDomains,
                updateThemes,
                updateBreaks,
                detail.planLevel(),
                detail.curriculumSourcePlanId(),
                updateTemplates);
    }

    private static YearPlanRequest.Theme theme(String key, Long id) {
        return new YearPlanRequest.Theme(
                key, id, "Trường mầm non", "TMN", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 25), "", "");
    }

    // Chủ đề trong tháng 9/2026: ngày 7, 14, 21, 28 là Thứ 2; 25 là Thứ 6
    private static YearPlanRequest.Theme datedTheme(String key, String abbr, int startDay, int endDay) {
        return new YearPlanRequest.Theme(
                key, null, abbr, abbr, LocalDate.of(2026, 9, startDay), LocalDate.of(2026, 9, endDay), "", "");
    }

    private YearPlanRequest.Domain physical(YearPlanRequest.Row... rows) {
        return new YearPlanRequest.Domain(domainPhysical, List.of(rows));
    }

    private static YearPlanRequest.Row subRow(String text) {
        return new YearPlanRequest.Row(null, "sub", text, List.of());
    }

    private static YearPlanRequest.Row goalRow(String text, String... contents) {
        return new YearPlanRequest.Row(
                null,
                "goal",
                text,
                java.util.Arrays.stream(contents)
                        .map(c -> new YearPlanRequest.Content(null, c, List.of()))
                        .toList());
    }

    private static YearPlanRequest.Theme namedTheme(String key, Long id, String name, String abbr) {
        return new YearPlanRequest.Theme(key, id, name, abbr, null, null, "", "");
    }

    private YearPlanRequest.Domain goalTickedTo(String themeKey, Long rowId, Long contentId) {
        return new YearPlanRequest.Domain(
                domainPhysical,
                List.of(new YearPlanRequest.Row(
                        rowId,
                        "goal",
                        "Trẻ thực hiện đủ động tác",
                        List.of(new YearPlanRequest.Content(contentId, "Hô hấp", List.of(themeKey))))));
    }

    private PageResponse<YearPlanSummaryResponse> list(CurrentUser user) {
        return service.getYearPlans(user, schoolYear, null, null, PageRequest.of(0, 20));
    }

    private static YearPlanDetailResponse.Domain domain(YearPlanDetailResponse plan, long domainId) {
        return plan.domains().stream()
                .filter(d -> d.domainId() == domainId)
                .findFirst()
                .orElseThrow();
    }

    // Mã lỗi nghiệp vụ chính xác (ERROR_CONTRACT), không chỉ HTTP status
    private static void assertErrorCode(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode()).isEqualTo(code));
    }

    private static void assertApiError(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode().getHttpStatus())
                        .isEqualTo(code.getHttpStatus()));
    }

    @Test
    @DisplayName("T027 [US2]: người có vai trò trong creators được tạo; reviewer bị từ chối quyền tác giả (precedence)")
    void creatorRoleScopeEnforcedAndReviewerPrecedence() {
        // Mặc định policy year: creators=[TEAM_LEADER], reviewers=[VICE_PRINCIPAL]
        YearPlanDetailResponse created = teamDraft(ageGroup3, leader3);
        assertThat(created.id()).isNotNull();

        Long annual4 = createApprovedAnnualPlan(ageGroup4);
        // teacher3 không có role trong creators (chỉ là GV) -> bị cấm
        assertApiError(
                () -> service.createYearPlan(
                        teacher3,
                        sampleTeamRequest(ageGroup4, annual4),
                        new YearPlanEditRequest(session(teacher3), 0L, UUID.randomUUID())),
                ApiCode.FORBIDDEN);

        // vicePrincipal có vai trò trong reviewers -> reviewer-precedence cấm làm tác giả
        assertApiError(
                () -> service.createYearPlan(
                        vicePrincipal,
                        sampleTeamRequest(ageGroup4, annual4),
                        new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID())),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("T027 [US2]: 3 trạng thái DRAFT -> REVIEW -> cấm sửa nội dung & cấm lấy author lease khi REVIEW")
    void threeStateTransitionsAndImmutabilityInReview() {
        YearPlanDetailResponse draft = teamDraft(ageGroup3, leader3);
        assertThat(draft.status()).isEqualTo(EduPlanStatus.DRAFT);

        YearPlanDetailResponse reviewed =
                service.submitYearPlan(leader3, draft.id(), "Gửi duyệt", edit(leader3, draft.id(), true));
        assertThat(reviewed.status()).isEqualTo(EduPlanStatus.REVIEW);

        // Khi ở REVIEW: tác giả không được lấy author lease (startEditing)
        assertApiError(
                () -> service.startEditing(
                        leader3,
                        draft.id(),
                        new YearPlanEditRequest(UUID.randomUUID(), reviewed.revision(), UUID.randomUUID())),
                ApiCode.CONFLICT);

        // Khi ở REVIEW: tác giả không được sửa nội dung (updateYearPlan)
        assertApiError(
                () -> service.updateYearPlan(
                        leader3,
                        draft.id(),
                        sampleRequest(),
                        new YearPlanEditRequest(session(leader3), reviewed.revision(), UUID.randomUUID())),
                ApiCode.CONFLICT);

        // Khi ở REVIEW: reviewer cũng không được sửa nội dung (updateYearPlan)
        assertApiError(
                () -> service.updateYearPlan(
                        vicePrincipal,
                        draft.id(),
                        sampleRequest(),
                        new YearPlanEditRequest(session(vicePrincipal), reviewed.revision(), UUID.randomUUID())),
                ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("T027 [US2]: chuỗi duyệt nhiều bước: bước trung gian giữ REVIEW, bước cuối sang APPROVED + snapshot")
    void intermediateReviewAdvanceVsFinalApproveWithSnapshot() {
        // Cấu hình chuỗi 2 bước duyệt cho year: TEAM_LEADER -> [VICE_PRINCIPAL, PRINCIPAL]
        jdbc.update("UPDATE edu_settings SET workflow_policy = "
                + "'{\"year\":{\"creators\":[\"TEAM_LEADER\"],\"reviewers\":[\"VICE_PRINCIPAL\",\"PRINCIPAL\"]},"
                + "\"branch\":{\"creators\":[\"TEACHER\"],\"reviewers\":[\"TEAM_LEADER\",\"VICE_PRINCIPAL\"]},"
                + "\"theme\":{\"creators\":[\"TEACHER\"],\"reviewers\":[\"TEAM_LEADER\",\"VICE_PRINCIPAL\"]}}'::jsonb, "
                + "workflow_revision = workflow_revision + 1");
        entityManager.clear();

        YearPlanDetailResponse draft = teamDraft(ageGroup3, leader3);
        YearPlanDetailResponse underReview =
                service.submitYearPlan(leader3, draft.id(), "Xin duyệt", edit(leader3, draft.id(), true));

        assertThat(underReview.status()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(underReview.review()).isNotNull();
        assertThat(underReview.review().waitingRole()).isEqualTo("VICE_PRINCIPAL");

        // PRINCIPAL chưa tới lượt: không mở được bản Chờ duyệt (EDU-73(2)(3) ⇒ 7384)
        assertErrorCode(
                () -> service.issueYearPlan(
                        principal,
                        draft.id(),
                        "HT duyệt sớm",
                        new YearPlanEditRequest(null, underReview.revision(), UUID.randomUUID())),
                ApiCode.EDU_PLAN_NOT_RELEASED);

        // VICE_PRINCIPAL duyệt bước 1: vẫn giữ REVIEW, chuyển waitingRole sang PRINCIPAL
        YearPlanDetailResponse advanced = service.issueYearPlan(
                vicePrincipal,
                draft.id(),
                "PHT duyệt vòng 1",
                new YearPlanEditRequest(null, underReview.revision(), UUID.randomUUID()));
        assertThat(advanced.status()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(advanced.review().waitingRole()).isEqualTo("PRINCIPAL");
        assertThat(advanced.review().approvals()).hasSize(1);
        assertThat(advanced.review().approvals().getFirst().roleKey()).isEqualTo("VICE_PRINCIPAL");

        // PRINCIPAL duyệt bước 2 (cuối): chuyển sang APPROVED + tạo printSnapshot
        YearPlanDetailResponse approved = service.issueYearPlan(
                principal,
                draft.id(),
                "HT duyệt ban hành",
                new YearPlanEditRequest(null, advanced.revision(), UUID.randomUUID()));
        assertThat(approved.status()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(approved.review().waitingRole()).isNull();
        assertThat(approved.review().printSnapshot()).isNotNull();
    }

    @Test
    @DisplayName("T027 [US2]: review actions (issue/return) không bắt buộc X-Edit-Session")
    void reviewActionsDoNotRequireEditSession() {
        YearPlanDetailResponse draft = teamDraft(ageGroup3, leader3);
        YearPlanDetailResponse reviewed =
                service.submitYearPlan(leader3, draft.id(), "Gửi duyệt", edit(leader3, draft.id(), true));

        // issue không cần edit session
        YearPlanDetailResponse issued = service.issueYearPlan(
                vicePrincipal,
                draft.id(),
                "Ban hành",
                new YearPlanEditRequest(null, reviewed.revision(), UUID.randomUUID()));
        assertThat(issued.status()).isEqualTo(EduPlanStatus.APPROVED);
    }

    @Test
    @DisplayName("T027 [US2]: returnYearPlan trả về DRAFT kèm note; cấm return nếu thiếu note")
    void returnYearPlanRevertsToDraftWithReason() {
        YearPlanDetailResponse draft = teamDraft(ageGroup3, leader3);
        YearPlanDetailResponse reviewed =
                service.submitYearPlan(leader3, draft.id(), "Gửi duyệt", edit(leader3, draft.id(), true));

        // Thiếu note bị từ chối
        assertErrorCode(
                () -> service.returnYearPlan(
                        vicePrincipal,
                        draft.id(),
                        "   ",
                        new YearPlanEditRequest(null, reviewed.revision(), UUID.randomUUID())),
                ApiCode.RETURN_REASON_REQUIRED);

        // Trả lại kèm note -> status về DRAFT
        YearPlanDetailResponse returned = service.returnYearPlan(
                vicePrincipal,
                draft.id(),
                "Cần bổ sung tiêu đề cụ thể",
                new YearPlanEditRequest(null, reviewed.revision(), UUID.randomUUID()));
        assertThat(returned.status()).isEqualTo(EduPlanStatus.DRAFT);
        assertThat(returned.lastReturnNote()).isEqualTo("Cần bổ sung tiêu đề cụ thể");

        // Sau khi về DRAFT, tác giả có thể sửa lại
        assertThat(service.getYearPlan(leader3, draft.id()).permissions().canEdit())
                .isTrue();
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                "kc-" + username,
                username,
                "Demo " + username);
        return new CurrentUser(id, "kc-" + username, username, "Demo " + username, null, Set.of(role));
    }

    @Test
    @DisplayName("T014 [US1]: lifecycle của SCHOOL_YEAR: PHT tạo, HT duyệt, không nhận tick, version mới giữ ROOT")
    void schoolYearPlanLifecycleAndPublication() {
        // Tổ trưởng không được tạo kế hoạch cấp SCHOOL_YEAR
        YearPlanRequest annualReq = sampleSchoolYearRequest(ageGroup3);
        assertApiError(
                () -> service.createYearPlan(
                        leader3, annualReq, new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID())),
                ApiCode.FORBIDDEN);

        // Phó HT tạo thành công cấp SCHOOL_YEAR
        YearPlanDetailResponse draft = service.createYearPlan(
                vicePrincipal, annualReq, new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));
        assertThat(draft.planLevel()).isEqualTo(com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR);
        assertThat(draft.curriculumSourcePlanId()).isNull();
        assertThat(draft.code()).startsWith("KHN-");
        assertThat(draft.canEditCurriculum()).isTrue();
        assertThat(draft.canAssignContents()).isFalse();
        assertThat(draft.canEditBranchTemplates()).isTrue();
        assertThat(draft.branchTemplates()).isNotEmpty();

        // Danh sách lọc theo level
        PageResponse<YearPlanSummaryResponse> annualList = service.getYearPlans(
                vicePrincipal,
                schoolYear,
                ageGroup3,
                null,
                PageRequest.of(0, 10),
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR);
        assertThat(annualList.items()).extracting(YearPlanSummaryResponse::id).contains(draft.id());

        // Chuỗi duyệt mặc định PHT -> HT
        YearPlanDetailResponse submitted = service.submitYearPlan(
                vicePrincipal, draft.id(), "Xin duyệt định hướng", edit(vicePrincipal, draft.id(), true));
        assertThat(submitted.status()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(submitted.review().waitingRole()).isEqualTo("PRINCIPAL");

        // PHT không tự duyệt được kế hoạch của mình
        assertErrorCode(
                () -> service.issueYearPlan(
                        vicePrincipal,
                        draft.id(),
                        "PHT tự duyệt",
                        new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID())),
                ApiCode.NOT_YOUR_REVIEW_STEP);

        // HT duyệt ban hành
        YearPlanDetailResponse approved = service.issueYearPlan(
                principal,
                draft.id(),
                "HT duyệt",
                new YearPlanEditRequest(null, submitted.revision(), UUID.randomUUID()));
        assertThat(approved.status()).isEqualTo(EduPlanStatus.APPROVED);

        // Tạo phiên bản điều chỉnh mới từ bản ban hành
        YearPlanDetailResponse v2 = service.createVersion(
                vicePrincipal,
                approved.id(),
                new YearPlanEditRequest(UUID.randomUUID(), approved.revision(), UUID.randomUUID()));
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(v2.status()).isEqualTo(EduPlanStatus.DRAFT);
        assertThat(v2.planLevel()).isEqualTo(com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR);
        assertThat(v2.curriculumSourcePlanId()).isNull();
    }

    private void insertLeader(CurrentUser user, long ageGroupId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) "
                        + "VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, '2026-09-01')",
                user.userId(),
                ageGroupId);
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
