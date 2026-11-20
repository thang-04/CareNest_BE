package com.carenest.dto.child;

import com.carenest.utils.enums.PlacementAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** toClassId bắt buộc với PROMOTE/STAY, bỏ qua với LEAVE/GRADUATE. */
public record SavePlacementsRequest(List<@Valid Item> items, List<@Valid NewAdmissionItem> newAdmissions) {

    public SavePlacementsRequest {
        items = items == null ? List.of() : List.copyOf(items);
        newAdmissions = newAdmissions == null ? List.of() : List.copyOf(newAdmissions);
    }

    public SavePlacementsRequest(List<Item> items) {
        this(items, List.of());
    }

    public record Item(@NotNull Long childId, @NotNull PlacementAction action, Long toClassId) {}

    public record NewAdmissionItem(@NotNull Long admissionId, Long toClassId) {}
}
