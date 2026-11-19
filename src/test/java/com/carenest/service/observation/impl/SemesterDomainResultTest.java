package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.observation.SemesterAssessmentDtos.Goal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemesterDomainResultTest {
    private Goal g(Boolean met) {
        return new Goal(1L, (short) 1, "TEST", met, met == null ? 0 : met ? 1 : 0, met == null ? 0 : 1);
    }

    @Test
    @DisplayName("OBS-25: lĩnh vực đạt đúng 50%, dưới 50% chưa đạt, thiếu để trống")
    void computesHalfWithoutRoundingOrTreatingMissingAsFailure() {
        assertThat(SemesterAssessmentServiceImpl.summarize(1L, "D", List.of(g(true), g(false)))
                        .met())
                .isTrue();
        assertThat(SemesterAssessmentServiceImpl.summarize(1L, "D", List.of(g(true), g(false), g(false)))
                        .met())
                .isFalse();
        assertThat(SemesterAssessmentServiceImpl.summarize(1L, "D", List.of(g(true), g((Boolean) null)))
                        .met())
                .isNull();
        assertThat(SemesterAssessmentServiceImpl.summarize(1L, "D", List.of()).met())
                .isNull();
    }
}
