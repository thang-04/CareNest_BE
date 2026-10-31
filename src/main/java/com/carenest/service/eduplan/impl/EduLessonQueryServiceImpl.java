package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.eduplan.ApprovedDayLessonResponse.Activity;
import com.carenest.dto.eduplan.ApprovedDayLessonResponse.Goal;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.utils.enums.EduClassDocStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class EduLessonQueryServiceImpl implements EduLessonQueryService {

    private static final String LOG_PREFIX = "[EduLessonQueryServiceImpl]";
    private static final int SCHOOL_DAYS = 5;

    private final EduClassDocRepository classDocRepository;
    private final EduPlanGoalRepository goalRepository;
    private final EduConfigService eduConfigService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<LocalDate, ApprovedDayLessonResponse> findApprovedDayLessons(
            Long classroomId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|findApprovedDayLessons|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            // Giáo án tuần duyệt cùng hồ sơ chủ đề (EDU-17): chỉ bản đã duyệt mới dùng để chấm
            List<EduClassDoc> weeks =
                    classDocRepository.findAllByClassroomIdInOrderByIdAsc(List.of(classroomId)).stream()
                            .filter(d ->
                                    EduClassDoc.WEEK.equals(d.getKind()) && d.getStatus() == EduClassDocStatus.APPROVED)
                            .filter(d ->
                                    !d.getWeekStart().plusDays(SCHOOL_DAYS - 1L).isBefore(from)
                                            && !d.getWeekStart().isAfter(to))
                            .toList();
            Map<LocalDate, ApprovedDayLessonResponse> result = new LinkedHashMap<>();
            if (!weeks.isEmpty()) {
                List<EduConfigResponse.WeekRow> rows =
                        eduConfigService.getEduConfig().weekRows();
                List<JsonNode> contents =
                        weeks.stream().map(d -> readContent(d.getContent())).toList();
                Map<Long, EduPlanGoal> goals = goalsOf(contents);
                for (int w = 0; w < weeks.size(); w++) {
                    EduClassDoc doc = weeks.get(w);
                    for (int day = 0; day < SCHOOL_DAYS; day++) {
                        LocalDate date = doc.getWeekStart().plusDays(day);
                        if (!date.isBefore(from) && !date.isAfter(to)) {
                            result.put(date, dayLesson(doc, contents.get(w), day, date, rows, goals));
                        }
                    }
                }
            }
            log.info("{}|days={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> findGoalLineages(Collection<Long> goalIds) {
        String logPrefix = LOG_PREFIX + "|findGoalLineages";
        log.info("{}|START", logPrefix);
        try {
            Map<Long, Long> result = goalIds.isEmpty()
                    ? Map.of()
                    : goalRepository.findAllById(goalIds).stream()
                            .collect(Collectors.toMap(EduGoalRow::getId, EduGoalRow::lineage));
            log.info("{}|mapped={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private JsonNode readContent(String json) {
        return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
    }

    private Map<Long, EduPlanGoal> goalsOf(List<JsonNode> contents) {
        Set<Long> ids = new LinkedHashSet<>();
        contents.forEach(c -> c.path("picks")
                .forEach(list -> list.forEach(p -> {
                    Long id = goalId(p);
                    if (id != null) {
                        ids.add(id);
                    }
                })));
        return goalRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(EduGoalRow::getId, Function.identity()));
    }

    private static Long goalId(JsonNode pick) {
        String raw = pick.path("goalId").asString("");
        return raw.matches("\\d+") ? Long.valueOf(raw) : null;
    }

    /** Dòng gộp cả tuần dùng ô "all", ngược lại ô của ngày đó kèm phần chung "extra" (như màn giáo án tuần). */
    private static ApprovedDayLessonResponse dayLesson(
            EduClassDoc doc,
            JsonNode content,
            int day,
            LocalDate date,
            List<EduConfigResponse.WeekRow> rows,
            Map<Long, EduPlanGoal> goals) {
        List<Activity> activities = new ArrayList<>();
        for (EduConfigResponse.WeekRow row : rows) {
            JsonNode cell = content.path("grid").path(row.key());
            boolean merged = row.canMerge() && cell.path("allWeek").asBoolean(row.allWeek());
            List<String> keys =
                    merged ? List.of(row.key() + ".all") : List.of(row.key() + "." + day, row.key() + ".extra");
            List<Goal> picked = new ArrayList<>();
            Set<Long> seen = new LinkedHashSet<>();
            for (String key : keys) {
                content.path("picks").path(key).forEach(p -> {
                    Long id = goalId(p);
                    EduPlanGoal goal = id == null ? null : goals.get(id);
                    if (goal != null && seen.add(id)) {
                        picked.add(new Goal(id, goal.getGoalNo(), goal.getText()));
                    }
                });
            }
            if (!picked.isEmpty()) {
                activities.add(new Activity(row.key(), row.label(), topicOf(cell, merged, day), picked));
            }
        }
        long weekNo = content.path("weekNo").asLong(0);
        return new ApprovedDayLessonResponse(
                doc.getId(),
                doc.getWeekStart(),
                date,
                weekNo > 0 ? "Tuần " + weekNo : "Tuần " + (ChronoUnit.WEEKS.between(doc.getWeekStart(), date) + 1),
                activities);
    }

    private static String topicOf(JsonNode cell, boolean merged, int day) {
        if (merged) {
            return cell.path("common").asString("");
        }
        JsonNode v = cell.path("days").path(day);
        if (v.isString()) {
            return v.asString();
        }
        String subject = v.path("subject").asString("");
        String topic = v.path("topic").asString("");
        return subject.isEmpty() ? topic : topic.isEmpty() ? subject : subject + ": " + topic;
    }
}
