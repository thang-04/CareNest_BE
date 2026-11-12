package com.carenest.entity.eduplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.EduPlanStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EduYearPlanEditTest {
    private final Instant now = Instant.parse("2026-10-27T00:00:00Z");

    @Test
    @DisplayName("EDU-54: kế hoạch duyệt giữ nguyên trạng thái REVIEW")
    void planInReviewHasReviewStatus() {
        EduPlan plan = EduPlan.createDraft("TEST", 1L, 1L, "", "", 1L, "", now);
        plan.submit(1L, now);

        assertThat(plan.getStatus()).isEqualTo(EduPlanStatus.REVIEW);
        assertThat(plan.publicStatus()).isEqualTo(EduPlanStatus.REVIEW);
    }
}
