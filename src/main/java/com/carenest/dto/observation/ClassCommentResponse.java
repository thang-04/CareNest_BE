package com.carenest.dto.observation;

import java.time.Instant;
import java.time.LocalDate;

public record ClassCommentResponse(
        Long classId, LocalDate date, String text, Long recordedBy, String recordedByName, Instant updatedAt) {}
