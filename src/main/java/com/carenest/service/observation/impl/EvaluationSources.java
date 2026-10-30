package com.carenest.service.observation.impl;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.observation.EvaluationDtos.DayHealth;
import com.carenest.dto.observation.EvaluationDtos.EvaluationDay;
import com.carenest.dto.observation.EvaluationDtos.GoalCount;
import com.carenest.dto.observation.EvaluationDtos.GoalRef;
import com.carenest.dto.observation.EvaluationDtos.GoalSummary;
import com.carenest.dto.observation.EvaluationDtos.HealthSummary;
import com.carenest.dto.observation.EvaluationDtos.StructuredSummary;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.DailyActivityResult;
import com.carenest.entity.observation.DailyObservation;
import com.carenest.repository.observation.DailyActivityResultRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Số liệu nguồn của đánh giá tháng / cuối năm, đọc từ điểm danh, đánh giá hằng ngày (chỉ ngày trẻ có mặt) và giáo án đã
 * duyệt (tên MT). Chỉ đọc; service gọi đã kiểm quyền.
 */
@Component
@RequiredArgsConstructor
class EvaluationSources {

    private final AttendanceService attendanceService;
    private final DailyObservationRepository observationRepository;
    private final DailyActivityResultRepository activityResultRepository;
    private final DailyHealthService dailyHealthService;
    private final EduLessonQueryService eduLessonQueryService;
    private final ObjectMapper objectMapper;

    /** days rỗng khi không yêu cầu (danh sách / cuối năm); presentDates / flagDates cho biểu đồ tuần. */
    record ChildData(
            int presentDays,
            int flagCount,
            StructuredSummary structured,
            List<EvaluationDay> days,
            Set<LocalDate> presentDates,
            Set<LocalDate> flagDates) {}

    Map<Long, ChildData> load(
            ClassroomInfoResponse cls, Collection<Long> childIds, LocalDate from, LocalDate to, boolean withDays) {
        if (childIds.isEmpty() || to.isBefore(from)) {
            return Map.of();
        }
        Set<String> present = attendanceService.findDayStatuses(childIds, from, to).stream()
                .filter(s -> s.status() == AttendanceStatus.PRESENT)
                .map(s -> key(s.childId(), s.date()))
                .collect(Collectors.toSet());
        Map<String, DailyObservation> observations = new HashMap<>();
        observationRepository.findByChildIdInAndDateBetween(childIds, from, to).stream()
                .filter(o -> present.contains(key(o.getChildId(), o.getDate())))
                .forEach(o -> observations.put(key(o.getChildId(), o.getDate()), o));
        Map<String, List<DailyActivityResult>> results =
                activityResultRepository.findByChildIdInAndDateBetween(childIds, from, to).stream()
                        .filter(r -> present.contains(key(r.getChildId(), r.getDate())))
                        .collect(Collectors.groupingBy(r -> key(r.getChildId(), r.getDate())));
        Map<String, DailyHealthService.ChildDaySigns> health = new HashMap<>();
        dailyHealthService
                .findLatestDaySigns(childIds, from, to)
                .forEach(h -> health.put(key(h.childId(), h.date()), h));
        Map<Long, ApprovedDayLessonResponse.Goal> goals = results.isEmpty() ? Map.of() : goalsOf(cls.id(), from, to);

        Map<Long, ChildData> data = new LinkedHashMap<>();
        for (Long childId : childIds) {
            Set<LocalDate> presentDates = present.stream()
                    .filter(k -> k.startsWith(childId + "|"))
                    .map(EvaluationSources::dateOf)
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<LocalDate> dates = new TreeSet<>();
            observations.keySet().stream()
                    .filter(k -> k.startsWith(childId + "|"))
                    .forEach(k -> dates.add(dateOf(k)));
            results.keySet().stream().filter(k -> k.startsWith(childId + "|")).forEach(k -> dates.add(dateOf(k)));
            data.put(
                    childId,
                    summarize(cls, childId, presentDates, dates, observations, results, health, goals, withDays));
        }
        return data;
    }

