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
import com.carenest.dto.eduplan.EduAiResponse;
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
    private final com.carenest.service.eduplan.EduCurriculumService curriculum =
            mock(com.carenest.service.eduplan.EduCurriculumService.class);
    private final com.carenest.service.eduplan.impl.rag.EduRagRetriever rag =
            mock(com.carenest.service.eduplan.impl.rag.EduRagRetriever.class);
    private final com.carenest.service.eduplan.EduPreviousYearTickService previous =
            mock(com.carenest.service.eduplan.EduPreviousYearTickService.class);
    private final com.carenest.integration.ai.EmbeddingClient embedding =
            mock(com.carenest.integration.ai.EmbeddingClient.class);
    private final CurrentUser user = mock(CurrentUser.class);
    private final com.carenest.service.organization.ClassChildNameLookup childNames =
            mock(com.carenest.service.organization.ClassChildNameLookup.class);
    private EduAiServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        var matcher = new com.carenest.service.eduplan.impl.match.EduGoalContentMatcher(
                curriculum,
                previous,
                rag,
                new com.carenest.service.eduplan.impl.match.EduCandidateRanker(embedding),
                mapper);
        service = new EduAiServiceImpl(ai, mapper, config, docs, plans, rag, matcher, childNames);
        when(ai.isAvailable(any())).thenReturn(true);
        when(user.userId()).thenReturn(7L);
    }

    private void aiReturns(String json) {
        when(ai.complete(any())).thenReturn(new AiRawResult(json, "test", "test", "edu/test", null, null, 0));
    }

    private static com.carenest.service.eduplan.impl.rag.EduRagRetriever.Hit hit(
            String id, String content, String url) {
        var meta = new ObjectMapper().createObjectNode();
        if (url != null) meta.put("url", url);
        return new com.carenest.service.eduplan.impl.rag.EduRagRetriever.Hit(id, "lesson", "WEB", content, 0.8, meta);
    }

    @Test
    void activitiesUseRagReferencesAndDropInventedFrom() {
        // EDU-113 (RAG bước b): tham khảo vào prompt; "from" lạ ⇒ bỏ gợi ý; from đúng ⇒ trả ref có tiêu đề + link
        EduConfigResponse cfg = mock(EduConfigResponse.class);
        when(cfg.weekRows())
                .thenReturn(List.of(new EduConfigResponse.WeekRow("hoc", "Hoạt động học", false, false, null)));
        when(config.getEduConfig()).thenReturn(cfg);
        when(rag.retrieve(
                        any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(List.of(hit("WEB-1", "Đề tài: Bật xa\nI. Mục đích", "https://x/1")));
        when(ai.complete(any())).thenAnswer(inv -> {
            AiRequest req = inv.getArgument(0);
            assertThat(req.userPrompt()).contains("[WEB-1] Đề tài: Bật xa");
            return new AiRawResult(
                    "{\"items\":[{\"goalId\":\"1\",\"slot\":\"hoc\",\"name\":\"Bật xa\",\"lines\":[0],\"from\":\"WEB-1\"},"
                            + "{\"goalId\":\"2\",\"slot\":\"hoc\",\"name\":\"Bịa\",\"lines\":[0],\"from\":\"WEB-9\"}]}",
                    "t",
                    "t",
                    "t",
                    null,
                    null,
                    0);
        });
        var out = service.suggestActivities(
                user,
                new EduAiRequest.Activities(
                        "Gia đình",
                        List.of(
                                new EduAiRequest.Goal("1", 1, "MT1", List.of("Bật xa", "Chạy")),
                                new EduAiRequest.Goal("2", 2, "MT2", List.of("Đọc thơ")))));
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().ref().id()).isEqualTo("WEB-1");
        assertThat(out.getFirst().ref().title()).isEqualTo("Đề tài: Bật xa");
        assertThat(out.getFirst().ref().url()).isEqualTo("https://x/1");
    }

    @Test
    void lessonStepsStillWorkWhenRagFails() {
        // AI-03: RAG lỗi ⇒ không có tham khảo, AI vẫn soạn
        when(rag.retrieve(
                        any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenThrow(new IllegalStateException("down"));
        aiReturns("{\"a\":{\"teacher\":\"Cô hỏi\",\"child\":\"Trẻ đáp\"},\"b\":{},\"c\":{}}");
        var out =
                service.suggestLessonSteps(user, new EduAiRequest.LessonSteps("Thể dục", List.of("Bật xa"), List.of()));
        assertThat(out.a().teacher()).isEqualTo("Cô hỏi");
        assertThat(out.refs()).isEmpty();
    }

    @Test
    void lessonStepsFollowRequestedStepsOfActivity() {
        // Tiền 25/11/2026: gợi ý theo từng hoạt động; có tên bước ⇒ prompt liệt kê bước, trả rows đúng thứ tự
        when(ai.complete(any())).thenAnswer(inv -> {
            AiRequest req = inv.getArgument(0);
            assertThat(req.userPrompt()).contains("Hoạt động ngoài trời").contains("2) Trò chơi vận động");
            return new AiRawResult(
                    "{\"rows\":[{\"teacher\":\"Cô dẫn trẻ ra sân\",\"child\":\"Trẻ quan sát\"},"
                            + "{\"teacher\":\"Cô giới thiệu trò chơi\",\"child\":\"Trẻ chơi\"}]}",
                    "t",
                    "t",
                    "t",
                    null,
                    null,
                    0);
        });
        var out = service.suggestLessonSteps(
                user,
                new EduAiRequest.LessonSteps(
                        "",
                        List.of("Quan sát cây"),
                        List.of(),
                        "Hoạt động ngoài trời",
                        List.of("Hoạt động có mục đích", "Trò chơi vận động")));
        assertThat(out.rows())
                .extracting(EduAiResponse.Step::teacher)
                .containsExactly("Cô dẫn trẻ ra sân", "Cô giới thiệu trò chơi");
    }

    @Test
    @DisplayName("EDU-114: bỏ MT/loại giờ/dòng lạ, mỗi dòng chỉ một hoạt động, được gộp dòng; MT trống bỏ qua")
    void activitiesFollowContentLines() {
        EduConfigResponse cfg = mock(EduConfigResponse.class);
        when(cfg.weekRows())
                .thenReturn(List.of(new EduConfigResponse.WeekRow("hoc", "Hoạt động học", false, false, null)));
        when(config.getEduConfig()).thenReturn(cfg);
        aiReturns("""
                {"items":[{"goalId":"1","slot":"hoc","name":"Gộp","lines":[0,1,7]},
                {"goalId":"1","slot":"hoc","name":"Trùng dòng","lines":[1]},
                {"goalId":"1","slot":"hoc","name":"Không dòng"},
                {"goalId":"99","slot":"hoc","name":"MT lạ","lines":[0]},
                {"goalId":"3","slot":"hoc","name":"MT trống","lines":[0]},
                {"goalId":"2","slot":"bay","name":"Loại giờ lạ","lines":[0]}]}""");
        var out = service.suggestActivities(
                user,
                new EduAiRequest.Activities(
                        "Trường mầm non",
                        List.of(
                                new EduAiRequest.Goal("1", 1, "MT1", List.of("Hát", "Vỗ tay")),
                                new EduAiRequest.Goal("2", 2, "MT2", List.of("Đọc thơ")),
                                new EduAiRequest.Goal("3", 3, "MT3", List.of()))));
        assertThat(out).extracting(EduAiResponse.Activity::name).containsExactly("Gộp");
        assertThat(out.getFirst().lines()).containsExactly(0, 1);
        org.mockito.ArgumentCaptor<AiRequest> request = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).complete(request.capture());
        assertThat(request.getValue().userPrompt()).contains("[1] Vỗ tay").doesNotContain("MT3");
        // Một lượt tìm tham khảo cho mỗi dòng nội dung
        verify(rag, org.mockito.Mockito.times(3))
                .retrieve(
                        any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble());
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

    private YearPlanDetailResponse annual(boolean allowed) {
        var p = mock(YearPlanDetailResponse.class);
        when(p.planLevel()).thenReturn(EduPlanLevel.SCHOOL_YEAR);
        when(p.canEditCurriculum()).thenReturn(allowed);
        when(p.ageGroupId()).thenReturn(5L);
        var empty = new YearPlanDetailResponse.Row(
                10L,
                "goal",
                "Trẻ hát đúng giai điệu",
                (short) 1,
                List.of(new YearPlanDetailResponse.Content(1L, " ", List.of())),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        var custom = new YearPlanDetailResponse.Row(
                11L, "goal", "Trẻ đọc thơ rõ lời", (short) 2, List.of(), null, null, null, null, null, null, null);
        var full = new YearPlanDetailResponse.Row(
                12L,
                "goal",
                "Trẻ hát",
                (short) 3,
                List.of(new YearPlanDetailResponse.Content(2L, "Đã có", List.of())),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(p.domains())
                .thenReturn(
                        List.of(new YearPlanDetailResponse.Domain(100L, "Âm nhạc", "", List.of(empty, custom, full))));
        when(plans.getYearPlan(user, 9L)).thenReturn(p);
        when(curriculum.getContents(5L))
                .thenReturn(List.of(
                        catalog(20L, 100L, "Hát đúng giai điệu"),
                        catalog(21L, 100L, "Đọc thơ, ca dao"),
                        catalog(22L, 200L, "Khác lĩnh vực")));
        return p;
    }

    private static com.carenest.dto.eduplan.CurriculumContentResponse catalog(long id, long domain, String text) {
        return new com.carenest.dto.eduplan.CurriculumContentResponse(
                id, 1L, domain, "", null, "", "", "", "", text, "C", 1);
    }

    @Test
    @DisplayName("EDU-106: nút chung chỉ mục tiêu trống, chữ lấy từ nguồn, lọc key/điểm/căn cứ/trùng")
    void annualKeepsOnlyEmptyGoalsAndUsesSourceText() {
        annual(true);
        aiReturns("""
                {"matches":[{"goalId":10,"key":"C20","score":3,"from":"hát đúng","text":"CHỮ BỊA"},
                {"goalId":11,"key":"C21","score":2,"from":"đọc  thơ"},
                {"goalId":12,"key":"C20","score":3,"from":"hát"},
                {"goalId":10,"key":"C22","score":3,"from":"hát"},
                {"goalId":99,"key":"C20","score":3,"from":"hát"},
                {"goalId":10,"key":"C20","score":3,"from":"hát"},
                {"goalId":11,"key":"C20","score":3,"from":"không nằm trong mục tiêu"},
                {"goalId":11,"key":"C20","score":1,"from":"đọc thơ"}],
                "written":[{"goalId":10,"text":"Hát theo nhạc","from":"hát"}]}
                """);
        var out = service.matchGoalContents(user, 9L, null);
        assertThat(out.matches()).extracting("text").containsExactly("Hát đúng giai điệu", "Đọc thơ, ca dao");
        assertThat(out.matches()).extracting("source").containsOnly("CURRICULUM_C");
        org.mockito.ArgumentCaptor<AiRequest> request = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).complete(request.capture());
        assertThat(request.getValue().task()).isEqualTo(AiTask.EDU_MATCH);
        assertThat(request.getValue().userPrompt()).doesNotContain("Đã có", "Khác lĩnh vực", "\"goalId\":12");
    }

    @Test
    @DisplayName("EDU-106: nút từng mục tiêu nhận mục tiêu đã có nội dung, không gợi ý lại câu đang có")
    void annualSingleGoalIncludesGoalWithContents() {
        annual(true);
        when(curriculum.getContents(5L))
                .thenReturn(List.of(catalog(20L, 100L, "Hát đúng giai điệu"), catalog(23L, 100L, "Đã có")));
        aiReturns("{\"matches\":[{\"goalId\":12,\"key\":\"C20\",\"score\":3,\"from\":\"hát\"},"
                + "{\"goalId\":12,\"key\":\"C23\",\"score\":3,\"from\":\"hát\"}]}");
        var out = service.matchGoalContents(user, 9L, 12L);
        assertThat(out.matches())
                .extracting("goalId", "text")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(12L, "Hát đúng giai điệu"));
        org.mockito.ArgumentCaptor<AiRequest> request = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).complete(request.capture());
        assertThat(request.getValue().userPrompt()).contains("\"goalId\":12").doesNotContain("\"goalId\":10", "C23");
        assertThatThrownBy(() -> service.matchGoalContents(user, 9L, 77L))
                .extracting("apiCode")
                .isEqualTo(ApiCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("EDU-106: nguồn mục D, năm trước, RAG; AI tự viết chỉ khi không chọn được ứng viên")
    void annualUsesAllSourcesAndWritesOnlyWhenNothingFits() {
        annual(true);
        when(curriculum.getContents(5L)).thenReturn(List.of());
        when(curriculum.getGoals(5L))
                .thenReturn(List.of(new com.carenest.dto.eduplan.CurriculumGoalResponse(
                        40L, 1L, 100L, "", null, "", null, "", "", "", "", "", 1, List.of("Hát cùng cô"))));
        when(previous.previousYearContents(any(), any()))
                .thenReturn(List.of(new com.carenest.service.eduplan.EduPreviousYearTickService.PreviousContent(
                        100L, "g", "Hát dân ca")));
        when(rag.active(any()))
                .thenReturn(List.of(new com.carenest.service.eduplan.impl.rag.EduRagRetriever.Hit(
                        "MT-1", "mt", "SEED", "YCCĐ A1: Trẻ hát\nNội dung: hát theo nhịp; vỗ tay", 0, null)));
        aiReturns("""
                {"matches":[{"goalId":10,"key":"D40_0","score":3,"from":"hát"},
                {"goalId":10,"key":"Y0","score":2,"from":"hát"},
                {"goalId":10,"key":"RMT-1_0","score":3,"from":"giai điệu"}],
                "written":[{"goalId":11,"text":"đọc thơ theo cô","from":"đọc thơ"},
                {"goalId":11,"text":"Bịa căn cứ","from":"không có"},
                {"goalId":11,"text":"đọc thơ theo cô.","from":"đọc thơ"}]}
                """);
        var out = service.matchGoalContents(user, 9L, null);
        assertThat(out.matches())
                .extracting("goalId", "text", "source")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, "Hát cùng cô", "CURRICULUM_D"),
                        org.assertj.core.groups.Tuple.tuple(10L, "Hát dân ca", "PREVIOUS_YEAR"),
                        org.assertj.core.groups.Tuple.tuple(10L, "Hát theo nhịp.", "RAG"),
                        org.assertj.core.groups.Tuple.tuple(11L, "Đọc thơ theo cô.", "AI"));
        assertThat(out.matches().get(2).ref().id()).isEqualTo("MT-1");
    }

    @Test
    void annualDeniedBeforeCatalogAndAiForWrongLevelOrNoEdit() {
        var p = annual(false);
        assertThatThrownBy(() -> service.matchGoalContents(user, 9L, null))
                .extracting("apiCode")
                .isEqualTo(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN);
        when(p.canEditCurriculum()).thenReturn(true);
        when(p.planLevel()).thenReturn(EduPlanLevel.TEAM);
        assertThatThrownBy(() -> service.matchGoalContents(user, 9L, null))
                .extracting("apiCode")
                .isEqualTo(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN);
        verify(curriculum, never()).getContents(any());
        verify(ai, never()).complete(any());
    }

    @Test
    void annualNoEmptyGoalReturnsEmptyWithoutAi() {
        var p = annual(true);
        when(p.domains()).thenReturn(List.of());
        assertThat(service.matchGoalContents(user, 9L, null).matches()).isEmpty();
        verify(ai, never()).complete(any());
    }

    @Test
    void annualAiOffTimeoutAndBrokenJsonRetainExistingErrors() {
        annual(true);
        when(ai.isAvailable(AiTask.EDU_MATCH)).thenReturn(false);
        assertThatThrownBy(() -> service.matchGoalContents(user, 9L, null))
                .extracting("apiCode")
                .isEqualTo(ApiCode.AI_SERVICE_UNAVAILABLE);
        when(ai.isAvailable(AiTask.EDU_MATCH)).thenReturn(true);
        aiReturns("not json");
        assertThatThrownBy(() -> service.matchGoalContents(user, 9L, null))
                .extracting("apiCode")
                .isEqualTo(ApiCode.AI_SERVICE_UNAVAILABLE);
        when(ai.complete(any())).thenThrow(new AiClientException(AiErrorReason.TIMEOUT, "slow", null));
        assertThatThrownBy(() -> service.matchGoalContents(user, 9L, null))
                .extracting("apiCode")
                .isEqualTo(ApiCode.AI_TIMEOUT);
    }

    @Test
    void annualPartialFailureStillReturnsOtherDomain() {
        var p = annual(true);
        var other = new YearPlanDetailResponse.Row(
                30L, "goal", "Trẻ đếm số", (short) 5, List.of(), null, null, null, null, null, null, null);
        var first = p.domains().getFirst();
        when(p.domains())
                .thenReturn(List.of(first, new YearPlanDetailResponse.Domain(200L, "Toán", "", List.of(other))));
        when(ai.complete(any())).thenAnswer(inv -> {
            AiRequest r = inv.getArgument(0);
            if (r.userPrompt().contains("Toán")) throw new AiClientException(AiErrorReason.TIMEOUT, "slow", null);
            return new AiRawResult(
                    "{\"matches\":[{\"goalId\":10,\"key\":\"C20\",\"score\":3,\"from\":\"hát\"}]}",
                    "t",
                    "t",
                    "t",
                    0,
                    0,
                    0);
        });
        var out = service.matchGoalContents(user, 9L, null);
        assertThat(out.matches()).hasSize(1);
        assertThat(out.failedDomains()).extracting("domainId").containsExactly(200L);
    }

    private EduAiRequest.ClassContents classAsk(String situation, Integer lineIndex) {
        return new EduAiRequest.ClassContents(
                3L, 9L, situation, List.of(new EduAiRequest.ClassGoal(10L, List.of("Hát cũ", "Vỗ tay"), lineIndex)));
    }

    @Test
    @DisplayName("EDU-115: có tình hình lớp thì AI viết lại, che tên trẻ trước khi gửi, một dòng thay đúng một câu")
    void rewriteClassContentsMasksNamesAndKeepsOneLine() {
        annual(true);
        when(childNames.childNames(any(), any())).thenReturn(List.of("Nguyễn Gia An"));
        aiReturns("""
                {"matches":[],"written":[{"goalId":10,"text":"hát cùng bạn","from":"hát"},
                {"goalId":10,"text":"hát theo cô","from":"hát"}]}
                """);
        var out = service.rewriteClassContents(user, classAsk("Gia An còn nhút nhát", 1));
        assertThat(out.matches())
                .extracting("text", "source")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Hát cùng bạn.", "AI"));
        verify(docs).requireThemeAuthor(user, 3L);
        org.mockito.ArgumentCaptor<AiRequest> request = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).complete(request.capture());
        assertThat(request.getValue().systemPrompt()).contains("VIẾT LẠI");
        assertThat(request.getValue().userPrompt())
                .contains("trẻ còn nhút nhát", "\"current\":[\"Vỗ tay\"]", "\"limit\":1")
                .doesNotContain("Gia An");
    }

    @Test
    @DisplayName("EDU-115: bỏ trống tình hình lớp thì chọn lại theo luồng ghép; dòng ngoài phạm vi bị từ chối")
    void rewriteClassContentsWithoutSituationUsesSources() {
        annual(true);
        aiReturns("{\"matches\":[{\"goalId\":10,\"key\":\"C20\",\"score\":3,\"from\":\"hát\"}]}");
        var out = service.rewriteClassContents(user, classAsk(" ", null));
        assertThat(out.matches()).extracting("source").containsExactly("CURRICULUM_C");
        verify(childNames, never()).childNames(any(), any());
        assertThatThrownBy(() -> service.rewriteClassContents(user, classAsk(null, 5)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.BAD_REQUEST);
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

    @org.junit.jupiter.api.Test
    void lessonStepAcceptsBulletArrayOrPlainText() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var step = EduAiServiceImpl.step(
                mapper.readTree("{\"teacher\":[\"Cô giới thiệu\",\"- Cô làm mẫu\"],\"child\":\"- Trẻ quan sát\"}"));
        assertThat(step.teacher()).isEqualTo("- Cô giới thiệu\n- Cô làm mẫu");
        assertThat(step.child()).isEqualTo("- Trẻ quan sát");
    }
}
