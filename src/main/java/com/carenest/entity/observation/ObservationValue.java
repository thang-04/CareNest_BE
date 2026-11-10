package com.carenest.entity.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Giá trị một tiêu chí trong một bản quan sát. */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ObservationValue {

    @Column(name = "criteria_id", nullable = false)
    private Long criteriaId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    public static ObservationValue of(Long criteriaId, Long optionId) {
        ObservationValue value = new ObservationValue();
        value.criteriaId = criteriaId;
        value.optionId = optionId;
        return value;
    }
}
