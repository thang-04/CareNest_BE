package com.carenest.dto.response;

import com.carenest.entity.PickupStatus;

import java.time.Instant;

public record PickupPersonResponse(
        Long id,
        Long childId,
        String childName,
        String fullName,
        String relationship,
        String phoneNumber,
        // Presigned URL, valid for app.storage.presigned-url-expiration.
        String photoUrl,
        PickupStatus status,
        String rejectReason,
        Instant reviewedAt
) {
}
