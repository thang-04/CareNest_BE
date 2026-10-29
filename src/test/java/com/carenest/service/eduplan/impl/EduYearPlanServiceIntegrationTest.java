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

/** Luồng kế hoạch năm học SD-01..SD-08 qua service + policy thật trên PostgreSQL. Mỗi test rollback. Cần Docker. */
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

    @Test
    @DisplayName("EDU-10: cùng tài khoản mở lại (vd. sau mất điện) thì phiên mới nhận quyền ngay; phiên cũ mất quyền")
    void sameAccountNewSessionTakesOverAndOldSessionLosesLease() {
        YearPlanDetailResponse created = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        YearPlanEditRequest oldTab = new YearPlanEditRequest(session(leader3), created.revision(), UUID.randomUUID());
        UUID newSession = UUID.randomUUID();
        YearPlanDetailResponse taken = service.startEditing(
                leader3, created.id(), new YearPlanEditRequest(newSession, created.revision(), UUID.randomUUID()));
        assertThat(taken.editing().userId()).isEqualTo(leader3.userId());

        assertErrorCode(() -> service.heartbeat(leader3, created.id(), oldTab), ApiCode.EDIT_SESSION_LOST);
        assertErrorCode(
                () -> service.updateYearPlan(leader3, created.id(), sampleRequest(), oldTab),
                ApiCode.EDIT_SESSION_LOST);
        // Phiên cũ thoát trang không nhả được quyền của phiên mới
        assertThat(service.finishEditing(leader3, created.id(), oldTab).editing())
                .isNotNull();

        YearPlanEditRequest write = new YearPlanEditRequest(newSession, created.revision(), UUID.randomUUID());
        YearPlanDetailResponse saved = service.updateYearPlan(leader3, created.id(), sampleRequest(), write);
        YearPlanDetailResponse retried = service.updateYearPlan(leader3, created.id(), sampleRequest(), write);
        assertThat(retried.revision()).isEqualTo(saved.revision()).isEqualTo(created.revision() + 1);
        YearPlanEditRequest stale = new YearPlanEditRequest(newSession, created.revision(), UUID.randomUUID());
        assertErrorCode(
                () -> service.updateYearPlan(leader3, created.id(), sampleRequest(), stale),
                ApiCode.YEAR_PLAN_REVISION_STALE);
    }

    @Test
    @DisplayName("EDU-10: người khác vẫn phải chờ khóa hết hạn; thông báo nêu tên người đang sửa")
    void otherUserStillWaitsForLeaseExpiry() {
        YearPlanDetailResponse created = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        assertThatThrownBy(() -> service.startEditing(
                        vicePrincipal,
                        created.id(),
                        new YearPlanEditRequest(UUID.randomUUID(), created.revision(), UUID.randomUUID())))
                .isInstanceOf(GlobalException.class)
                .hasMessage("Đang được Demo leader3 sửa");
    }

    @Test
    @DisplayName("EDU-10: quyền hết sau 90 giây; yêu cầu cũ không nhả khóa mới")
    void expiredLeaseCannotOverwriteOrReleaseNewSession() {
        YearPlanDetailResponse created = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        jdbc.update(
                "UPDATE edu_year_plans SET editing_at = editing_at - interval '91 seconds' WHERE id = ?", created.id());
        entityManager.clear();
        YearPlanEditRequest old = new YearPlanEditRequest(session(leader3), created.revision(), UUID.randomUUID());
        assertErrorCode(() -> service.heartbeat(leader3, created.id(), old), ApiCode.EDIT_SESSION_LOST);
        YearPlanEditRequest next =
                new YearPlanEditRequest(session(vicePrincipal), created.revision(), UUID.randomUUID());
        service.startEditing(vicePrincipal, created.id(), next);
        assertThat(service.finishEditing(leader3, created.id(), old).editing().userId())
                .isEqualTo(vicePrincipal.userId());
        assertErrorCode(
                () -> service.updateYearPlan(leader3, created.id(), sampleRequest(), old),
                ApiCode.YEAR_PLAN_LOCKED_BY_OTHER);
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
                List.of());
        YearPlanDetailResponse created = service.createYearPlan(
                leader3, draft, new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        assertThat(created.title()).isEmpty();
        assertErrorCode(
                () -> service.submitYearPlan(leader3, created.id(), null, edit(leader3, created.id(), true)),
                ApiCode.YEAR_PLAN_TITLE_REQUIRED);
        YearPlanRequest titled =
                new YearPlanRequest(schoolYear, ageGroup3, "Title", "", draft.domains(), List.of(), List.of());
        service.updateYearPlan(leader3, created.id(), titled, edit(leader3, created.id(), true));
        assertErrorCode(
                () -> service.submitYearPlan(leader3, created.id(), null, edit(leader3, created.id(), true)),
                ApiCode.PLAN_GOAL_REQUIRED);
    }

    private final Map<Long, UUID> sessions = new HashMap<>();

    @Test
    @DisplayName("EDU-22: tạo đã lưu nhưng mất phản hồi được thử lại cùng request mà không tạo trùng")
    void retryFirstCreateReturnsTheSamePlan() {
        YearPlanEditRequest create = new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID());
        YearPlanDetailResponse first = service.createYearPlan(leader3, sampleRequest(), create);
        YearPlanDetailResponse again = service.createYearPlan(leader3, sampleRequest(), create);
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
    }

    @Test
    @DisplayName("EDU-01: kế hoạch thứ hai cho cùng (năm học, nhóm tuổi) bị 409")
    void secondPlanForSameAgeGroupIsConflict() {
        service.createYearPlan(
                leader3,
                request(ageGroup3, List.of(), List.of()),
                new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));

        assertApiError(
                () -> service.createYearPlan(
                        vicePrincipal,
                        request(ageGroup3, List.of(), List.of()),
                        new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID())),
                ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("EDU-02: Tổ trưởng không lập được kế hoạch cho nhóm tuổi khác")
    void teamLeaderCannotCreateForOtherAgeGroup() {
        assertApiError(
                () -> service.createYearPlan(
                        leader3,
                        request(ageGroup4, List.of(), List.of()),
                        new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID())),
                ApiCode.FORBIDDEN);
        assertApiError(
                () -> service.createYearPlan(
                        teacher3,
                        request(ageGroup3, List.of(), List.of()),
                        new YearPlanEditRequest(session(teacher3), 0L, UUID.randomUUID())),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("EDU-09: lưu cây mục tiêu, đánh số MT theo thứ tự lĩnh vực, tích chủ đề theo key")
    void savesGoalTreeAndNumbersGoalsAcrossDomains() {
        YearPlanDetailResponse created = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));

        assertThat(created.code()).isEqualTo("KHNH-2627-MGB");
        assertThat(created.status()).isEqualTo(EduPlanStatus.EDITING);
        YearPlanDetailResponse.Domain physical = domain(created, domainPhysical);
        YearPlanDetailResponse.Domain language = domain(created, domainLanguage);
        // Request gửi lĩnh vực ngôn ngữ trước nhưng MT đánh theo thứ tự lĩnh vực đã cấu hình
        assertThat(physical.rows()).extracting(YearPlanDetailResponse.Row::kind).containsExactly("sub", "goal");
        assertThat(physical.rows().get(1).goalNo()).isEqualTo((short) 1);
        assertThat(language.rows().get(0).goalNo()).isEqualTo((short) 2);
        Long themeId = created.themes().get(0).id();
        assertThat(physical.rows().get(1).contents())
                .singleElement()
                .satisfies(content -> assertThat(content.themeIds()).containsExactly(themeId));

        // Lần tự lưu sau gửi id đã cấp: dòng giữ id, nội dung bị bỏ thì xóa
        YearPlanDetailResponse.Row goal = physical.rows().get(1);
        YearPlanRequest edit = new YearPlanRequest(
                null,
                null,
                "Kế hoạch MGB",
                "",
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(new YearPlanRequest.Row(goal.id(), "goal", "Trẻ khỏe mạnh", List.of())))),
                List.of(theme(String.valueOf(themeId), themeId)),
                List.of());
        YearPlanDetailResponse updated =
                service.updateYearPlan(leader3, created.id(), edit, edit(leader3, created.id(), true));

        assertThat(domain(updated, domainPhysical).rows()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(goal.id());
            assertThat(row.contents()).isEmpty();
            assertThat(row.goalNo()).isEqualTo((short) 1);
        });
        assertThat(domain(updated, domainLanguage).rows()).isEmpty();
    }

    @Test
    @DisplayName("EDU-09: đổi thứ tự hoặc xóa chủ đề, đợt nghỉ thì lưu theo thứ tự gửi lên, giữ id và dấu tích")
    void reordersThemesAndBreaksKeepingIdsAndTicks() {
        YearPlanDetailResponse created = service.createYearPlan(
                leader3,
                new YearPlanRequest(
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
                                new YearPlanRequest.Break(null, "Ôn tập", null, null))),
                new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        List<Long> themeIds =
                created.themes().stream().map(YearPlanDetailResponse.Theme::id).toList();
        List<Long> breakIds =
                created.breaks().stream().map(YearPlanDetailResponse.Break::id).toList();
        YearPlanDetailResponse.Row goal = domain(created, domainPhysical).rows().get(0);
        Long contentId = goal.contents().get(0).id();

        // Chủ đề "Gia đình" chuyển lên đầu, đợt nghỉ đảo chỗ; dấu tích gửi theo key là id cũ
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
    @DisplayName("EDU-03, EDU-04, EDU-05: gửi, Phó HT sửa khi chờ duyệt, ban hành rồi khóa")
    void submitEditIssueFlow() {
        YearPlanDetailResponse plan = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));

        YearPlanDetailResponse sent = service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));
        assertThat(sent.status()).isEqualTo(EduPlanStatus.PENDING_VP);
        assertThat(sent.sentBy().userId()).isEqualTo(leader3.userId());
        // Đã gửi thì Tổ trưởng không sửa được, Phó HT thì được (ghi lịch sử 1 lần)
        assertApiError(
                () -> service.updateYearPlan(leader3, plan.id(), sampleRequest(), edit(leader3, plan.id(), true)),
                ApiCode.FORBIDDEN);
        service.updateYearPlan(vicePrincipal, plan.id(), sampleRequest(), edit(vicePrincipal, plan.id(), true));
        YearPlanDetailResponse editedTwice =
                service.updateYearPlan(vicePrincipal, plan.id(), sampleRequest(), edit(vicePrincipal, plan.id(), true));
        assertThat(editedTwice.history())
                .extracting(YearPlanDetailResponse.History::action)
                .containsExactly("SUBMIT", "VP_EDIT");

        YearPlanDetailResponse issued =
                service.issueYearPlan(vicePrincipal, plan.id(), "Đạt", edit(vicePrincipal, plan.id(), true));
        assertThat(issued.status()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(issued.issued().userId()).isEqualTo(vicePrincipal.userId());
        assertThat(issued.issued().at()).isNotNull();
        assertApiError(
                () -> service.updateYearPlan(
                        vicePrincipal, plan.id(), sampleRequest(), edit(vicePrincipal, plan.id(), true)),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("EDU-04: kế hoạch chưa có MT không gửi được")
    void cannotSubmitWithoutGoals() {
        YearPlanDetailResponse plan = service.createYearPlan(
                leader3,
                request(ageGroup3, List.of(), List.of()),
                new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));

        assertApiError(
                () -> service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true)),
                ApiCode.PLAN_GOAL_REQUIRED);
    }

    @Test
    @DisplayName("EDU-05: trả lại cần lý do, chỉ khi chờ duyệt; Tổ trưởng thấy lý do")
    void returnRequiresPendingAndReason() {
        YearPlanDetailResponse plan = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        assertApiError(
                () -> service.returnYearPlan(vicePrincipal, plan.id(), "Sửa MT", edit(vicePrincipal, plan.id(), true)),
                ApiCode.CONFLICT);
        service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));
        assertApiError(
                () -> service.returnYearPlan(vicePrincipal, plan.id(), " ", edit(vicePrincipal, plan.id(), true)),
                ApiCode.BAD_REQUEST);
        assertApiError(
                () -> service.returnYearPlan(leader3, plan.id(), "Sửa MT", edit(leader3, plan.id(), true)),
                ApiCode.FORBIDDEN);

        service.returnYearPlan(vicePrincipal, plan.id(), "Sửa MT 2", edit(vicePrincipal, plan.id(), true));
        YearPlanDetailResponse seenByLeader = service.getYearPlan(leader3, plan.id());

        assertThat(seenByLeader.status()).isEqualTo(EduPlanStatus.REJECTED);
        assertThat(seenByLeader.lastReturnNote()).isEqualTo("Sửa MT 2");
        assertThat(seenByLeader.permissions().canSubmit()).isTrue();
        assertThat(seenByLeader.permissions().canDelete()).isFalse();
    }

    @Test
    @DisplayName("EDU-05: Phó HT tự soạn thì ban hành thẳng, Tổ trưởng không ban hành được")
    void vicePrincipalIssuesOwnDraftDirectly() {
        YearPlanDetailResponse plan = service.createYearPlan(
                vicePrincipal, sampleRequest(), new YearPlanEditRequest(session(vicePrincipal), 0L, UUID.randomUUID()));

        assertApiError(
                () -> service.issueYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true)),
                ApiCode.FORBIDDEN);
        assertThat(service.issueYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true))
                        .status())
                .isEqualTo(EduPlanStatus.APPROVED);
    }

    @Test
    @DisplayName("EDU-06: GV chỉ xem bản đã ban hành; HT không xem nháp; TT khác khối không xem")
    void viewScopeByRole() {
        YearPlanDetailResponse plan = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));

        assertApiError(() -> service.getYearPlan(teacher3, plan.id()), ApiCode.FORBIDDEN);
        assertApiError(() -> service.getYearPlan(principal, plan.id()), ApiCode.FORBIDDEN);
        assertApiError(() -> service.getYearPlan(leader4, plan.id()), ApiCode.FORBIDDEN);
        assertThat(list(teacher3).items()).isEmpty();
        assertThat(list(vicePrincipal).items())
                .extracting(YearPlanSummaryResponse::id)
                .containsExactly(plan.id());

        service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));
        service.issueYearPlan(vicePrincipal, plan.id(), null, edit(vicePrincipal, plan.id(), true));

        assertThat(service.getYearPlan(teacher3, plan.id()).permissions().canEdit())
                .isFalse();
        PageResponse<YearPlanSummaryResponse> teacherList = list(teacher3);
        assertThat(teacherList.items()).singleElement().satisfies(summary -> {
            assertThat(summary.goalCount()).isEqualTo(2);
            assertThat(summary.themeCount()).isEqualTo(1);
            assertThat(summary.owner().name()).isEqualTo("Demo leader3");
        });
    }

    @Test
    @DisplayName("EDU-07: chỉ Phó HT xóa, chỉ bản nháp không bị người khác khóa")
    void onlyVicePrincipalDeletesDraft() {
        YearPlanDetailResponse plan = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        assertApiError(
                () -> service.deleteYearPlan(leader3, plan.id(), edit(leader3, plan.id(), true)), ApiCode.FORBIDDEN);
        // Tổ trưởng đang sửa thì Phó HT chưa xóa được
        assertApiError(
                () -> service.deleteYearPlan(vicePrincipal, plan.id(), edit(vicePrincipal, plan.id(), true)),
                ApiCode.CONFLICT);

        service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));
        service.returnYearPlan(vicePrincipal, plan.id(), "Sửa", edit(vicePrincipal, plan.id(), true));
        assertApiError(
                () -> service.deleteYearPlan(vicePrincipal, plan.id(), edit(vicePrincipal, plan.id(), true)),
                ApiCode.CONFLICT);

        YearPlanDetailResponse other = service.createYearPlan(
                leader4,
                request(ageGroup4, List.of(), List.of()),
                new YearPlanEditRequest(session(leader4), 0L, UUID.randomUUID()));
        service.finishEditing(leader4, other.id(), edit(leader4, other.id(), false));
        assertThat(service.getYearPlan(vicePrincipal, other.id()).permissions().canDelete())
                .isTrue();
        service.deleteYearPlan(vicePrincipal, other.id(), edit(vicePrincipal, other.id(), true));
        assertApiError(() -> service.getYearPlan(vicePrincipal, other.id()), ApiCode.NOT_FOUND);
    }

    @Test
    @DisplayName("EDU-10: người đang sửa giữ khóa; lưu vẫn Đang sửa; thoát trang mới về Nháp, người kia sửa tiếp")
    void editingLockIsHeldUntilEditorLeaves() {
        YearPlanDetailResponse created = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        assertThat(created.status()).isEqualTo(EduPlanStatus.EDITING);
        assertThat(created.editing().userId()).isEqualTo(leader3.userId());

        YearPlanDetailResponse seenByVp = service.getYearPlan(vicePrincipal, created.id());
        assertThat(seenByVp.editing().name()).isEqualTo("Demo leader3");
        assertThat(seenByVp.permissions().canEdit()).isFalse();
        assertApiError(
                () -> service.startEditing(vicePrincipal, created.id(), edit(vicePrincipal, created.id(), false)),
                ApiCode.CONFLICT);
        assertApiError(
                () -> service.updateYearPlan(
                        vicePrincipal, created.id(), sampleRequest(), edit(vicePrincipal, created.id(), true)),
                ApiCode.CONFLICT);

        assertThat(service.updateYearPlan(leader3, created.id(), sampleRequest(), edit(leader3, created.id(), true))
                        .status())
                .isEqualTo(EduPlanStatus.EDITING);
        YearPlanDetailResponse released =
                service.finishEditing(leader3, created.id(), edit(leader3, created.id(), false));
        assertThat(released.status()).isEqualTo(EduPlanStatus.DRAFT);
        assertThat(released.editing()).isNull();

        // Phó HT cùng soạn bản của Tổ trưởng; lúc đó Tổ trưởng không gửi được
        assertThat(service.startEditing(vicePrincipal, created.id(), edit(vicePrincipal, created.id(), false))
                        .status())
                .isEqualTo(EduPlanStatus.EDITING);
        assertThat(service.getYearPlan(leader3, created.id()).permissions().canSubmit())
                .isFalse();
        assertApiError(
                () -> service.submitYearPlan(leader3, created.id(), null, edit(leader3, created.id(), true)),
                ApiCode.CONFLICT);
        // Thoát lại khi không giữ khóa thì không làm gì
        assertThat(service.finishEditing(leader3, created.id(), edit(leader3, created.id(), false))
                        .status())
                .isEqualTo(EduPlanStatus.EDITING);
        service.finishEditing(vicePrincipal, created.id(), edit(vicePrincipal, created.id(), false));

        assertThat(service.submitYearPlan(leader3, created.id(), null, edit(leader3, created.id(), true))
                        .status())
                .isEqualTo(EduPlanStatus.PENDING_VP);
        // Chờ duyệt: Phó HT sửa thẳng, không khóa
        assertThat(service.startEditing(vicePrincipal, created.id(), edit(vicePrincipal, created.id(), false))
                        .status())
                .isEqualTo(EduPlanStatus.PENDING_VP);
        assertApiError(
                () -> service.startEditing(leader3, created.id(), edit(leader3, created.id(), false)),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("EDU-10: bị trả lại thì cả hai sửa được và thoát trang vẫn giữ Bị trả lại")
    void rejectedPlanEditableByBothAndKeepsStatus() {
        YearPlanDetailResponse plan = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true));
        service.returnYearPlan(vicePrincipal, plan.id(), "Sửa MT", edit(vicePrincipal, plan.id(), true));

        YearPlanDetailResponse editing =
                service.startEditing(vicePrincipal, plan.id(), edit(vicePrincipal, plan.id(), false));
        assertThat(editing.status()).isEqualTo(EduPlanStatus.EDITING);
        assertThat(editing.lastReturnNote()).isEqualTo("Sửa MT");
        assertThat(service.finishEditing(vicePrincipal, plan.id(), edit(vicePrincipal, plan.id(), false))
                        .status())
                .isEqualTo(EduPlanStatus.REJECTED);

        assertThat(service.startEditing(leader3, plan.id(), edit(leader3, plan.id(), false))
                        .status())
                .isEqualTo(EduPlanStatus.EDITING);
        assertThat(service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true))
                        .status())
                .isEqualTo(EduPlanStatus.PENDING_VP);
    }

    @Test
    @DisplayName("EDU-10: khóa quá 30 phút không lưu thì người khác lấy được, job trả về trạng thái trước")
    void expiredEditLockIsReleased() {
        YearPlanDetailResponse plan = service.createYearPlan(
                leader3, sampleRequest(), new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        YearPlanDetailResponse other = service.createYearPlan(
                leader4,
                request(ageGroup4, List.of(), List.of()),
                new YearPlanEditRequest(session(leader4), 0L, UUID.randomUUID()));
        entityManager.flush();
        jdbc.update("UPDATE edu_year_plans SET editing_at = editing_at - INTERVAL '30 minutes'");
        entityManager.clear();

        assertThat(service.getYearPlan(vicePrincipal, plan.id()).permissions().canEdit())
                .isTrue();
        YearPlanDetailResponse takenOver =
                service.startEditing(vicePrincipal, plan.id(), edit(vicePrincipal, plan.id(), false));
        assertThat(takenOver.editing().userId()).isEqualTo(vicePrincipal.userId());

        assertThat(service.releaseExpiredEditLocks()).isEqualTo(1);
        assertThat(service.getYearPlan(vicePrincipal, other.id()).status()).isEqualTo(EduPlanStatus.DRAFT);
        assertThat(service.getYearPlan(vicePrincipal, plan.id()).status()).isEqualTo(EduPlanStatus.EDITING);
    }

    @Test
    @DisplayName("EDU-23: chủ đề, đợt nghỉ có ngày ngoài năm học thì không lưu được")
    void datesOutsideSchoolYearAreRejected() {
        YearPlanRequest.Theme early = new YearPlanRequest.Theme(
                "t-1", null, "Trường mầm non", "TMN", LocalDate.of(2026, 8, 24), LocalDate.of(2026, 9, 4), "", "");
        YearPlanDetailResponse plan = service.createYearPlan(
                leader3,
                request(ageGroup3, List.of(), List.of()),
                new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));
        YearPlanRequest lateBreak = new YearPlanRequest(
                schoolYear,
                ageGroup3,
                "Kế hoạch MGB",
                "",
                List.of(),
                List.of(),
                List.of(new YearPlanRequest.Break(
                        null, "Nghỉ hè", LocalDate.of(2027, 5, 31), LocalDate.of(2027, 6, 5))));
        service.updateYearPlan(leader3, plan.id(), lateBreak, edit(leader3, plan.id(), true));
        assertApiError(
                () -> service.submitYearPlan(leader3, plan.id(), null, edit(leader3, plan.id(), true)),
                ApiCode.BAD_REQUEST);
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
        YearPlanRequest linked = request(
                ageGroup3,
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(new YearPlanRequest.Row(null, "goal", ministryText, List.of(), ministryGoal)))),
                List.of());
        YearPlanDetailResponse created = service.createYearPlan(
                leader3, linked, new YearPlanEditRequest(session(leader3), 0L, UUID.randomUUID()));

        YearPlanDetailResponse.Row row = domain(created, domainPhysical).rows().getFirst();
        assertThat(row.curriculumGoalId()).isEqualTo(ministryGoal);
        assertThat(row.curriculumGoalText()).isEqualTo("Giữ được thăng bằng cơ thể khi thực hiện vận động");
        assertThat(row.curriculumSectionRef()).isEqualTo("Phần ba › D › I › a) › 2 › 2.1");

        Long otherAgeGoal = jdbc.queryForObject(
                "SELECT g.id FROM edu_curriculum_goals g JOIN age_groups a ON a.id = g.age_group_id "
                        + "WHERE a.code = 'ag-5' ORDER BY g.sort_order LIMIT 1",
                Long.class);
        YearPlanRequest wrongAge = request(
                ageGroup3,
                List.of(new YearPlanRequest.Domain(
                        domainPhysical,
                        List.of(new YearPlanRequest.Row(row.id(), "goal", ministryText, List.of(), otherAgeGoal)))),
                List.of());
        assertErrorCode(
                () -> service.updateYearPlan(leader3, created.id(), wrongAge, edit(leader3, created.id(), true)),
                ApiCode.CURRICULUM_GOAL_INVALID);

        // Phiên bản điều chỉnh (EDU-25) chép cả liên kết nguồn của Bộ
        service.submitYearPlan(leader3, created.id(), null, edit(leader3, created.id(), true));
        service.issueYearPlan(vicePrincipal, created.id(), "Đạt", edit(vicePrincipal, created.id(), true));
        YearPlanDetailResponse v2 =
                service.createVersion(vicePrincipal, created.id(), edit(vicePrincipal, created.id(), false));
        YearPlanDetailResponse.Row copied = domain(v2, domainPhysical).rows().getFirst();
        assertThat(copied.curriculumGoalId()).isEqualTo(ministryGoal);
        assertThat(copied.curriculumSectionRef()).isEqualTo("Phần ba › D › I › a) › 2 › 2.1");
    }

    private YearPlanRequest sampleRequest() {
        return request(
                ageGroup3,
                List.of(
                        new YearPlanRequest.Domain(
                                domainLanguage,
                                List.of(new YearPlanRequest.Row(null, "goal", "Trẻ nghe hiểu lời nói", List.of()))),
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
                List.of(theme("t-1", null)));
    }

    private YearPlanRequest request(
            long ageGroupId, List<YearPlanRequest.Domain> domains, List<YearPlanRequest.Theme> themes) {
        return new YearPlanRequest(schoolYear, ageGroupId, "Kế hoạch MGB", "", domains, themes, List.of());
    }

    private static YearPlanRequest.Theme theme(String key, Long id) {
        return new YearPlanRequest.Theme(
                key, id, "Trường mầm non", "TMN", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 25), "", "");
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

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                "kc-" + username,
                username,
                "Demo " + username);
        return new CurrentUser(id, "kc-" + username, username, "Demo " + username, null, Set.of(role));
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
