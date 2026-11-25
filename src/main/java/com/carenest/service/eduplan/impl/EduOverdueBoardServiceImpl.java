package com.carenest.service.eduplan.impl;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduOverdueBoardService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.enums.EduPlanLevel;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Ghép dòng màn Kế hoạch quá hạn ở BE (edu-list-paging, Tiền 25/11/2026), cùng cách gom của Web trước đây
 * (models/education-plan/overdue.mjs): quyền mở và hạn do các service nguồn trả (canExtend / timeLock.canUnlock); EDU-109 (9)
 * một hạn hiệu lực = extendedTo ?? deadline; loại gửi/duyệt suy từ trạng thái (Chờ duyệt ⇒ duyệt).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduOverdueBoardServiceImpl implements EduOverdueBoardService {

    private static final String LOG_PREFIX = "[EduOverdueBoardServiceImpl]";

    private final EduYearPlanService yearPlanService;
    private final EduClassDocService classDocService;
    private final EduThemeRepository themeRepository;
    private final SchoolStructureService schoolStructureService;
    private final ObjectMapper objectMapper;
    private final java.time.Clock clock;

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Board getOverdueBoard(CurrentUser currentUser, Long schoolYearId, Filter filter) {
        String logPrefix =
                LOG_PREFIX + "|getOverdueBoard|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            List<AgeGroupResponse> ageGroups = schoolStructureService.findAgeGroups();
            Map<Long, AgeGroupResponse> ageById =
                    ageGroups.stream().collect(Collectors.toMap(AgeGroupResponse::id, a -> a, (a, b) -> a));
            List<ObjectNode> rows = new ArrayList<>();
            for (EduPlanLevel level : List.of(EduPlanLevel.SCHOOL_YEAR, EduPlanLevel.TEAM)) {
                for (YearPlanSummaryResponse plan : yearPlanService
                        .getOverdueYearPlans(currentUser, schoolYearId, null, null, Pageable.unpaged(), level)
                        .items()) {
                    planRow(plan, level, ageById, today).ifPresent(rows::add);
                }
            }
            ClassDocsResponse docs = classDocService.getOverdueClassDocs(currentUser, schoolYearId);
            Map<String, ClassDocsResponse.ClassItem> classes = docs.classes().stream()
                    .collect(Collectors.toMap(ClassDocsResponse.ClassItem::id, c -> c, (a, b) -> a));
            Map<Long, String> themeNames = themeNames(docs.themeDossiers());
            for (ObjectNode d : docs.semesterPackages())
                rows.add(classRow(d, "semester", classes, themeNames, docs.semesterPackages(), today));
            for (ObjectNode d : docs.themeDossiers())
                rows.add(classRow(d, "dossier", classes, themeNames, docs.semesterPackages(), today));
            rows.removeIf(r -> "APPROVED".equals(r.path("status").asString("")));

            String chosenRange = rangeOf(filter.ageGroupId(), ageGroups);
            List<ObjectNode> shown = rows.stream()
                    .filter(r -> matches(r, filter, chosenRange, ageGroups))
                    .toList();
            int size = Math.max(1, Math.min(filter.size(), 100));
            int page = Math.max(0, filter.page());
            int from = Math.min(page * size, shown.size());
            var pageRows = new PageResponse<>(
                    List.copyOf(shown.subList(from, Math.min(from + size, shown.size()))),
                    page,
                    size,
                    shown.size(),
                    (shown.size() + size - 1) / size);
            long overdue = rows.stream()
                    .filter(r -> r.path("overdue").asBoolean(false))
                    .count();
            long extended = rows.stream().filter(r -> r.hasNonNull("extension")).count();
            List<Integer> semesters = rows.stream()
                    .map(r -> r.path("semesterNo"))
                    .filter(n -> n.isNumber())
                    .map(JsonNode::asInt)
                    .distinct()
                    .sorted()
                    .toList();
            var result = new Board(pageRows, overdue, extended, semesters);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Optional<ObjectNode> planRow(
            YearPlanSummaryResponse plan, EduPlanLevel level, Map<Long, AgeGroupResponse> ageById, LocalDate today) {
        var lock = plan.timeLock();
        if (lock == null) return Optional.empty();
        LocalDate deadline = lock.extendedTo() != null ? lock.extendedTo() : lock.deadline();
        String deadlineKind = kindOf(plan.status() == null ? "" : plan.status().name());
        ObjectNode row = objectMapper.createObjectNode();
        row.put("key", "plan-" + plan.id());
        row.put("kind", level.name());
        row.put("planId", plan.id());
        row.putNull("classId");
        row.put("className", "—");
        AgeGroupResponse age = ageById.get(plan.ageGroupId());
        row.put("ageGroupId", age == null ? "" : age.code());
        row.put("title", plan.title());
        row.putNull("semesterNo");
        putDate(row, "deadline", deadline);
        Long days = lock.daysLeft() != null ? Math.max(0, -lock.daysLeft()) : daysSince(deadline, today);
        if (days == null) row.putNull("daysOverdue");
        else row.put("daysOverdue", days);
        row.put("status", plan.status() == null ? "" : plan.status().name());
        row.put("deadlineKind", deadlineKind);
        row.put("overdue", lock.canUnlock());
        row.put("extensionKind", deadlineKind);
        if (lock.extendedTo() != null)
            row.putObject("extension").put("deadline", lock.extendedTo().toString());
        else row.putNull("extension");
        row.set("doc", objectMapper.valueToTree(plan));
        return Optional.of(row);
    }

    private ObjectNode classRow(
            ObjectNode d,
            String kind,
            Map<String, ClassDocsResponse.ClassItem> classes,
            Map<Long, String> themeNames,
            List<ObjectNode> packages,
            LocalDate today) {
        ClassDocsResponse.ClassItem cls = classes.get(d.path("classId").asString(""));
        String deadline = d.hasNonNull("extendedTo")
                ? d.path("extendedTo").asString()
                : d.hasNonNull("deadline") ? d.path("deadline").asString() : null;
        String status = d.path("status").asString("");
        ObjectNode row = objectMapper.createObjectNode();
        row.put("key", kind + "-" + d.path("id").asString(""));
        row.put("kind", kind);
        row.put("docKey", d.path("id").asString(""));
        row.put("classId", d.path("classId").asString(""));
        row.put("className", cls == null ? "—" : cls.name());
        row.put("ageGroupId", cls == null || cls.ageGroupId() == null ? "" : cls.ageGroupId());
        row.put(
                "title",
                "semester".equals(kind)
                        ? "Học kỳ " + d.path("semesterNo").asString("")
                        : themeNames.getOrDefault(d.path("themeId").asLong(-1), "Chủ đề"));
        Integer semesterNo = "semester".equals(kind) ? intOrNull(d.path("semesterNo")) : semesterOf(d, packages);
        if (semesterNo == null) row.putNull("semesterNo");
        else row.put("semesterNo", semesterNo);
        if (deadline == null) row.putNull("deadline");
        else row.put("deadline", deadline);
        Long days = d.hasNonNull("daysLeft")
                ? Math.max(0, -d.path("daysLeft").asLong())
                : daysSince(deadline == null ? null : LocalDate.parse(deadline.substring(0, 10)), today);
        if (days == null) row.putNull("daysOverdue");
        else row.put("daysOverdue", days);
        row.put("status", status);
        row.set("version", d.path("version"));
        row.put("deadlineKind", kindOf(status));
        row.put("overdue", d.path("canExtend").asBoolean(false));
        row.put("extensionKind", kindOf(status));
        JsonNode ext = lastExtend(d.path("history"));
        if (ext != null) {
            ObjectNode e = row.putObject("extension");
            for (String field : List.of("deadline", "by", "note", "at"))
                if (ext.has(field)) e.set(field, ext.get(field));
        } else if (d.hasNonNull("extendedTo")) {
            row.putObject("extension").set("deadline", d.get("extendedTo"));
        } else {
            row.putNull("extension");
        }
        row.set("doc", d);
        return row;
    }

    private static boolean matches(ObjectNode r, Filter f, String chosenRange, List<AgeGroupResponse> ageGroups) {
        boolean extendedTab = "extended".equals(f.tab());
        if (extendedTab ? !r.hasNonNull("extension") : !r.path("overdue").asBoolean(false)) return false;
        if (notBlank(f.deadlineKind())
                && !f.deadlineKind()
                        .equals(r.path(extendedTab ? "extensionKind" : "deadlineKind")
                                .asString(""))) return false;
        if (chosenRange != null) {
            String code = r.path("ageGroupId").asString("");
            String range = ageGroups.stream()
                    .filter(a -> code.equals(a.code()))
                    .findFirst()
                    .map(EduOverdueBoardServiceImpl::range)
                    .orElse(null);
            if (!chosenRange.equals(range)) return false;
        }
        if (notBlank(f.classId()) && !f.classId().equals(r.path("classId").asString(""))) return false;
        if (notBlank(f.kind()) && !f.kind().equals(r.path("kind").asString(""))) return false;
        return f.semesterNo() == null || f.semesterNo() == r.path("semesterNo").asInt(-1);
    }

    /** Lọc nhóm tuổi theo khoảng tháng tuổi như ô lọc của Web (nhóm cùng khoảng coi là một). */
    private static String rangeOf(String code, List<AgeGroupResponse> ageGroups) {
        if (!notBlank(code)) return null;
        return ageGroups.stream()
                .filter(a -> code.equals(a.code()) || code.equals(String.valueOf(a.id())))
                .findFirst()
                .map(EduOverdueBoardServiceImpl::range)
                .orElse("missing:" + code);
    }

    private static String range(AgeGroupResponse a) {
        return a.ageFromMonths() == null || a.ageToMonths() == null
                ? "id:" + a.id()
                : a.ageFromMonths() + "-" + a.ageToMonths();
    }

    private Map<Long, String> themeNames(List<ObjectNode> dossiers) {
        List<Long> ids = dossiers.stream()
                .map(d -> d.path("themeId").asLong(-1))
                .filter(id -> id > 0)
                .distinct()
                .toList();
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) themeRepository.findAllById(ids).forEach(t -> names.put(t.getId(), t.getName()));
        return names;
    }

    private static Integer semesterOf(ObjectNode dossier, List<ObjectNode> packages) {
        String classId = dossier.path("classId").asString("");
        String planId = dossier.path("yearPlanId").asString("");
        String themeId = dossier.path("themeId").asString("");
        return packages.stream()
                .filter(p -> classId.equals(p.path("classId").asString(""))
                        && planId.equals(p.path("yearPlanId").asString(""))
                        && StreamSupport.stream(p.path("themeIds").spliterator(), false)
                                .anyMatch(t -> themeId.equals(t.asString(""))))
                .map(p -> intOrNull(p.path("semesterNo")))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static JsonNode lastExtend(JsonNode history) {
        JsonNode last = null;
        for (JsonNode h : history) {
            String action = h.path("action").asString("").toLowerCase(java.util.Locale.ROOT);
            if (action.equals("extend") || action.equals("reopen_review_deadline")) last = h;
        }
        return last;
    }

    private static String kindOf(String status) {
        return "REVIEW".equals(status) ? "review" : "send";
    }

    private static Long daysSince(LocalDate date, LocalDate today) {
        return date == null ? null : Math.max(0, ChronoUnit.DAYS.between(date, today));
    }

    private static Integer intOrNull(JsonNode n) {
        return n.isNumber() || (n.isString() && !n.asString().isBlank()) ? n.asInt() : null;
    }

    private static void putDate(ObjectNode row, String field, LocalDate date) {
        if (date == null) row.putNull(field);
        else row.put(field, date.toString());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
