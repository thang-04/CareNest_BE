package com.carenest.dto.knowledge;

import com.carenest.utils.enums.AiDocumentStatus;
import com.carenest.utils.enums.AiDocumentType;
import java.time.Instant;
import java.time.LocalDate;

public record AiDocumentResponse(
        Long id,
        String title,
        AiDocumentType documentType,
        String sourceName,
        String sourceUrl,
        String region,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        String version,
        AiDocumentStatus status,
        long chunkCount,
        Instant createdAt) {}
