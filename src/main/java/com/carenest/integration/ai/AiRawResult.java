package com.carenest.integration.ai;

/** Nội dung thô provider trả về (chưa kiểm); domain phải guard trước khi dùng (AI-01, ADR-0008). */
public record AiRawResult(
        String content,
        String providerId,
        String model,
        String promptVersion,
        Integer inputTokens,
        Integer outputTokens,
        long latencyMs) {}
