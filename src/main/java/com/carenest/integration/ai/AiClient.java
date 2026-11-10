package com.carenest.integration.ai;

import com.carenest.integration.ai.dto.SummaryDraftInput;
import com.carenest.integration.ai.dto.SummaryDraftResult;

/**
 * Port gọi LLM theo task (ADR-0007). Mọi lỗi ném {@link AiClientException}; caller phải có luồng không-AI
 * (ADR-0008). Kết quả luôn là nháp đã qua guard, chưa phải dữ liệu chính thức.
 */
public interface AiClient {

    SummaryDraftResult draftChildSummary(SummaryDraftInput input);
}
