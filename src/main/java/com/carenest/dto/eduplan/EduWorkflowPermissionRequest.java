package com.carenest.dto.eduplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Lưu tab Soạn/duyệt hồ sơ: đủ 4 loại hồ sơ; lý do ghi vào lịch sử quyền (AUTH-14). */
public record EduWorkflowPermissionRequest(
        @NotNull Long revision,
        @NotNull @Size(min = 4, max = 4) List<@Valid @NotNull Document> documents,
        @NotBlank @Size(max = 500) String reason) {
    public record Document(
            @NotBlank String docType,
            @NotNull List<String> creators,
            @NotNull List<String> reviewers,
            @NotNull List<String> editRoles,
            @NotNull List<String> approveRoles) {}
}
