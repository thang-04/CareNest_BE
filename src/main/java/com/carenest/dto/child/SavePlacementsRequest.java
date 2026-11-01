package com.carenest.dto.child;

import com.carenest.utils.enums.PlacementAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** toClassId bắt buộc với PROMOTE/STAY, bỏ qua với LEAVE/GRADUATE. */
public record SavePlacementsRequest(@NotEmpty List<@Valid Item> items) {

    public record Item(@NotNull Long childId, @NotNull PlacementAction action, Long toClassId) {}
}
