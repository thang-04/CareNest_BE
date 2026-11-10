package com.carenest.dto.observation;

import java.time.LocalDate;
import java.util.List;

public record ObservationResponse(
        Long id, Long childId, Long classroomId, LocalDate observedOn, String note, List<ValueResponse> values) {

    public record ValueResponse(Long criteriaId, String criteriaName, Long optionId, String optionLabel) {}
}
