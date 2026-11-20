package com.carenest.utils.enums;

/** Vòng đời nháp gợi ý thực đơn; {@code NO_FEASIBLE} là trạng thái cuối khi không có phương án qua cổng cứng. */
public enum MenuAiDraftStatus {
    PENDING_REVIEW,
    APPLIED,
    DISMISSED,
    NO_FEASIBLE
}
