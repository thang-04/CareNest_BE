package com.carenest.dto.response;

import java.time.Instant;
import java.util.List;

public record ChildDetailResponse(
        ChildResponse child,
        Instant createdAt,
        // All pickup persons, whatever their status.
        List<PickupPersonResponse> pickupPersons
) {
}
