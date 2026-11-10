package com.carenest.dto.knowledge;

import com.carenest.utils.enums.AiDocumentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAiDocumentStatusRequest(@NotNull AiDocumentStatus status) {}
