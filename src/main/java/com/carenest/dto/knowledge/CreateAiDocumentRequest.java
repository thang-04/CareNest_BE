package com.carenest.dto.knowledge;

import com.carenest.utils.enums.AiDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Nạp tài liệu tham khảo cho RAG. content là văn bản thuần/markdown (v1 chưa nhận PDF). region bỏ trống = ALL.
 * Chỉ nạp tài liệu thật từ nguồn chính thống (Bộ GD&ĐT, Viện Dinh dưỡng, quy định của trường).
 */
public record CreateAiDocumentRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull AiDocumentType documentType,
        @NotBlank @Size(max = 255) String sourceName,
        @Size(max = 1000) String sourceUrl,
        @Size(max = 50) @Pattern(regexp = "^[A-Za-z_]*$") String region,
        @NotNull LocalDate effectiveDate,
        LocalDate expiryDate,
        @Size(max = 50) String version,
        @NotBlank @Size(max = 200_000) String content) {

    @Override
    public String toString() {
        return "CreateAiDocumentRequest[title=" + title + ", documentType=" + documentType + "]";
    }
}
