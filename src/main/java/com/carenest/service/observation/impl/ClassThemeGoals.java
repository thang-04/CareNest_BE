package com.carenest.service.observation.impl;

import com.carenest.repository.observation.ThemeEvaluationRepository;
import com.carenest.repository.observation.ThemeEvaluationRepository.GoalRow;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.eduplan.EduLessonQueryService.ThemeGoal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** MT của chủ đề dùng để đánh giá trẻ: theo kế hoạch chủ đề đã ban hành của lớp, lớp chưa có thì theo kế hoạch tổ. */
@Component
@RequiredArgsConstructor
class ClassThemeGoals {
    private final EduLessonQueryService lessonQueryService;
    private final ThemeEvaluationRepository repository;

    List<GoalRow> of(Long classroomId, Long themeId) {
        return lessonQueryService
                .findClassThemeGoals(classroomId, themeId)
                .map(goals -> goals.stream().map(ClassThemeGoals::row).toList())
                .orElseGet(() -> repository.findThemeGoals(themeId));
    }

    private static GoalRow row(ThemeGoal goal) {
        return new GoalRow() {
            @Override
            public Long getId() {
                return goal.id();
            }

            @Override
            public Long getLineage() {
                return goal.lineage();
            }

            @Override
            public Short getGoalNo() {
                return goal.goalNo();
            }

            @Override
            public String getText() {
                return goal.text();
            }
        };
    }
}
