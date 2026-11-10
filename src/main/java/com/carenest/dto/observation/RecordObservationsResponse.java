package com.carenest.dto.observation;

import java.time.LocalDate;

public record RecordObservationsResponse(Long classroomId, LocalDate observedOn, int savedCount) {}
