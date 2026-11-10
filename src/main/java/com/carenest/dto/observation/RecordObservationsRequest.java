package com.carenest.dto.observation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** GV nhập quan sát theo batch cả lớp cho một ngày; nhập lại cùng ngày thì ghi đè. */
public record RecordObservationsRequest(
        @NotNull LocalDate observedOn,
        @NotEmpty @Size(max = 60) List<@Valid @NotNull Entry> entries) {

    public record Entry(
            @NotNull Long childId,
            @NotNull @Size(max = 30) List<@Valid @NotNull Value> values,
            @Size(max = 1000) String note) {

        @Override
        public String toString() {
            return "Entry[childId=" + childId + ", values=" + values.size() + "]";
        }
    }

    public record Value(@NotNull Long criteriaId, @NotNull Long optionId) {}
}
