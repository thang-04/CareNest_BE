package com.carenest.integration.ai;

/**
 * Cổng AI duy nhất cho service nghiệp vụ (AI-06, ADR-0007): không gọi SDK nhà cung cấp trực tiếp. Kết quả chỉ là nháp, service
 * lưu kèm {@code source=AI}, model, thời điểm (AI-01). Lỗi ném {@link AiException}.
 */
public interface AiClient {

    /** Có cấu hình nhà cung cấp; false ⇒ gọi {@link #complete} luôn lỗi UNAVAILABLE (AI-03). */
    boolean isEnabled();

    AiResult complete(AiRequest request);

    /**
     * {@code task}: tên tác vụ để log metadata (AI-05). Prompt chỉ chứa dữ liệu tối thiểu, không tên, ngày sinh, ảnh trẻ
     * (AI-04) — service tự bảo đảm.
     */
    record AiRequest(String task, String systemPrompt, String userPrompt, int maxTokens) {
        @Override
        public String toString() {
            return "AiRequest[task=" + task + "]";
        }
    }

    record AiResult(String text, String model) {
        @Override
        public String toString() {
            return "AiResult[model=" + model + "]";
        }
    }
}
