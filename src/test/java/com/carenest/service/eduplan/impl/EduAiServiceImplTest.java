package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.EduAiRequest;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiRawResult;
import com.carenest.integration.ai.AiRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.AiTask;
import com.carenest.utils.enums.EduPlanLevel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** EDU-95/96: server lọc output AI (chỉ MT, loại giờ, tuần, ô có trong yêu cầu); AI tắt/lỗi ⇒ 8001/8002. */
class EduAiServiceImplTest {
    private final AiGateway ai = mock(AiGateway.class);
    private final EduConfigService config = mock(EduConfigService.class);
    private final EduClassDocService docs = mock(EduClassDocService.class);
    private final EduYearPlanService plans = mock(EduYearPlanService.class);
    private final CurrentUser user = mock(CurrentUser.class);
    private EduAiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EduAiServiceImpl(ai, new ObjectMapper(), config, docs, plans);
        when(ai.isAvailable(any())).thenReturn(true);
        when(user.userId()).thenReturn(7L);
    }

    private void aiReturns(String json) {
        when(ai.complete(any())).thenReturn(new AiRawResult(json, "test", "test", "edu/test", null, null, 0));
    }

    @Test
    void activitiesDropUnknownGoalsSlotsAndDuplicates() {
        EduConfigResponse cfg = mock(EduConfigResponse.class);
        when(cfg.weekRows())
                .thenReturn(List.of(new EduConfigResponse.WeekRow("hoc", "Hoạt động học", false, false, null)));
        when(config.getEduConfig()).thenReturn(cfg);
        aiReturns("""
                {"items":[{"goalId":"1","slot":"hoc","name":"Thơ: Bé tới trường"},
                {"goalId":"1","slot":"hoc","name":"Lặp lại"},
                {"goalId":"99","slot":"hoc","name":"MT lạ"},
                {"goalId":"2","slot":"bay","name":"Loại giờ lạ"}]}""");
        var out = service.suggestActivities(
                user,
                new EduAiRequest.Activities(
                        "Trường mầm non",
                        List.of(
                                new EduAiRequest.Goal("1", 1, "MT1", List.of()),
                                new EduAiRequest.Goal("2", 2, "MT2", null))));
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().goalId()).isEqualTo("1");
    }

    @Test
    void branchNamesKeepOnlyRequestedWeeksAndUniqueNames() {
        aiReturns("""
                ```json
                {"suggestions":[{"weekStart":"2026-09-07","name":"Bé vui đến trường"},
                {"weekStart":"2026-09-14","name":"bé vui đến trường"},
                {"weekStart":"2027-01-01","name":"Tuần lạ"}]}
                ```""");
        var out = service.suggestBranchNames(
                user,
                new EduAiRequest.BranchNames(
                        1L,
                        1L,
                        List.of(new EduAiRequest.Theme(
                                "t1",
                                "Trường mầm non",
                                null,
                                List.of(
                                        new EduAiRequest.Week("2026-09-07", 1),
                                        new EduAiRequest.Week("2026-09-14", 2)))),
                        List.of()));
        assertThat(out.suggestions()).extracting("weekStart").containsExactly("2026-09-07");
    }

    @Test
    void editDayReturnsOnlyKnownChangedCells() {
        EduClassDoc day = mock(EduClassDoc.class);
        when(day.getContent()).thenReturn("""
                        {"days":[{"date":"2026-09-07","sections":{"hoc":{"aims":"Trẻ biết bài thơ","prep":"",
                        "rows":[{"title":"Ổn định","teacher":"Cô hát","child":"","note":""}]}}}]}""");
        when(docs.requireDayAccess(eq(user), eq("dd_1"), eq(true))).thenReturn(day);
        aiReturns("""
                {"changes":[{"path":"days.0.sections.hoc.aims","after":"Trẻ đọc thuộc bài thơ"},
                {"path":"days.0.sections.hoc.rows.0.teacher","after":"Cô hát"},
                {"path":"days.9.sections.hoc.aims","after":"Ô lạ"}]}""");
        var out = service.editDay(user, "dd_1", new EduAiRequest.EditDay("đổi mục đích"));
        assertThat(out.changes()).hasSize(1);
        assertThat(out.changes().getFirst().before()).isEqualTo("Trẻ biết bài thơ");
        assertThat(out.changes().getFirst().label()).contains("Thứ 2 07/09").contains("Mục đích");
    }

    @Test
    void aiOffOrBrokenJsonGivesServiceUnavailable() {
        when(ai.isAvailable(any())).thenReturn(false);
        assertThatThrownBy(() -> service.ask("t", "p"))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.AI_SERVICE_UNAVAILABLE);
        when(ai.isAvailable(any())).thenReturn(true);
        aiReturns("không phải JSON");
        assertThatThrownBy(() -> service.ask("t", "p")).extracting("apiCode").isEqualTo(ApiCode.AI_SERVICE_UNAVAILABLE);
        when(ai.complete(any())).thenThrow(new AiClientException(AiErrorReason.TIMEOUT, "slow", null));
        assertThatThrownBy(() -> service.ask("t", "p")).extracting("apiCode").isEqualTo(ApiCode.AI_TIMEOUT);
    }

    // EDU-103: kế hoạch tổ dựng tay — 2 lĩnh vực (nội dung 11, 12 | 21), 2 chủ đề (lễ hội, tên nhánh)
    private YearPlanDetailResponse teamPlan(boolean canAssign) {
        YearPlanDetailResponse plan = mock(YearPlanDetailResponse.class);
        when(plan.planLevel()).thenReturn(EduPlanLevel.TEAM);
        when(plan.canAssignContents()).thenReturn(canAssign);
        when(plan.themes())
                .thenReturn(List.of(
                        new YearPlanDetailResponse.Theme(
                                1L, "Trường mầm non", "TMN", null, null, "Tết Trung thu", null),
                        new YearPlanDetailResponse.Theme(2L, "Gia đình", "GĐ", null, null, null, null)));
        when(plan.branchTemplates())
                .thenReturn(
                        List.of(new YearPlanDetailResponse.BranchTemplate(5L, 2L, 5L, null, "Nhà của bé", null, null)));
        when(plan.domains())
                .thenReturn(List.of(
                        domain(
                                100L,
                                "Phát triển thể chất",
                                new YearPlanDetailResponse.Content(11L, "Đi theo đường hẹp", List.of()),
                                new YearPlanDetailResponse.Content(12L, "Bé làm đèn ông sao", List.of()),
                                new YearPlanDetailResponse.Content(13L, "  ", List.of())),
                        domain(
                                200L,
                                "Phát triển ngôn ngữ",
                                new YearPlanDetailResponse.Content(21L, "Thơ: Bé và mẹ", List.of()))));
        when(plans.getYearPlan(user, 9L)).thenReturn(plan);
        return plan;
    }

    private static YearPlanDetailResponse.Domain domain(
            Long id, String name, YearPlanDetailResponse.Content... contents) {
        return new YearPlanDetailResponse.Domain(
                id,
                name,
                name,
                List.of(new YearPlanDetailResponse.Row(
                        id + 1, "goal", "MT", (short) 1, List.of(contents), null, null, null, null, null, null, null)));
    }

    private void aiReturnsByDomain(String physical, String language) {
        when(ai.complete(any())).thenAnswer(inv -> {
            AiRequest req = inv.getArgument(0);
            assertThat(req.task()).isEqualTo(AiTask.EDU_MATCH);
            String body = req.userPrompt().contains("Phát triển thể chất") ? physical : language;
            if (body == null) throw new AiClientException(AiErrorReason.TIMEOUT, "slow", null);
            return new AiRawResult(body, "test", "test", "edu/test", 10, 5, 0);
        });
    }

    private EduAiRequest.ThemeContentMap asks() {
        return new EduAiRequest.ThemeContentMap(
                List.of(new EduAiRequest.ThemeRequest(1L, "đèn ông sao, đi dạo sân trường")));
    }

    @Test
    @DisplayName("EDU-103: trả cặp hợp lệ của mọi lĩnh vực, bỏ cặp trùng")
    void themeMapReturnsValidMatchesWithoutDuplicates() {
        teamPlan(true);
        aiReturnsByDomain("""
                {"matches":[{"contentId":12,"themeId":1,"from":"Đèn ông sao"},
                {"contentId":12,"themeId":1,"from":"Tết Trung thu"}]}""", """
                {"matches":[{"contentId":"21","themeId":"2","from":"Nhà của bé"}]}""");
        var out = service.matchThemeContents(user, 9L, asks());
        assertThat(out.failedDomains()).isEmpty();
        assertThat(out.matches())
                .extracting("themeId", "contentId", "from")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 12L, "Đèn ông sao"),
                        org.assertj.core.groups.Tuple.tuple(2L, 21L, "Nhà của bé"));
    }

    @Test
    @DisplayName("EDU-103: bỏ contentId của lĩnh vực khác, nội dung trống và themeId lạ")
    void themeMapDropsForeignContentAndUnknownTheme() {
        teamPlan(true);
        aiReturnsByDomain("""
                {"matches":[{"contentId":21,"themeId":1,"from":"đèn ông sao"},
                {"contentId":13,"themeId":1,"from":"đèn ông sao"},
                {"contentId":11,"themeId":99,"from":"đèn ông sao"},
                {"contentId":11,"themeId":1,"from":"đi dạo sân trường"}]}""", """
                {"matches":[]}""");
        var out = service.matchThemeContents(user, 9L, asks());
        assertThat(out.matches()).extracting("contentId").containsExactly(11L);
    }

    @Test
    @DisplayName("EDU-103: bỏ dòng có from không nằm trong chữ của đúng chủ đề")
    void themeMapDropsUngroundedFrom() {
        teamPlan(true);
        aiReturnsByDomain("""
                {"matches":[{"contentId":11,"themeId":2,"from":"đi dạo sân trường"},
                {"contentId":12,"themeId":1,"from":"lồng đèn kéo quân"},
                {"contentId":11,"themeId":1,"from":""}]}""", """
                {"matches":[{"contentId":21,"themeId":2,"from":"Gia đình"}]}""");
        var out = service.matchThemeContents(user, 9L, asks());
        assertThat(out.matches()).extracting("contentId").containsExactly(21L);
    }

    @Test
    @DisplayName("EDU-103: không tick được nội dung ⇒ lỗi quyền, không gọi AI")
    void themeMapDeniedWithoutAssignRight() {
        teamPlan(false);
        assertThatThrownBy(() -> service.matchThemeContents(user, 9L, asks()))
                .extracting("apiCode")
                .isEqualTo(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN);
        verify(ai, never()).complete(any());
    }

    @Test
    @DisplayName("EDU-103: một lĩnh vực lỗi ⇒ lĩnh vực khác vẫn trả, lĩnh vực lỗi nằm trong failedDomains")
    void themeMapReportsFailedDomain() {
        teamPlan(true);
        aiReturnsByDomain(null, """
                {"matches":[{"contentId":21,"themeId":2,"from":"Nhà của bé"}]}""");
        var out = service.matchThemeContents(user, 9L, asks());
        assertThat(out.matches()).extracting("contentId").containsExactly(21L);
        assertThat(out.failedDomains()).extracting("domainId").containsExactly(100L);
    }

    @Test
    @DisplayName("EDU-103: AI tắt ⇒ 8001; mọi lĩnh vực quá hạn ⇒ 8002")
    void themeMapAiOffOrAllTimedOut() {
        teamPlan(true);
        when(ai.isAvailable(AiTask.EDU_MATCH)).thenReturn(false);
        assertThatThrownBy(() -> service.matchThemeContents(user, 9L, asks()))
                .extracting("apiCode")
                .isEqualTo(ApiCode.AI_SERVICE_UNAVAILABLE);
        verify(ai, never()).complete(any());
        when(ai.isAvailable(AiTask.EDU_MATCH)).thenReturn(true);
        aiReturnsByDomain(null, null);
        assertThatThrownBy(() -> service.matchThemeContents(user, 9L, asks()))
                .extracting("apiCode")
                .isEqualTo(ApiCode.AI_TIMEOUT);
    }
}
