package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.exception.GlobalException;
import com.carenest.utils.enums.EduGoalRowKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InheritedCurriculumGuardTest {
    @Test
    void teamCanEditAndAddContentButCannotRemoveOrImportForeignContent() {
        var plan = mock(EduPlan.class);
        when(plan.getCurriculumSourcePlanId()).thenReturn(1L);
        var goal = mock(EduGoalRow.class);
        when(goal.getId()).thenReturn(2L);
        when(goal.getDomainId()).thenReturn(3L);
        when(goal.getSortOrder()).thenReturn((short) 0);
        when(goal.getKind()).thenReturn(EduGoalRowKind.GOAL);
        when(goal.getText()).thenReturn("Mục tiêu");
        when(goal.getCurriculumId()).thenReturn(null);
        var content = mock(EduGoalContent.class);
        when(content.getId()).thenReturn(4L);
        var existing = Map.of(2L, List.of(content));
        var request = request(List.of(new YearPlanRequest.Content(4L, "Đã sửa", List.of()),
                new YearPlanRequest.Content(null, "Thêm mới", List.of())));
        InheritedCurriculumGuard.validateInheritedTree(plan, request, List.of(goal), existing);
        assertThatThrownBy(() -> InheritedCurriculumGuard.validateInheritedTree(plan, request(List.of()),
                List.of(goal), existing)).isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> InheritedCurriculumGuard.validateInheritedTree(plan,
                request(List.of(new YearPlanRequest.Content(99L, "Ngoại", List.of()))),
                List.of(goal), existing)).isInstanceOf(GlobalException.class);
    }

    private YearPlanRequest request(List<YearPlanRequest.Content> contents) {
        return new YearPlanRequest(1L, 1L, "Kế hoạch", "", List.of(new YearPlanRequest.Domain(3L,
                List.of(new YearPlanRequest.Row(2L, "goal", "Mục tiêu", contents)))), List.of(), List.of());
    }
}
