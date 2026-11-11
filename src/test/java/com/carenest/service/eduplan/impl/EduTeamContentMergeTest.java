package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EduTeamContentMergeTest {
    @Test
    void localChangeSurvivesYearChangesButUnchangedContentFollowsYear() {
        assertThat(EduTeamPlanServiceImpl.mergedContent("Hát A", "Hát B", "Hát C"))
                .isEqualTo("Hát B");
        assertThat(EduTeamPlanServiceImpl.mergedContent("Hát A", "Hát A", "Hát C"))
                .isEqualTo("Hát C");
        assertThat(EduTeamPlanServiceImpl.mergedContent("Hát A", "Hát B", "Hát A"))
                .isEqualTo("Hát B");
    }
}
