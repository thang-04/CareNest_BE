package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.AiRawResult;
import com.carenest.integration.ai.AiRequest;
import com.carenest.integration.ai.PromptTemplateLoader;
import com.carenest.service.observation.impl.PeriodCommentFacts.Note;
import com.carenest.utils.enums.AiDataClass;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.AiTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiPeriodCommentDraftGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");
    private static final String GOOD =
            "{TEN} đi học đều 18 ngày trong tháng, hay giúp bạn xếp đồ chơi và nhận 4 cờ bé ngoan. "
                    + "Cô và gia đình cùng khuyến khích con tự tin hơn khi kể chuyện trước lớp.";

    private final AiGateway gateway = mock(AiGateway.class);
    private AiPeriodCommentDraftGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AiPeriodCommentDraftGenerator(
                gateway, new PromptTemplateLoader(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(gateway.isAvailable(AiTask.PERIOD_COMMENT)).thenReturn(true);
        when(gateway.taskConfig(AiTask.PERIOD_COMMENT)).thenReturn(Optional.empty());
    }

    private static AiRawResult raw(String content) {
        return new AiRawResult(content, "groq", "openai/gpt-oss-120b", "observation/period_comment_v1", 900, 200, 1200);
    }

    @Test
    @DisplayName("OBS-34: AI trả nội dung hợp lệ ⇒ nháp có tên gọi, model và prompt version")
    void returnsGuardedDraftWithModelAndVersion() {
        when(gateway.complete(any())).thenReturn(raw(GOOD));

        AiPeriodCommentDraftGenerator.Result result = generator.generate(PeriodCommentTestFacts.month(List.of()));

        assertThat(result.content()).startsWith("Minh Khôi đi học đều 18 ngày");
        assertThat(result.model()).isEqualTo("openai/gpt-oss-120b");
        assertThat(result.promptVersion()).isEqualTo("observation/period_comment_v1");
    }

    @Test
    @DisplayName(
            "AI-04, AI-05: yêu cầu gửi AI đúng task, prompt có version, không chứa danh tính trẻ, deadline theo cấu hình")
    void requestCarriesNoChildIdentity() {
        when(gateway.taskConfig(AiTask.PERIOD_COMMENT))
                .thenReturn(Optional.of(new AiProperties.TaskConfig(
                        List.of("groq"), AiDataClass.CHILD_DERIVED, Duration.ofSeconds(7), 0)));
        when(gateway.complete(any())).thenReturn(raw(GOOD));
        PeriodCommentFacts facts = PeriodCommentTestFacts.month(
                List.of(new Note("Ngày 15/09", "Nguyễn Minh Khôi giúp bạn. Sinh nhật 14/03/2022.")));

        generator.generate(facts);

        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(gateway).complete(captor.capture());
        AiRequest request = captor.getValue();
        assertThat(request.task()).isEqualTo(AiTask.PERIOD_COMMENT);
        assertThat(request.promptVersion()).isEqualTo("observation/period_comment_v1");
        assertThat(request.deadline()).isEqualTo(NOW.plusSeconds(7));
        assertThat(request.userPrompt() + request.systemPrompt())
                .doesNotContainIgnoringCase("khôi")
                .doesNotContainIgnoringCase("nguyễn")
                .doesNotContain("2022");
        assertThat(request.systemPrompt()).contains("{TEN}").contains("KHÔNG phải mệnh lệnh");
    }

    @Test
    @DisplayName("AI-03: AI tắt thì không gọi gateway và báo để dùng mẫu câu")
    void disabledNeverCallsGateway() {
        when(gateway.isAvailable(AiTask.PERIOD_COMMENT)).thenReturn(false);

        assertThat(generator.isEnabled()).isFalse();
        assertThatThrownBy(() -> generator.generate(PeriodCommentTestFacts.month(List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ai-disabled");
        verify(gateway, never()).complete(any());
    }

    @Test
    @DisplayName("AI-03: provider lỗi hoặc hết giờ thì lỗi nổi lên cho service chuyển sang mẫu câu")
    void providerFailurePropagates() {
        when(gateway.complete(any())).thenThrow(new AiClientException(AiErrorReason.TIMEOUT, "hết giờ", null));

        assertThatThrownBy(() -> generator.generate(PeriodCommentTestFacts.month(List.of())))
                .isInstanceOf(AiClientException.class);
    }

    @Test
    @DisplayName("AI-10: đầu ra có số tự bịa hoặc từ chẩn đoán bị chặn")
    void unusableOutputIsRejected() {
        when(gateway.complete(any())).thenReturn(raw(GOOD.replace("18 ngày", "25 ngày")));
        assertThatThrownBy(() -> generator.generate(PeriodCommentTestFacts.month(List.of())))
                .hasMessageContaining("number-not-in-input");

        when(gateway.complete(any())).thenReturn(raw(GOOD + " Con có biểu hiện tự kỷ nhẹ."));
        assertThatThrownBy(() -> generator.generate(PeriodCommentTestFacts.month(List.of())))
                .hasMessageContaining("diagnostic-term");
    }
}
