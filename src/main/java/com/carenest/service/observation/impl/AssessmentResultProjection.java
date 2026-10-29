package com.carenest.service.observation.impl;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.observation.DailySheetResponse.ActivityResult;
import com.carenest.entity.observation.DailyActivityResult;
import com.carenest.service.eduplan.EduLessonQueryService;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class AssessmentResultProjection {
    private AssessmentResultProjection() {}

    // EDU-26: dùng cùng phép chiếu cho bảng giáo viên và số liệu BGH, không sửa entity đã lưu.
    static Map<DailyActivityResult, ActivityResult> project(
            EduLessonQueryService query,
            Map<LocalDate, ApprovedDayLessonResponse> lessons,
            Collection<DailyActivityResult> results) {
        Set<Long> ids = new HashSet<>();
        results.forEach(r -> {
            ids.addAll(r.getGoalsMet());
            ids.addAll(r.getGoalsEffort());
            ids.addAll(r.getGoalsNotMet());
        });
        lessons.values()
                .forEach(lesson -> lesson.activities().forEach(a -> a.goals().forEach(g -> ids.add(g.id()))));
        Map<Long, Long> lineages = results.isEmpty() || lessons.isEmpty() ? Map.of() : query.findGoalLineages(ids);
        Map<LocalDate, Map<String, Map<Long, Long>>> targets = new HashMap<>();
        lessons.forEach((day, lesson) -> {
            Map<String, Map<Long, Long>> activities = new HashMap<>();
            lesson.activities().forEach(a -> {
                Map<Long, Long> goals = new HashMap<>();
                a.goals().forEach(g -> {
                    Long lineage = lineages.get(g.id());
                    if (lineage != null) goals.putIfAbsent(lineage, g.id());
                });
                activities.put(a.key(), goals);
            });
            targets.put(day, activities);
        });
        Map<DailyActivityResult, ActivityResult> projected = new HashMap<>();
        results.forEach(r -> {
            var day = targets.get(r.getDate());
            Map<Long, Long> offered = day == null ? null : day.getOrDefault(r.getActivityKey(), Map.of());
            projected.put(
                    r,
                    new ActivityResult(
                            r.getActivityKey(),
                            r.getParticipation(),
                            mapGoals(r.getGoalsMet(), lineages, offered),
                            mapGoals(r.getGoalsEffort(), lineages, offered),
                            mapGoals(r.getGoalsNotMet(), lineages, offered),
                            Objects.requireNonNullElse(r.getUpdatedAt(), r.getRecordedAt())));
        });
        return projected;
    }

    private static List<Long> mapGoals(List<Long> ids, Map<Long, Long> lineages, Map<Long, Long> offered) {
        if (offered == null) return ids;
        return ids.stream()
                .map(lineages::get)
                .filter(Objects::nonNull)
                .map(offered::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
