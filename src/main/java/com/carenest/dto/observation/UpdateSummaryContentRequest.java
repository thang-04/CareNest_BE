package com.carenest.dto.observation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSummaryContentRequest(
        @NotBlank @Size(max = 5000) String content) {

    @Override
    public String toString() {
        return "UpdateSummaryContentRequest[length=" + (content == null ? 0 : content.length()) + "]";
    }
}
