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
import com.carenest.dto.health.PublishedHealthResponse.GrowthPoint;
import com.carenest.dto.health.PublishedHealthResponse.TrendWarning;
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
        given(aiClient.complete(any())).willReturn(new AiClient.AiResult("Trẻ tăng cân đều.", "gemini-x"));

        HealthTrendAnalysisResponse response = service.analyze(TEACHER, CHILD_ID);

        assertThat(response.draft()).isTrue();
        assertThat(response.summary()).isEqualTo("Trẻ tăng cân đều.");
        assertThat(response.warnings()).isNotEmpty();
        ArgumentCaptor<AiClient.AiRequest> request = ArgumentCaptor.forClass(AiClient.AiRequest.class);
        verify(aiClient).complete(request.capture());
        assertThat(request.getValue().userPrompt())
                .doesNotContain("Bé An")
                .doesNotContain("HS26001")
                .doesNotContain("2022");
        ArgumentCaptor<HealthInterpretation> saved = ArgumentCaptor.forClass(HealthInterpretation.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(HealthInterpretation.SOURCE_AI);
        assertThat(saved.getValue().getModel()).isEqualTo("gemini-x");
        assertThat(saved.getValue().getStatus()).isEqualTo(InterpretationStatus.DRAFT);
    }

    @Test
    @DisplayName("AI-04: prompt không có ngày đo — câu cảnh báo đổi ngày thành tuổi tháng của lần đo")
    void promptReplacesMeasurementDatesWithAge() {
        List<GrowthPoint> drop = List.of(growth("2026-06-01", "100.0", "16.0"), growth("2026-09-15", "100.0", "15.5"));
        List<TrendWarning> warnings = HealthGrowth.warnings(drop);

        String prompt = HealthTrendServiceImpl.userPrompt(Gender.FEMALE, drop, warnings);

        assertThat(prompt)
                .doesNotContain("2026")
                .doesNotContain("/06/")
                .contains("Cân nặng lần đo lúc 50 tháng tuổi giảm 0.5 kg");
    }

    @Test
    @DisplayName("HLT-04: AI viết ngôn ngữ chẩn đoán ⇒ bỏ, 503, không lưu")
    void blocksDiagnosticLanguage() {
        given(records.views(any(), any(), any(), any(), anyBoolean()))
                .willReturn(List.of(point(2L, "2026-11-01"), point(1L, "2026-09-10")));
        given(aiClient.complete(any())).willReturn(new AiClient.AiResult("Có thể trẻ mắc bệnh tuyến giáp.", "m"));

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
    @DisplayName("AI-02: duyệt có sửa câu chữ ⇒ APPROVED, source=HUMAN")
    void approveWithEdit() {
        HealthInterpretation draft =
                HealthInterpretation.draft(CHILD_ID, "Bản AI", "[]", "[1,2]", "m", 7L, Instant.now());
        ReflectionTestUtils.setField(draft, "id", 90L);
        given(repository.findByIdAndChildId(90L, CHILD_ID)).willReturn(Optional.of(draft));

        HealthTrendAnalysisResponse response =
                service.approve(TEACHER, CHILD_ID, 90L, new HealthTrendApproveRequest(" Bản đã sửa "));

        assertThat(response.draft()).isFalse();
        assertThat(draft.getSummary()).isEqualTo("Bản đã sửa");
        assertThat(draft.getSource()).isEqualTo(HealthInterpretation.SOURCE_HUMAN);
        assertThat(draft.getApprovedBy()).isEqualTo(TEACHER.userId());
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
        BigDecimal height = id == 1L ? new BigDecimal("100.0") : new BigDecimal("101.5");
        BigDecimal weight = id == 1L ? new BigDecimal("16.0") : new BigDecimal("16.4");
        return new HealthMeasurementResponse(
                id,
                CHILD_ID,
                3L,
                "Kỳ cân đo",
                null,
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
