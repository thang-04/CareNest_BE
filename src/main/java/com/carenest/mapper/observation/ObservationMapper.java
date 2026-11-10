package com.carenest.mapper.observation;

import com.carenest.dto.observation.ObservationCriteriaResponse;
import com.carenest.dto.observation.ObservationCriteriaResponse.OptionResponse;
import com.carenest.dto.observation.ObservationResponse;
import com.carenest.dto.observation.ObservationResponse.ValueResponse;
import com.carenest.entity.observation.CriteriaOption;
import com.carenest.entity.observation.Observation;
import com.carenest.entity.observation.ObservationCriteria;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ObservationMapper {

    public ObservationCriteriaResponse toCriteriaResponse(ObservationCriteria criteria, List<CriteriaOption> options) {
        return new ObservationCriteriaResponse(
                criteria.getId(),
                criteria.getCode(),
                criteria.getName(),
                options.stream()
                        .map(option -> new OptionResponse(
                                option.getId(), option.getCode(), option.getLabel(), option.getScore()))
                        .toList());
    }

    public ObservationResponse toObservationResponse(
            Observation observation,
            Map<Long, ObservationCriteria> criteriaById,
            Map<Long, CriteriaOption> optionsById) {
        List<ValueResponse> values = observation.getValues().stream()
                .map(value -> new ValueResponse(
                        value.getCriteriaId(),
                        nameOf(criteriaById.get(value.getCriteriaId())),
                        value.getOptionId(),
                        labelOf(optionsById.get(value.getOptionId()))))
                .toList();
        return new ObservationResponse(
                observation.getId(),
                observation.getChildId(),
                observation.getClassroomId(),
                observation.getObservedOn(),
                observation.getNote(),
                values);
    }

    private static String nameOf(ObservationCriteria criteria) {
        return criteria == null ? null : criteria.getName();
    }

    private static String labelOf(CriteriaOption option) {
        return option == null ? null : option.getLabel();
    }
}
