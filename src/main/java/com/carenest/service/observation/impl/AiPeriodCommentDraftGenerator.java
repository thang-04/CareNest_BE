package com.carenest.service.observation.impl;

import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.AiRawResult;
import com.carenest.integration.ai.AiRequest;
import com.carenest.integration.ai.PromptTemplateLoader;
import com.carenest.utils.enums.AiTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Nháp nhận xét tháng / học kỳ / cuối năm do AI viết (OBS-34, AI-03): chỉ gửi số liệu kỳ và nhận xét tự do của GV đã
 * lọc tên, ngày sinh (AI-04, AI-08). AI tắt, lỗi hoặc đầu ra không qua {@link PeriodCommentGuard} thì ném
 * {@link IllegalStateException} để service dùng mẫu câu.
 */
@Component
@RequiredArgsConstructor
class AiPeriodCommentDraftGenerator {

    static final String PROMPT_VERSION = "observation/period_comment_v1";
    private static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(20);
    private static final int MODEL_MAX = 100;

    private final AiGateway aiGateway;
    private final PromptTemplateLoader promptLoader;
    private final Clock clock;

    /** Nội dung đã qua kiểm, model và prompt version lưu kèm nháp (AI-01). */
    record Result(String content, String model, String promptVersion) {}

    boolean isEnabled() {
        return aiGateway.isAvailable(AiTask.PERIOD_COMMENT);
    }

    Result generate(PeriodCommentFacts facts) {
        if (!isEnabled()) {
            throw new IllegalStateException("ai-disabled");
        }
        String userPrompt = PeriodCommentPrompt.render(facts);
        Duration deadline = aiGateway
                .taskConfig(AiTask.PERIOD_COMMENT)
                .map(AiProperties.TaskConfig::deadline)
                .orElse(DEFAULT_DEADLINE);
        AiRawResult raw = aiGateway.complete(new AiRequest(
                AiTask.PERIOD_COMMENT,
                PROMPT_VERSION,
                promptLoader.load(PROMPT_VERSION),
                userPrompt,
                Instant.now(clock).plus(deadline)));
        String content =
                PeriodCommentGuard.check(raw.content(), userPrompt, ChildTextScrubber.givenName(facts.childFullName()));
        String model = raw.model() == null || raw.model().length() <= MODEL_MAX
                ? raw.model()
                : raw.model().substring(0, MODEL_MAX);
        return new Result(content, model, PROMPT_VERSION);
    }
}
