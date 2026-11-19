package com.carenest.service.eduplan.impl;

import com.carenest.entity.eduplan.EduContentThemeTick;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.repository.eduplan.EduContentThemeTickRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * MT của chủ đề / của năm cho kiểm EDU-80 (research R7). MT của chủ đề lọc như themeGoalBlocks của Web: MT có số, có nội dung
 * được tích cho chủ đề, sau khi áp {@code customTicks} (khóa id nội dung trước, rồi id MT).
 */
@Component
@RequiredArgsConstructor
class EduThemeGoals {
    record GoalRef(Long goalId, int goalNo) {}

    private final EduPlanGoalRepository goalRepository;
    private final EduGoalContentRepository contentRepository;
    private final EduContentThemeTickRepository tickRepository;

    List<GoalRef> ofYear(Long planId) {
        return numbered(goalRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId)).stream()
                .map(EduThemeGoals::ref)
                .toList();
    }

    List<GoalRef> ofTheme(Long planId, Long themeId, JsonNode customTicks) {
        List<EduPlanGoal> goals = numbered(goalRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId));
        Map<Long, List<EduGoalContent>> contents = goals.isEmpty()
                ? Map.of()
                : contentRepository
                        .findAllByGoalRowIdInOrderBySortOrderAsc(
                                goals.stream().map(EduPlanGoal::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(EduGoalContent::getGoalRowId));
        Set<Long> ticked = tickRepository.findAllByThemeIdIn(List.of(themeId)).stream()
                .map(EduContentThemeTick::getContentId)
                .collect(Collectors.toSet());
        return ofTheme(goals, contents, ticked, customTicks);
    }

    static List<GoalRef> ofTheme(
            List<EduPlanGoal> goals, Map<Long, List<EduGoalContent>> contents, Set<Long> ticked, JsonNode customTicks) {
        JsonNode custom = customTicks == null || !customTicks.isObject() ? null : customTicks;
        return numbered(goals).stream()
                .filter(g -> {
                    JsonNode goalTick = custom == null ? null : custom.get(String.valueOf(g.getId()));
                    return contents.getOrDefault(g.getId(), List.of()).stream()
                            .filter(c -> c.getText() != null && !c.getText().isBlank())
                            .anyMatch(c -> {
                                JsonNode own = custom == null ? null : custom.get(String.valueOf(c.getId()));
                                if (own != null && !own.isNull()) return own.asBoolean(false);
                                if (goalTick != null && !goalTick.isNull()) return goalTick.asBoolean(false);
                                return ticked.contains(c.getId());
                            });
                })
                .map(EduThemeGoals::ref)
                .toList();
    }

    // Web chỉ đánh số MT có chữ (numberGoals); MT trống không thuộc chủ đề, không phải dạy
    private static List<EduPlanGoal> numbered(List<EduPlanGoal> goals) {
        return goals.stream()
                .filter(g -> g.getGoalNo() != null
                        && g.getText() != null
                        && !g.getText().isBlank())
                .sorted(Comparator.comparing(EduPlanGoal::getGoalNo))
                .toList();
    }

    private static GoalRef ref(EduPlanGoal g) {
        return new GoalRef(g.getId(), g.getGoalNo());
    }
}
