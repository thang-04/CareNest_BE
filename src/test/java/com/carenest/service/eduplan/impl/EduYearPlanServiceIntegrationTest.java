package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanStatus;
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
        service.createYearPlan(leader3, request(ageGroup3, List.of(), List.of()));

        assertApiError(
                () -> service.createYearPlan(vicePrincipal, request(ageGroup3, List.of(), List.of())),
                ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("EDU-02: Tổ trưởng không lập được kế hoạch cho nhóm tuổi khác")
    void teamLeaderCannotCreateForOtherAgeGroup() {
        assertApiError(
                () -> service.createYearPlan(leader3, request(ageGroup4, List.of(), List.of())), ApiCode.FORBIDDEN);
        assertApiError(
                () -> service.createYearPlan(teacher3, request(ageGroup3, List.of(), List.of())), ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("EDU-09: lưu cây mục tiêu, đánh số MT theo thứ tự lĩnh vực, tích chủ đề theo key")
    void savesGoalTreeAndNumbersGoalsAcrossDomains() {
        YearPlanDetailResponse created = service.createYearPlan(leader3, sampleRequest());

        assertThat(created.code()).isEqualTo("KHNH-2627-MGB");
        assertThat(created.status()).isEqualTo(EduPlanStatus.DRAFT);
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
        YearPlanDetailResponse updated = service.updateYearPlan(leader3, created.id(), edit);

        assertThat(domain(updated, domainPhysical).rows()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(goal.id());
            assertThat(row.contents()).isEmpty();
            assertThat(row.goalNo()).isEqualTo((short) 1);
        });
        assertThat(domain(updated, domainLanguage).rows()).isEmpty();
    }

    @Test
    @DisplayName("EDU-03, EDU-04, EDU-05: gửi, Phó HT sửa khi chờ duyệt, ban hành rồi khóa")
    void submitEditIssueFlow() {
        YearPlanDetailResponse plan = service.createYearPlan(leader3, sampleRequest());

        YearPlanDetailResponse sent = service.submitYearPlan(leader3, plan.id(), null);
        assertThat(sent.status()).isEqualTo(EduPlanStatus.PENDING_VP);
        assertThat(sent.sentBy().userId()).isEqualTo(leader3.userId());
        // Đã gửi thì Tổ trưởng không sửa được, Phó HT thì được (ghi lịch sử 1 lần)
        assertApiError(() -> service.updateYearPlan(leader3, plan.id(), sampleRequest()), ApiCode.FORBIDDEN);
        service.updateYearPlan(vicePrincipal, plan.id(), sampleRequest());
        YearPlanDetailResponse editedTwice = service.updateYearPlan(vicePrincipal, plan.id(), sampleRequest());
        assertThat(editedTwice.history())
                .extracting(YearPlanDetailResponse.History::action)
                .containsExactly("SUBMIT", "VP_EDIT");

        YearPlanDetailResponse issued = service.issueYearPlan(vicePrincipal, plan.id(), "Đạt");
        assertThat(issued.status()).isEqualTo(EduPlanStatus.APPROVED);
        assertThat(issued.issued().userId()).isEqualTo(vicePrincipal.userId());
        assertThat(issued.issued().at()).isNotNull();
        assertApiError(() -> service.updateYearPlan(vicePrincipal, plan.id(), sampleRequest()), ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("EDU-04: kế hoạch chưa có MT không gửi được")
    void cannotSubmitWithoutGoals() {
        YearPlanDetailResponse plan = service.createYearPlan(leader3, request(ageGroup3, List.of(), List.of()));

        assertApiError(() -> service.submitYearPlan(leader3, plan.id(), null), ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("EDU-05: trả lại cần lý do, chỉ khi chờ duyệt; Tổ trưởng thấy lý do")
    void returnRequiresPendingAndReason() {
        YearPlanDetailResponse plan = service.createYearPlan(leader3, sampleRequest());
        assertApiError(() -> service.returnYearPlan(vicePrincipal, plan.id(), "Sửa MT"), ApiCode.CONFLICT);
        service.submitYearPlan(leader3, plan.id(), null);
        assertApiError(() -> service.returnYearPlan(vicePrincipal, plan.id(), " "), ApiCode.BAD_REQUEST);
        assertApiError(() -> service.returnYearPlan(leader3, plan.id(), "Sửa MT"), ApiCode.FORBIDDEN);

        service.returnYearPlan(vicePrincipal, plan.id(), "Sửa MT 2");
        YearPlanDetailResponse seenByLeader = service.getYearPlan(leader3, plan.id());

        assertThat(seenByLeader.status()).isEqualTo(EduPlanStatus.REJECTED);
        assertThat(seenByLeader.lastReturnNote()).isEqualTo("Sửa MT 2");
        assertThat(seenByLeader.permissions().canSubmit()).isTrue();
        assertThat(seenByLeader.permissions().canDelete()).isFalse();
    }

    @Test
    @DisplayName("EDU-05: Phó HT tự soạn thì ban hành thẳng, Tổ trưởng không ban hành được")
    void vicePrincipalIssuesOwnDraftDirectly() {
        YearPlanDetailResponse plan = service.createYearPlan(vicePrincipal, sampleRequest());

        assertApiError(() -> service.issueYearPlan(leader3, plan.id(), null), ApiCode.FORBIDDEN);
        assertThat(service.issueYearPlan(vicePrincipal, plan.id(), null).status())
                .isEqualTo(EduPlanStatus.APPROVED);
    }

    @Test
    @DisplayName("EDU-06: GV chỉ xem bản đã ban hành; HT không xem nháp; TT khác khối không xem")
    void viewScopeByRole() {
        YearPlanDetailResponse plan = service.createYearPlan(leader3, sampleRequest());

        assertApiError(() -> service.getYearPlan(teacher3, plan.id()), ApiCode.FORBIDDEN);
        assertApiError(() -> service.getYearPlan(principal, plan.id()), ApiCode.FORBIDDEN);
        assertApiError(() -> service.getYearPlan(leader4, plan.id()), ApiCode.FORBIDDEN);
        assertThat(list(teacher3).items()).isEmpty();
        assertThat(list(vicePrincipal).items())
                .extracting(YearPlanSummaryResponse::id)
                .containsExactly(plan.id());

        service.submitYearPlan(leader3, plan.id(), null);
        service.issueYearPlan(vicePrincipal, plan.id(), null);

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
    @DisplayName("EDU-07: chỉ người soạn xóa được bản nháp")
    void deleteOnlyOwnDraft() {
        YearPlanDetailResponse plan = service.createYearPlan(leader3, sampleRequest());
        assertApiError(() -> service.deleteYearPlan(vicePrincipal, plan.id()), ApiCode.FORBIDDEN);

        service.submitYearPlan(leader3, plan.id(), null);
        service.returnYearPlan(vicePrincipal, plan.id(), "Sửa");
        assertApiError(() -> service.deleteYearPlan(leader3, plan.id()), ApiCode.CONFLICT);

        YearPlanDetailResponse other = service.createYearPlan(leader4, request(ageGroup4, List.of(), List.of()));
        service.deleteYearPlan(leader4, other.id());
        assertApiError(() -> service.getYearPlan(vicePrincipal, other.id()), ApiCode.NOT_FOUND);
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

    private PageResponse<YearPlanSummaryResponse> list(CurrentUser user) {
        return service.getYearPlans(user, schoolYear, null, null, PageRequest.of(0, 20));
    }

    private static YearPlanDetailResponse.Domain domain(YearPlanDetailResponse plan, long domainId) {
        return plan.domains().stream()
                .filter(d -> d.domainId() == domainId)
                .findFirst()
                .orElseThrow();
    }

    private static void assertApiError(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode()).isEqualTo(code));
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
