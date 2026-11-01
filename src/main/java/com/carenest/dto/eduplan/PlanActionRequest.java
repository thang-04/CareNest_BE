package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ghi chú kèm thao tác gửi duyệt / ban hành (tùy chọn) và trả lại (bắt buộc, EDU-05). */
public final class PlanActionRequest {

    private PlanActionRequest() {}

    public record Note(@Size(max = 2000) String note) {}

    public record RequiredNote(@NotBlank @Size(max = 2000) String note) {}
}
