package com.carenest.integration.ai;

import com.carenest.integration.ai.dto.SummaryDraftInput;
import com.carenest.integration.ai.dto.SummaryDraftResult;
import com.carenest.utils.enums.AiErrorReason;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Adapter khi AI tắt: caller nhận DISABLED và chuyển sang luồng template (OBS-04). */
@Component
@ConditionalOnProperty(prefix = "carenest.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiClient implements AiClient {

    @Override
    public SummaryDraftResult draftChildSummary(SummaryDraftInput input) {
        throw new AiClientException(AiErrorReason.DISABLED, "AI is disabled", null);
    }
}
