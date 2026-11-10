package com.carenest.dto.observation;

import java.util.List;

public record ObservationCriteriaResponse(Long id, String code, String name, List<OptionResponse> options) {

    public record OptionResponse(Long id, String code, String label, int score) {}
}
