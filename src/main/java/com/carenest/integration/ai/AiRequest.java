package com.carenest.integration.ai;

import com.carenest.utils.enums.AiTask;
import java.time.Instant;
import java.util.Objects;

/**
 * Một lần gọi LLM. {@code promptVersion} là tên file prompt (vd. "menu/weekly_v1") lưu kèm nháp; {@code deadline}
 * là mốc cuối cho cả chuỗi provider, không phải cho từng provider.
 */
public record AiRequest(AiTask task, String promptVersion, String systemPrompt, String userPrompt, Instant deadline) {

    public AiRequest {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        Objects.requireNonNull(deadline, "deadline");
    }
}
