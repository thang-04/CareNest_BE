package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduPlanGoal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class EduThemeGoalsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private static EduPlanGoal goal(long id, int no, String text) {
        EduPlanGoal g = mock(EduPlanGoal.class);
        when(g.getId()).thenReturn(id);
        when(g.getGoalNo()).thenReturn((short) no);
        when(g.getText()).thenReturn(text);
        return g;
    }

    private static EduGoalContent content(long id, long goalId, String text) {
        EduGoalContent c = EduGoalContent.create();
        c.place(goalId, (short) 0, text);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private final List<EduPlanGoal> goals = List.of(goal(1, 1, "MT một"), goal(2, 2, "MT hai"), goal(3, 3, "  "));
    private final Map<Long, List<EduGoalContent>> contents = Map.of(
            1L, List.of(content(11, 1, "Nội dung 1"), content(12, 1, " ")),
            2L, List.of(content(21, 2, "Nội dung 2")),
            3L, List.of(content(31, 3, "Nội dung 3")));

    @Test
    void themeGoalsAreGoalsWithTickedNonBlankContent() {
        assertThat(EduThemeGoals.ofTheme(goals, contents, Set.of(11L), null))
                .extracting(EduThemeGoals.GoalRef::goalNo)
                .containsExactly(1);
        // Nội dung rỗng được tích không làm MT thuộc chủ đề; MT không có chữ không tính
        assertThat(EduThemeGoals.ofTheme(goals, contents, Set.of(12L, 31L), null))
                .isEmpty();
    }

    @Test
    void customTicksOverrideContentThenGoal() {
        assertThat(EduThemeGoals.ofTheme(goals, contents, Set.of(11L), mapper.readTree("{\"2\":true}")))
                .extracting(EduThemeGoals.GoalRef::goalId)
                .containsExactly(1L, 2L);
        assertThat(EduThemeGoals.ofTheme(goals, contents, Set.of(11L), mapper.readTree("{\"11\":false}")))
                .isEmpty();
        assertThat(EduThemeGoals.ofTheme(goals, contents, Set.of(), mapper.readTree("{\"2\":false,\"21\":true}")))
                .extracting(EduThemeGoals.GoalRef::goalId)
                .containsExactly(2L);
    }
}
