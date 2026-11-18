package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.EduAiRequest;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClient;
import com.carenest.integration.ai.AiException;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.utils.ApiCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** EDU-95/96: server lọc output AI (chỉ MT, loại giờ, tuần, ô có trong yêu cầu); AI tắt/lỗi ⇒ 8001/8002. */
class EduAiServiceImplTest {
    private final AiClient ai = mock(AiClient.class);
    private final EduConfigService config = mock(EduConfigService.class);
    private final EduClassDocService docs = mock(EduClassDocService.class);
    private final CurrentUser user = mock(CurrentUser.class);
    private EduAiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EduAiServiceImpl(ai, new ObjectMapper(), config, docs);
        when(ai.isEnabled()).thenReturn(true);
        when(user.userId()).thenReturn(7L);
    }

    private void aiReturns(String json) {
        when(ai.complete(any())).thenReturn(new AiClient.AiResult(json, "test"));
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
        when(ai.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.ask("t", "p", 10))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.AI_SERVICE_UNAVAILABLE);
        when(ai.isEnabled()).thenReturn(true);
        aiReturns("không phải JSON");
        assertThatThrownBy(() -> service.ask("t", "p", 10))
                .extracting("apiCode")
                .isEqualTo(ApiCode.AI_SERVICE_UNAVAILABLE);
        when(ai.complete(any())).thenThrow(new AiException(AiException.Reason.TIMEOUT, "slow", null));
        assertThatThrownBy(() -> service.ask("t", "p", 10))
                .extracting("apiCode")
                .isEqualTo(ApiCode.AI_TIMEOUT);
    }
}
