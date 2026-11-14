package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.eduplan.ApprovedDayLessonResponse.Activity;
import com.carenest.dto.eduplan.ApprovedDayLessonResponse.Goal;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.repository.eduplan.EduClassDocReleaseRepository;
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
    private final EduClassDocReleaseRepository releaseRepository;
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
            // EDU-19 + spec 003 FR-008: chấm theo tuần có kế hoạch tuần và giáo án ngày đã ban hành (giáo án ngày duyệt
            // trong
            // hồ sơ chủ đề); hồ sơ mở lại sửa phần tương lai vẫn giữ ngày đã dạy (phần đã qua khóa sửa, EDU-64)
            List<EduClassDoc> docs = classDocRepository.findAllByClassroomIdInOrderByIdAsc(List.of(classroomId));
            Map<String, String> releasedContents = releasedContents(docs);
            Map<String, EduClassDoc> releasedDays = docs.stream()
                    .filter(d -> EduClassDoc.DAY.equals(d.getKind()) && released(d))
                    .collect(Collectors.toMap(d -> d.getYearPlanId() + ":" + d.getWeekStart(), d -> d, (a, b) -> a));
            List<EduClassDoc> weeks = docs.stream()
                    .filter(d -> EduClassDoc.WEEK.equals(d.getKind())
                            && released(d)
                            && releasedDays.containsKey(d.getYearPlanId() + ":" + d.getWeekStart()))
                    .filter(d -> !d.getWeekStart().plusDays(SCHOOL_DAYS - 1L).isBefore(from)
                            && !d.getWeekStart().isAfter(to))
                    .toList();
            Map<LocalDate, ApprovedDayLessonResponse> result = new LinkedHashMap<>();
            if (!weeks.isEmpty()) {
                List<EduConfigResponse.WeekRow> rows =
                        eduConfigService.getEduConfig().weekRows();
                // EDU-79 (FR-029): giáo án ngày đã ban hành có bản tuần sửa theo hồ sơ chủ đề thì dùng bản đó
                List<JsonNode> contents = new ArrayList<>();
                List<Map<String, JsonNode>> cards = new ArrayList<>();
                for (EduClassDoc week : weeks) {
                    JsonNode dayWeek = readContent(effectiveContent(
                                    releasedDays.get(week.getYearPlanId() + ":" + week.getWeekStart()),
                                    releasedContents))
                            .path("week");
                    boolean dossier = dayWeek.isObject();
                    contents.add(dossier ? dayWeek : readContent(effectiveContent(week, releasedContents)));
                    JsonNode theme = docs.stream()
                            .filter(d -> EduClassDoc.THEME.equals(d.getKind())
                                    && d.getYearPlanId().equals(week.getYearPlanId())
                                    && d.getThemeId() != null
                                    && d.getThemeId().equals(week.getThemeId()))
                            .findFirst()
                            .map(d -> readContent(effectiveContent(d, releasedContents)))
                            .orElse(null);
                    cards.add(EduActivityCards.cards(
                            theme instanceof tools.jackson.databind.node.ObjectNode o
                                    ? EduActivityCards.normalizeTheme(o, rows, null)
                                    : null,
                            dossier ? dayWeek : null));
                }
                Map<Long, EduPlanGoal> goals = goalsOf(contents, cards);
                for (int w = 0; w < weeks.size(); w++) {
                    EduClassDoc doc = weeks.get(w);
                    for (int day = 0; day < SCHOOL_DAYS; day++) {
                        LocalDate date = doc.getWeekStart().plusDays(day);
                        if (!date.isBefore(from) && !date.isAfter(to)) {
                            result.put(date, dayLesson(doc, contents.get(w), cards.get(w), day, date, rows, goals));
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

    /** Bản đang điều chỉnh (Nháp sau "revise_future") chưa duyệt ⇒ đánh giá dùng nội dung của lần ban hành gần nhất. */
    private static String effectiveContent(EduClassDoc doc, Map<String, String> releasedContents) {
        if (doc.getStatus() == EduClassDocStatus.APPROVED) return doc.getContent();
        // ponytail: dữ liệu cũ chưa có bản ban hành trong bảng releases thì giữ nội dung hiện tại như trước
        return releasedContents.getOrDefault(doc.getDocKey(), doc.getContent());
    }

    /** docKey → nội dung thành viên trong lần ban hành gần nhất của gói học kỳ / hồ sơ chủ đề. */
    private Map<String, String> releasedContents(List<EduClassDoc> docs) {
        if (docs.stream().noneMatch(d -> d.getStatus() != EduClassDocStatus.APPROVED && released(d))) return Map.of();
        List<Long> rootIds = docs.stream()
                .filter(d -> EduClassDoc.isRoot(d.getKind()))
                .map(EduClassDoc::getId)
                .toList();
        Map<String, String> result = new LinkedHashMap<>();
        // releases xếp theo thời gian tăng dần ⇒ lần ban hành sau ghi đè lần trước
        releaseRepository
                .findAllByClassDocIdInOrderByReleasedAtAscIdAsc(rootIds)
                .forEach(r -> readContent(r.getPrintSnapshot()).path("members").forEach(m -> {
                    if (m.hasNonNull("docKey"))
                        result.put(
                                m.path("docKey").asString(), m.path("content").toString());
                }));
        return result;
    }

    private static boolean released(EduClassDoc doc) {
        return doc.getStatus() == EduClassDocStatus.APPROVED
                || (doc.getHistory() != null && doc.getHistory().contains("\"revise_future\""));
    }

    private Map<Long, EduPlanGoal> goalsOf(List<JsonNode> contents, List<Map<String, JsonNode>> cards) {
        Set<Long> ids = new LinkedHashSet<>();
        for (int i = 0; i < contents.size(); i++) {
            Map<String, JsonNode> byId = cards.get(i);
            contents.get(i)
                    .path("picks")
                    .forEach(list -> list.forEach(p -> ids.addAll(EduActivityCards.resolveGoals(p, byId))));
        }
        return goalRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(EduGoalRow::getId, Function.identity()));
    }

    /** Dòng gộp cả tuần dùng ô "all", ngược lại ô của ngày đó kèm phần chung "extra" (như màn giáo án tuần). */
    private static ApprovedDayLessonResponse dayLesson(
            EduClassDoc doc,
            JsonNode content,
            Map<String, JsonNode> cards,
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
                // FR-026: MT của lượt xếp = MT của thẻ; gõ tay không có MT nên không sinh mục đánh giá
                content.path("picks")
                        .path(key)
                        .forEach(p -> EduActivityCards.resolveGoals(p, cards).forEach(id -> {
                            EduPlanGoal goal = goals.get(id);
                            if (goal != null && seen.add(id)) {
                                picked.add(new Goal(id, goal.getGoalNo(), goal.getText()));
                            }
                        }));
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
