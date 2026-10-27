package com.carenest.entity.eduplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.EduPlanStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EduYearPlanEditTest {
    private final Instant now = Instant.parse("2026-10-27T00:00:00Z");

    @Test
    @DisplayName("EDU-10: khóa sửa không làm đổi trạng thái Chờ duyệt")
    void pendingPlanKeepsItsBusinessStatusWhileEditing() {
        EduYearPlan plan = EduYearPlan.createDraft("TEST", 1L, 1L, "", "", 1L, "", now);
        plan.submit(1L, now);
        plan.startEditing(2L, now);

        assertThat(plan.getStatus()).isEqualTo(EduPlanStatus.PENDING_VP);
        assertThat(plan.isEditingBy(2L)).isTrue();
        plan.finishEditing();
        assertThat(plan.getStatus()).isEqualTo(EduPlanStatus.PENDING_VP);
        assertThat(plan.getEditingBy()).isNull();
    }
}
