package com.carenest.dto.eduplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Lưu cấu hình kế hoạch giáo dục (EDU-11). domainLabels: tên lĩnh vực → tên hiển thị (rỗng = dùng tên gốc);
 * extraDomains: lĩnh vực trường thêm sau 5 lĩnh vực chuẩn, theo thứ tự hiển thị.
 */
public record EduConfigRequest(
        @NotNull Map<String, @Size(max = 150) String> domainLabels,
        @NotNull List<@NotBlank @Size(max = 150) String> extraDomains,
        @NotNull @Valid Signers signers,
        @NotNull @Size(min = 1) List<@Valid WeekRow> weekRows) {

    public record Signers(
            @NotNull List<String> year,
            @NotNull List<String> branch,
            @NotNull List<String> week) {}

    public record WeekRow(
            @NotBlank @Size(max = 40) String key,
            @NotBlank @Size(max = 150) String label,
            boolean canMerge,
            boolean allWeek,
            @Size(max = 150) String extraLabel) {}
}
