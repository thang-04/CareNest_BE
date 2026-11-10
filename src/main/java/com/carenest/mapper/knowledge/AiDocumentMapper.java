package com.carenest.mapper.knowledge;

import com.carenest.dto.knowledge.AiDocumentResponse;
import com.carenest.entity.knowledge.AiDocument;
import org.springframework.stereotype.Component;

@Component
public class AiDocumentMapper {

    public AiDocumentResponse toResponse(AiDocument document, long chunkCount) {
        return new AiDocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getDocumentType(),
                document.getSourceName(),
                document.getSourceUrl(),
                document.getRegion(),
                document.getEffectiveDate(),
                document.getExpiryDate(),
                document.getVersion(),
                document.getStatus(),
                chunkCount,
                document.getCreatedAt());
    }
}
