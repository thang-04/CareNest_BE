package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.health.HealthTrendAnalysisResponse;
import com.carenest.dto.health.HealthTrendApproveRequest;
import com.carenest.dto.health.HealthTrendContent;
import com.carenest.dto.health.HealthTrendContent.AttentionPoint;
import com.carenest.dto.health.HealthTrendContent.FollowUp;
import com.carenest.dto.health.PublishedHealthResponse.GrowthPoint;
import com.carenest.dto.health.PublishedHealthResponse.TrendWarning;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthInterpretation;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClient;
import com.carenest.integration.ai.AiException;
import com.carenest.repository.health.HealthInterpretationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.ChildService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthTrendFollowUpType;
import com.carenest.utils.enums.HealthTrendWarningCode;
import com.carenest.utils.enums.InterpretationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class HealthTrendServiceImplTest {
    private static final Long CHILD_ID = 1L;
    private static final CurrentUser TEACHER = TestPermissions.userWithDefaults(7L, CareNestRole.TEACHER);
    private static final String AI_JSON = "{\"overview\": \"Trẻ tăng cân đều.\", \"attentionPoints\": [],"
            + " \"positives\": [\"Chiều cao tăng 1,5 cm.\"],"
            + " \"followUps\": [{\"type\": \"RECHECK_NEXT_ROUND\", \"text\": \"Theo dõi ở kỳ cân đo sau.\"}],"
            + " \"limitations\": \"Mới có 2 lần đo.\"}";

    private final ChildAccessPolicy policy = mock(ChildAccessPolicy.class);
    private final ChildService childService = mock(ChildService.class);
    private final HealthRecords records = mock(HealthRecords.class);
    private final HealthCheckRules rules = mock(HealthCheckRules.class);
    private final HealthInterpretationRepository repository = mock(HealthInterpretationRepository.class);
    private final AccountService accountService = mock(AccountService.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final HealthTrendServiceImpl service = new HealthTrendServiceImpl(
            policy,
            childService,
            records,
            rules,
            repository,
            accountService,
            aiClient,
            JsonMapper.builder().build());
    private final HealthRecords.Snapshot snapshot = mock(HealthRecords.Snapshot.class);

    @BeforeEach
    void setUp() {
        given(policy.canViewChild(TEACHER, CHILD_ID)).willReturn(true);
        given(childService.findChildBriefs(any()))
                .willReturn(Map.of(
                        CHILD_ID,
                        new ChildBriefResponse(CHILD_ID, "HS26001", "Bé An", LocalDate.of(2022, 3, 1), Gender.FEMALE)));
        given(records.load(List.of(CHILD_ID))).willReturn(snapshot);
        given(rules.now()).willReturn(Instant.parse("2026-11-10T03:00:00Z"));
        given(repository.save(any())).willAnswer(invocation -> {
            HealthInterpretation i = invocation.getArgument(0);
            ReflectionTestUtils.setField(i, "id", 90L);
            return i;
        });
        given(aiClient.isEnabled()).willReturn(true);
    }

    @Test
    @DisplayName("HLT-02: dưới 2 lần đo đã công bố ⇒ NOT_ENOUGH_DATA, không gọi AI, không lưu")
    void notEnoughData() {
        given(records.views(any(), any(), any(), any(), anyBoolean())).willReturn(List.of(point(1L, "2026-09-10")));

        HealthTrendAnalysisResponse response = service.analyze(TEACHER, CHILD_ID);

        assertThat(response.status()).isEqualTo(HealthTrendAnalysisResponse.NOT_ENOUGH_DATA);
        verify(aiClient, never()).complete(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("HLT-05, AI-01, AI-04: đủ dữ liệu ⇒ nháp source=AI kèm model; prompt không có tên, ngày sinh, mã trẻ")
    void savesAiDraftWithoutIdentity() {
        given(records.views(any(), any(), any(), any(), anyBoolean()))
                .willReturn(List.of(point(2L, "2026-11-01"), point(1L, "2026-09-10")));
        given(aiClient.complete(any())).willReturn(new AiClient.AiResult(AI_JSON, "gemini-x"));

        HealthTrendAnalysisResponse response = service.analyze(TEACHER, CHILD_ID);

        assertThat(response.draft()).isTrue();
        assertThat(response.content().overview()).isEqualTo("Trẻ tăng cân đều.");
        assertThat(response.content().positives()).containsExactly("Chiều cao tăng 1,5 cm.");
        assertThat(response.summary())
                .startsWith("Tổng quan: Trẻ tăng cân đều.")
                .contains("Điểm tích cực:");
        assertThat(response.warnings()).isNotEmpty();
        ArgumentCaptor<AiClient.AiRequest> request = ArgumentCaptor.forClass(AiClient.AiRequest.class);
        verify(aiClient).complete(request.capture());
        assertThat(request.getValue().jsonOutput()).isTrue();
        assertThat(request.getValue().maxTokens()).isEqualTo(1200);
        assertThat(request.getValue().userPrompt())
                .doesNotContain("Bé An")
                .doesNotContain("HS26001")
                .doesNotContain("2022");
        ArgumentCaptor<HealthInterpretation> saved = ArgumentCaptor.forClass(HealthInterpretation.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(HealthInterpretation.SOURCE_AI);
        assertThat(saved.getValue().getModel()).isEqualTo("gemini-x");
        assertThat(saved.getValue().getStatus()).isEqualTo(InterpretationStatus.DRAFT);
        assertThat(saved.getValue().getContent()).contains("\"overview\"");
    }

    @Test
    @DisplayName("AI-04, HLT-21: prompt không có ngày đo; có mã cảnh báo, loại gợi ý được phép, số lần đo")
    void promptReplacesMeasurementDatesWithAge() {
        List<GrowthPoint> drop = List.of(growth("2026-06-01", "100.0", "16.0"), growth("2026-09-15", "100.0", "15.5"));
        List<TrendWarning> warnings = HealthGrowth.warnings(drop);

        String prompt = HealthTrendServiceImpl.userPrompt(
                Gender.FEMALE, drop, warnings, HealthGrowth.allowedFollowUps(warnings, false));

        assertThat(prompt)
                .doesNotContain("2026")
                .doesNotContain("/06/")
                .contains("[WEIGHT_DROP] Cân nặng lần đo lúc 50 tháng tuổi giảm 0.5 kg")
                .contains("[HEIGHT_STALL]")
                .contains("Số lần đo: 2")
                .contains("RECHECK_NEXT_ROUND")
                .contains("TALK_TO_PARENT")
                .doesNotContain("REMEASURE");
    }

    @Test
    @DisplayName("HLT-04, HLT-21: tổng quan có ngôn ngữ chẩn đoán hoặc output không phải JSON ⇒ bỏ, 503, không lưu")
    void blocksDiagnosticLanguage() {
        given(records.views(any(), any(), any(), any(), anyBoolean()))
                .willReturn(List.of(point(2L, "2026-11-01"), point(1L, "2026-09-10")));
        given(aiClient.complete(any()))
                .willReturn(new AiClient.AiResult("{\"overview\": \"Có thể trẻ mắc bệnh tuyến giáp.\"}", "m"));
        expectCode(() -> service.analyze(TEACHER, CHILD_ID), ApiCode.AI_SERVICE_UNAVAILABLE);

        given(aiClient.complete(any())).willReturn(new AiClient.AiResult("Trẻ tăng cân đều.", "m"));
        expectCode(() -> service.analyze(TEACHER, CHILD_ID), ApiCode.AI_SERVICE_UNAVAILABLE);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("AI-03: AI tắt ⇒ 503; quá giờ ⇒ 504")
    void aiUnavailableOrTimeout() {
        given(records.views(any(), any(), any(), any(), anyBoolean()))
                .willReturn(List.of(point(2L, "2026-11-01"), point(1L, "2026-09-10")));
        given(aiClient.isEnabled()).willReturn(false);
        expectCode(() -> service.analyze(TEACHER, CHILD_ID), ApiCode.AI_SERVICE_UNAVAILABLE);

        given(aiClient.isEnabled()).willReturn(true);
        given(aiClient.complete(any())).willThrow(new AiException(AiException.Reason.TIMEOUT, "slow", null));
        expectCode(() -> service.analyze(TEACHER, CHILD_ID), ApiCode.AI_TIMEOUT);
    }

    @Test
    @DisplayName("AI-02, HLT-21: duyệt có sửa từng mục ⇒ APPROVED, source=HUMAN, summary ghép lại từ bản sửa")
    void approveWithEdit() {
        HealthInterpretation draft = aiDraft();
        HealthTrendContent edited = new HealthTrendContent(
                " Cân nặng giảm nhẹ. ",
                List.of(new AttentionPoint(HealthTrendWarningCode.WEIGHT_DROP, "Giảm 0,5 kg.")),
                null,
                List.of(new FollowUp(HealthTrendFollowUpType.TALK_TO_PARENT, "Trao đổi với phụ huynh.")),
                " ");

        HealthTrendAnalysisResponse response =
                service.approve(TEACHER, CHILD_ID, 90L, new HealthTrendApproveRequest(edited));

        assertThat(response.draft()).isFalse();
        assertThat(response.stale()).isFalse();
        assertThat(response.content().overview()).isEqualTo("Cân nặng giảm nhẹ.");
        assertThat(response.content().positives()).isEmpty();
        assertThat(response.content().limitations()).isNull();
        assertThat(draft.getSummary())
                .startsWith("Tổng quan: Cân nặng giảm nhẹ.")
                .contains("- Giảm 0,5 kg.");
        assertThat(draft.getSource()).isEqualTo(HealthInterpretation.SOURCE_HUMAN);
        assertThat(draft.getApprovedBy()).isEqualTo(TEACHER.userId());
    }

    @Test
    @DisplayName("AI-02: duyệt không gửi nội dung, hoặc gửi đúng bản AI ⇒ giữ source=AI")
    void approveWithoutEditKeepsAiSource() {
        HealthInterpretation draft = aiDraft();

        service.approve(TEACHER, CHILD_ID, 90L, null);

        assertThat(draft.getStatus()).isEqualTo(InterpretationStatus.APPROVED);
        assertThat(draft.getSource()).isEqualTo(HealthInterpretation.SOURCE_AI);
        assertThat(draft.getSummary()).isEqualTo("Tổng quan: Trẻ tăng cân đều.");
    }

    @Test
    @DisplayName("AI-02, HLT-21: bản sửa sai (tổng quan trống, mã cảnh báo lạ, mục quá dài) ⇒ 400, không duyệt")
    void approveRejectsInvalidContent() {
        HealthInterpretation draft = aiDraft();
        HealthTrendContent invalid = new HealthTrendContent(
                " ",
                List.of(new AttentionPoint(HealthTrendWarningCode.WFA_LOW, "Cảnh báo không có trong lần phân tích.")),
                List.of("x".repeat(HealthTrendContentRules.MAX_ITEM + 1)),
                List.of(),
                null);

        assertThatThrownBy(() -> service.approve(TEACHER, CHILD_ID, 90L, new HealthTrendApproveRequest(invalid)))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                    assertThat(ex.getFieldErrors())
                            .extracting(e -> e.field())
                            .containsExactly(
                                    "content.overview",
                                    "content.attentionPoints[0].warningCode",
                                    "content.positives[0]");
                });
        assertThat(draft.getStatus()).isEqualTo(InterpretationStatus.DRAFT);
    }

    @Test
    @DisplayName("HLT-21: có số đo công bố mới sau lần phân tích ⇒ bản mới nhất đánh dấu stale")
    void latestMarksStaleAfterNewMeasurement() {
        HealthInterpretation draft = aiDraft();
        given(repository.findFirstByChildIdOrderByGeneratedAtDescIdDesc(CHILD_ID))
                .willReturn(Optional.of(draft));

        assertThat(service.latest(TEACHER, CHILD_ID).stale()).isFalse();

        given(records.views(any(), any(), any(), any(), anyBoolean()))
                .willReturn(List.of(
                        measurement(3L, "2026-12-01", "102.0", "16.6", null),
                        point(2L, "2026-11-01"),
                        point(1L, "2026-09-10")));
        assertThat(service.latest(TEACHER, CHILD_ID).stale()).isTrue();
    }

    /** Nháp AI dùng lần đo 1, 2 có cảnh báo sụt cân; số đo đã công bố hiện tại cũng là 1, 2. */
    private HealthInterpretation aiDraft() {
        HealthInterpretation draft = HealthInterpretation.draft(
                CHILD_ID,
                "Tổng quan: Trẻ tăng cân đều.",
                "{\"overview\":\"Trẻ tăng cân đều.\",\"attentionPoints\":[],\"positives\":[],"
                        + "\"followUps\":[],\"limitations\":null}",
                "[{\"code\":\"WEIGHT_DROP\",\"level\":\"warning\",\"text\":\"Cân nặng giảm.\"}]",
                "[1,2]",
                "m",
                7L,
                Instant.now());
        ReflectionTestUtils.setField(draft, "id", 90L);
        given(repository.findByIdAndChildId(90L, CHILD_ID)).willReturn(Optional.of(draft));
        given(records.views(any(), any(), any(), any(), anyBoolean()))
                .willReturn(List.of(point(2L, "2026-11-01"), point(1L, "2026-09-10")));
        return draft;
    }

    @Test
    @DisplayName("Nhân sự ngoài phạm vi trẻ ⇒ 403")
    void outOfScopeDenied() {
        given(policy.canViewChild(TEACHER, CHILD_ID)).willReturn(false);

        expectCode(() -> service.analyze(TEACHER, CHILD_ID), ApiCode.HEALTH_RECORD_ACCESS_DENIED);
    }

    @Test
    @DisplayName("HLT-02: cảnh báo xác định — sụt cân, chiều cao không tăng ≥3 tháng; bình thường ⇒ một dòng info")
    void deterministicWarnings() {
        List<GrowthPoint> drop = List.of(growth("2026-06-01", "100.0", "16.0"), growth("2026-09-15", "100.0", "15.5"));
        List<TrendWarning> warnings = HealthGrowth.warnings(drop);
        assertThat(warnings)
                .extracting(TrendWarning::text)
                .anyMatch(t -> t.startsWith("Cân nặng lần đo ngày 15/09/2026 giảm 0.5 kg"))
                .anyMatch(t -> t.startsWith("Chiều cao chưa tăng"));

        List<TrendWarning> normal = HealthGrowth.warnings(
                List.of(growth("2026-06-01", "100.0", "16.0"), growth("2026-09-15", "102.0", "16.4")));
        assertThat(normal).singleElement().satisfies(w -> assertThat(w.level()).isEqualTo(HealthGrowth.INFO));
    }

    @Test
    @DisplayName(
            "HLT-02, HLT-21: mỗi cảnh báo xác định có mã — sụt cân, chiều cao chững, z thấp, BMI ngoài ±2, BMI đổi nhanh, bình thường")
    void warningCodes() {
        List<GrowthPoint> drop = List.of(growth("2026-06-01", "100.0", "16.0"), growth("2026-09-15", "100.0", "15.5"));
        assertThat(HealthGrowth.warnings(drop))
                .extracting(TrendWarning::code)
                .containsExactly(HealthTrendWarningCode.WEIGHT_DROP, HealthTrendWarningCode.HEIGHT_STALL);

        List<GrowthPoint> lowZ =
                List.of(zPoint("2026-06-01", "-1.00", "-1.00", "0.00"), zPoint("2026-07-01", "-2.10", "-2.20", "2.30"));
        assertThat(HealthGrowth.warnings(lowZ))
                .extracting(TrendWarning::code)
                .containsExactly(
                        HealthTrendWarningCode.WFA_LOW,
                        HealthTrendWarningCode.HFA_LOW,
                        HealthTrendWarningCode.BMI_OUT_OF_RANGE,
                        HealthTrendWarningCode.BMI_Z_JUMP);

        List<TrendWarning> normal = HealthGrowth.warnings(
                List.of(growth("2026-06-01", "100.0", "16.0"), growth("2026-09-15", "102.0", "16.4")));
        assertThat(normal).extracting(TrendWarning::code).containsExactly(HealthTrendWarningCode.NORMAL);
    }

    @Test
    @DisplayName(
            "HLT-21: gợi ý được phép — bình thường chỉ theo dõi kỳ sau; có cảnh báo thêm trao đổi phụ huynh; đo lại chỉ khi còn đo lại được")
    void followUpsAllowedByWarnings() {
        TrendWarning normal = new TrendWarning(HealthTrendWarningCode.NORMAL, HealthGrowth.INFO, "bình thường");
        TrendWarning drop = new TrendWarning(HealthTrendWarningCode.WEIGHT_DROP, HealthGrowth.WARNING, "sụt cân");

        assertThat(HealthGrowth.allowedFollowUps(List.of(normal), true))
                .containsExactly(HealthTrendFollowUpType.RECHECK_NEXT_ROUND);
        assertThat(HealthGrowth.allowedFollowUps(List.of(drop), false))
                .containsExactlyInAnyOrder(
                        HealthTrendFollowUpType.RECHECK_NEXT_ROUND, HealthTrendFollowUpType.TALK_TO_PARENT);
        assertThat(HealthGrowth.allowedFollowUps(List.of(drop), true))
                .containsExactlyInAnyOrder(
                        HealthTrendFollowUpType.RECHECK_NEXT_ROUND,
                        HealthTrendFollowUpType.TALK_TO_PARENT,
                        HealthTrendFollowUpType.REMEASURE);
    }

    @Test
    @DisplayName(
            "HLT-21: kỳ cân đo đang diễn ra ⇒ gợi ý đo lại; đã gợi ý đo lại lần đo đó ⇒ lần phân tích sau không lặp")
    void remeasureSuggestedOncePerMeasurement() {
        givenWeightDropInRound(HealthCheckStatus.IN_PROGRESS);
        given(repository.findByChildIdOrderByGeneratedAtDescIdDesc(CHILD_ID)).willReturn(List.of());

        service.analyze(TEACHER, CHILD_ID);
        assertThat(lastPrompt()).contains("REMEASURE");

        HealthInterpretation previous = HealthInterpretation.draft(
                CHILD_ID,
                "Tổng quan",
                "{\"overview\":\"Tổng quan\",\"attentionPoints\":[],\"positives\":[],"
                        + "\"followUps\":[{\"type\":\"REMEASURE\",\"text\":\"Đo lại.\"}],\"limitations\":null}",
                "[]",
                "[1,2]",
                "m",
                7L,
                Instant.now());
        given(repository.findByChildIdOrderByGeneratedAtDescIdDesc(CHILD_ID)).willReturn(List.of(previous));

        service.analyze(TEACHER, CHILD_ID);
        assertThat(lastPrompt()).doesNotContain("REMEASURE").contains("TALK_TO_PARENT");
    }

    @Test
    @DisplayName("HLT-21, HLT-13: kỳ cân đo của lần đo mới nhất đã kết thúc ⇒ không gợi ý đo lại")
    void noRemeasureWhenRoundClosed() {
        givenWeightDropInRound(HealthCheckStatus.CLOSED);
        given(repository.findByChildIdOrderByGeneratedAtDescIdDesc(CHILD_ID)).willReturn(List.of());

        service.analyze(TEACHER, CHILD_ID);

        assertThat(lastPrompt()).doesNotContain("REMEASURE").contains("TALK_TO_PARENT");
    }

    private void givenWeightDropInRound(HealthCheckStatus status) {
        given(records.views(any(), any(), any(), any(), anyBoolean()))
                .willReturn(List.of(
                        measurement(2L, "2026-11-01", "101.5", "15.5", HealthCheckKind.MEASUREMENT),
                        measurement(1L, "2026-09-10", "100.0", "16.0", HealthCheckKind.MEASUREMENT)));
        HealthCheckCampaign round = mock(HealthCheckCampaign.class);
        given(round.statusOn(any())).willReturn(status);
        given(snapshot.campaign(3L)).willReturn(round);
        given(aiClient.complete(any())).willReturn(new AiClient.AiResult(AI_JSON, "m"));
    }

    private String lastPrompt() {
        ArgumentCaptor<AiClient.AiRequest> request = ArgumentCaptor.forClass(AiClient.AiRequest.class);
        verify(aiClient, org.mockito.Mockito.atLeastOnce()).complete(request.capture());
        return request.getValue().userPrompt();
    }

    private static GrowthPoint zPoint(String date, String weightZ, String heightZ, String bmiZ) {
        return new GrowthPoint(
                null,
                LocalDate.parse(date),
                50,
                new BigDecimal("100.0"),
                new BigDecimal("16.0"),
                null,
                new BigDecimal(weightZ),
                new BigDecimal(heightZ),
                new BigDecimal(bmiZ));
    }

    private static GrowthPoint growth(String date, String height, String weight) {
        return new GrowthPoint(
                null,
                LocalDate.parse(date),
                50,
                new BigDecimal(height),
                new BigDecimal(weight),
                null,
                null,
                null,
                null);
    }

    private static HealthMeasurementResponse point(Long id, String date) {
        return id == 1L ? measurement(id, date, "100.0", "16.0", null) : measurement(id, date, "101.5", "16.4", null);
    }

    private static HealthMeasurementResponse measurement(
            Long id, String date, String heightCm, String weightKg, HealthCheckKind kind) {
        BigDecimal height = new BigDecimal(heightCm);
        BigDecimal weight = new BigDecimal(weightKg);
        return new HealthMeasurementResponse(
                id,
                CHILD_ID,
                3L,
                "Kỳ cân đo",
                kind,
                10L,
                LocalDate.parse(date),
                54,
                height,
                weight,
                null,
                null,
                null,
                null,
                null,
                HealthMeasurementResponse.SOURCE_MEASUREMENT_ONLY,
                true,
                null,
                7L,
                "GV",
                false,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static void expectCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(code));
    }
}
