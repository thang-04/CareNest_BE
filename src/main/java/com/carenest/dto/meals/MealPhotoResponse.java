package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import java.time.Instant;
import java.time.LocalDate;

/** Ảnh bữa ăn của điểm trường; tải ảnh qua {@code GET /nutrition/meal-photos/{id}/content}. */
public record MealPhotoResponse(
        Long id,
        Long campusId,
        LocalDate date,
        MealSession session,
        String contentType,
        long sizeBytes,
        Long uploadedBy,
        Instant uploadedAt) {}
