package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.entity.eduplan.EduContentThemeTick;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.exception.GlobalException;
import com.carenest.utils.enums.EduGoalRowKind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EduTeamAllocationTest {
    @Test
    void eachGoalNeedsItsOwnContentTickedIntoAValidTheme() {
        var goal1 = mock(EduGoalRow.class);
        var goal2 = mock(EduGoalRow.class);
        var content1 = mock(EduGoalContent.class);
        var content2 = mock(EduGoalContent.class);
        when(goal1.getKind()).thenReturn(EduGoalRowKind.GOAL);
        when(goal1.getId()).thenReturn(1L);
        when(goal2.getKind()).thenReturn(EduGoalRowKind.GOAL);
        when(goal2.getId()).thenReturn(2L);
        when(content1.getId()).thenReturn(11L);
        when(content1.getGoalRowId()).thenReturn(1L);
        when(content2.getId()).thenReturn(12L);
        when(content2.getGoalRowId()).thenReturn(2L);
        var rows = List.of(goal1, goal2);
        var contents = List.of(content1, content2);
        assertThatThrownBy(() -> EduTeamPlanServiceImpl.requireAllocatedGoals(
                        rows,
                        contents,
                        List.of(EduContentThemeTick.of(11L, 101L), EduContentThemeTick.of(12L, 999L)),
                        Set.of(101L)))
                .isInstanceOf(GlobalException.class);
        EduTeamPlanServiceImpl.requireAllocatedGoals(
                rows,
                contents,
                List.of(EduContentThemeTick.of(11L, 101L), EduContentThemeTick.of(12L, 101L)),
                Set.of(101L));
    }
}