    @SuppressWarnings("java:S107")
    private ChildData summarize(
            ClassroomInfoResponse cls,
            Long childId,
            Set<LocalDate> presentDates,
            Set<LocalDate> dates,
            Map<String, DailyObservation> observations,
            Map<String, List<DailyActivityResult>> results,
            Map<String, DailyHealthService.ChildDaySigns> health,
            Map<Long, ApprovedDayLessonResponse.Goal> goals,
            boolean withDays) {
        int healthDays = 0;
        int normalDays = 0;
        Set<LocalDate> flagDates = new TreeSet<>();
        Map<HealthSymptom, Integer> signDays = new EnumMap<>(HealthSymptom.class);
        Map<MealSession, Map<EatingLevel, Integer>> meals = new EnumMap<>(MealSession.class);
        for (MealSession s : cls.mealSessions()) {
            Map<EatingLevel, Integer> levels = new EnumMap<>(EatingLevel.class);
            for (EatingLevel l : EatingLevel.values()) {
                levels.put(l, 0);
            }
            meals.put(s, levels);
        }
        Map<NapLevel, Integer> nap = new EnumMap<>(NapLevel.class);
        Map<Emotion, Integer> emotion = new EnumMap<>(Emotion.class);
        Map<ObservedBehavior, Integer> behaviors = new EnumMap<>(ObservedBehavior.class);
        Map<Participation, Integer> participation = new EnumMap<>(Participation.class);
        Map<Long, int[]> goalCounts = new HashMap<>();
        List<EvaluationDay> days = new ArrayList<>();

        for (LocalDate date : dates) {
            String k = key(childId, date);
            DailyObservation o = observations.get(k);
            List<DailyActivityResult> rs = results.getOrDefault(k, List.of());
            DayHealth dayHealth = null;
            Map<MealSession, EatingLevel> dayMeals = Map.of();
            if (o != null) {
                DailyHealthService.ChildDaySigns h = health.get(k);
                dayHealth = o.isHealthOverridden()
                        ? new DayHealth(o.getHealthSigns(), o.getHealthNote())
                        : new DayHealth(h == null ? List.of() : h.signs(), h == null ? "" : h.note());
                healthDays++;
                if (dayHealth.signs().isEmpty()) {
                    normalDays++;
                }
                dayHealth.signs().forEach(s -> signDays.merge(s, 1, Integer::sum));
                dayMeals = mealsOf(o.getMeals());
                dayMeals.forEach((s, l) -> {
                    if (meals.containsKey(s)) {
                        meals.get(s).merge(l, 1, Integer::sum);
                    }
                });
                count(nap, o.getNap());
                count(emotion, o.getEmotion());
                count(participation, o.getParticipation());
                o.getBehaviors().forEach(b -> behaviors.merge(b, 1, Integer::sum));
                if (Boolean.TRUE.equals(o.getFlag())) {
                    flagDates.add(date);
                }
            }
            List<GoalRef> met = new ArrayList<>();
            List<GoalRef> effort = new ArrayList<>();
            List<GoalRef> notMet = new ArrayList<>();
            for (DailyActivityResult r : rs) {
                tally(r.getGoalsMet(), 0, goalCounts, goals, met);
                tally(r.getGoalsEffort(), 1, goalCounts, goals, effort);
                tally(r.getGoalsNotMet(), 2, goalCounts, goals, notMet);
            }
            if (withDays) {
                days.add(new EvaluationDay(
                        date,
                        dayHealth,
                        o == null ? null : dayMeals,
                        o == null ? null : o.getNap(),
                        o == null ? null : o.getEmotion(),
                        o == null ? List.of() : o.getBehaviors(),
                        o == null ? "" : o.getBehaviorNote(),
                        met,
                        effort,
                        notMet,
                        o == null ? "" : o.getComment()));
            }
        }
        Map<String, GoalCount> byGoal = new LinkedHashMap<>();
        goalCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparingInt((Long id) -> goalNo(goals, id)).thenComparing(id -> id)))
                .forEach(e -> byGoal.put(
                        goalName(goals, e.getKey()), new GoalCount(e.getValue()[0], e.getValue()[1], e.getValue()[2])));
        StructuredSummary structured = new StructuredSummary(
                dates.size(),
                new HealthSummary(healthDays, normalDays, signDays, Map.of(), Map.of()),
                meals,
                nap,
                emotion,
                behaviors,
                participation,
                new GoalSummary(byGoal));
        return new ChildData(presentDates.size(), flagDates.size(), structured, days, presentDates, flagDates);
    }

    private void tally(
            List<Long> ids,
            int slot,
            Map<Long, int[]> counts,
            Map<Long, ApprovedDayLessonResponse.Goal> goals,
            List<GoalRef> refs) {
        if (ids == null) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id != null && seen.add(id)) {
                counts.computeIfAbsent(id, x -> new int[3])[slot]++;
                ApprovedDayLessonResponse.Goal g = goals.get(id);
                refs.add(new GoalRef(id, shortName(goals, id), g == null ? "" : g.text()));
            }
        }
    }

    private Map<Long, ApprovedDayLessonResponse.Goal> goalsOf(Long classId, LocalDate from, LocalDate to) {
        Map<Long, ApprovedDayLessonResponse.Goal> goals = new HashMap<>();
        eduLessonQueryService.findApprovedDayLessons(classId, from, to).values().stream()
                .flatMap(d -> d.activities().stream())
                .flatMap(a -> a.goals().stream())
                .forEach(g -> goals.putIfAbsent(g.id(), g));
        return goals;
    }

    // MT12 (tên ngắn trên chip); bảng tổng hợp dùng "MT12 – nội dung"
    private static String shortName(Map<Long, ApprovedDayLessonResponse.Goal> goals, Long id) {
        ApprovedDayLessonResponse.Goal g = goals.get(id);
        return g == null || g.goalNo() == null ? "MT" : "MT" + g.goalNo();
    }

    private static String goalName(Map<Long, ApprovedDayLessonResponse.Goal> goals, Long id) {
        ApprovedDayLessonResponse.Goal g = goals.get(id);
        if (g == null) {
            return "MT (mã " + id + ")";
        }
        return shortName(goals, id) + " – " + g.text();
    }

    private static int goalNo(Map<Long, ApprovedDayLessonResponse.Goal> goals, Long id) {
        ApprovedDayLessonResponse.Goal g = goals.get(id);
        return g == null || g.goalNo() == null ? Integer.MAX_VALUE : g.goalNo();
    }

    private Map<MealSession, EatingLevel> mealsOf(String json) {
        return json == null || json.isBlank()
                ? Map.of()
                : objectMapper.readValue(json, new TypeReference<Map<MealSession, EatingLevel>>() {});
    }

    private static <E extends Enum<E>> void count(Map<E, Integer> map, E value) {
        if (value != null) {
            map.merge(value, 1, Integer::sum);
        }
    }

    private static String key(Long childId, LocalDate date) {
        return childId + "|" + date;
    }

    private static LocalDate dateOf(String key) {
        return LocalDate.parse(key.substring(key.indexOf('|') + 1));
    }
}
