package com.carenest.utils.enums;

/** Lý do lỗi khi gọi AI (LLM/embedding); service dùng để chọn fallback hoặc map sang ApiCode. */
public enum AiErrorReason {
    /** AI tắt bằng cấu hình hoặc thiếu base URL/model. */
    DISABLED,
    /** Provider không trả lời trong thời gian cho phép. */
    TIMEOUT,
    /** Provider trả 429 (hết quota/rate limit). */
    RATE_LIMITED,
    /** Không gọi được hoặc provider trả lỗi 4xx/5xx khác. */
    PROVIDER_UNAVAILABLE,
    /** Output không đúng schema hoặc vi phạm guard (bịa evidence, số liệu, từ chẩn đoán). */
    OUTPUT_INVALID
}
