package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.BranchFilePreviewResponse;
import com.carenest.dto.eduplan.ClassCalendarPreviewRequest;
import com.carenest.dto.eduplan.ClassCalendarPreviewResponse;
import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.dto.eduplan.EduDurationPolicyResponse;
import com.carenest.dto.eduplan.YearPlanChangesResponse;
import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.entity.eduplan.EduBreak;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduClassDocRelease;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduBreakRepository;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduSettingsRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.service.eduplan.EduDurationPolicyService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.EduPermission;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduClassDocStatus;
import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class EduClassDocServiceImpl implements EduClassDocService {
    private static final String LOG_PREFIX = "[EduClassDocServiceImpl]";

    private record ClassListContext(
            Map<Long, ClassroomInfoResponse> classes,
            Map<String, EduWorkflowPolicy.RolePolicy> policies,
            long policyRevision,
            EduPlanAccessPolicy.ClassScopeSnapshot scope,
            Map<Long, Set<Long>> authors,
            Map<Long, EduPlan> plans,
            Set<Long> supersededPlanIds,
            Map<Long, EduClassDoc> docsById,
            Map<String, EduClassDoc> docsByKey,
            Map<Long, SchoolYearDetailResponse> years,
            Map<Long, LocalDate> opened) {}

    private static final String ACTION_SUBMIT = "submit";
    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_REJECT = "reject";
    // Spec 003: HT/PHT mở hạn gói/hồ sơ quá hạn
    private static final String ACTION_EXTEND = "extend";
    private static final String MODE_SEMESTER = "SEMESTER";
    private static final String MODE_THEME = "THEME";
    private static final String ROLE_TEAM_LEADER = "TEAM_LEADER";
    private static final String ROLE_VICE_PRINCIPAL = "VICE_PRINCIPAL";
    private static final String ROLE_PRINCIPAL = "PRINCIPAL";
    private static final String TONE_NONE = "";
    private static final String TONE_OK = "ok";
    private static final String TONE_ERR = "err";
    // Lịch sử hiển thị theo giờ trường (Web hiện nguyên chuỗi)
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Trường do hệ thống quản lý: không lấy từ nội dung client gửi
    private static final Set<String> META_FIELDS = Set.of(
            "version",
            "dossierVersion",
            "review",
            "permissions",
            "creationCapabilities",
            "id",
            "kind",
            "classId",
            "yearPlanId",
            "themeId",
            "weekStart",
            "status",
            "approvals",
            "history",
            "sourceDocKey",
            "adjustments",
            "yearPlanVersion",
            "superseded",
            "durationPolicySnapshot",
            "calendarRevision",
            "effectiveCalendar",
            "orderedThemeIds");
    private static final int ADJUSTMENT_MAX_LENGTH = 4000;
    private static final String CHECK_GOAL = "goal:";
    private static final String CHECK_THEME = "theme:";
    private static final Map<String, String> SIGN_ROLE_LABELS = Map.of(
            ROLE_TEAM_LEADER, "Tổ trưởng",
            ROLE_VICE_PRINCIPAL, "Phó hiệu trưởng",
            ROLE_PRINCIPAL, "Hiệu trưởng");
    private static final Map<String, String> ROLE_LABELS = Map.of(
            ROLE_PRINCIPAL,
            "Hiệu trưởng",
            ROLE_VICE_PRINCIPAL,
            "Phó hiệu trưởng",
            ROLE_TEAM_LEADER,
            "Tổ trưởng nhóm tuổi",
            "TEACHER",
            "Giáo viên");

    private final EduClassDocRepository classDocRepository;
    private final com.carenest.repository.eduplan.EduClassDocReleaseRepository releaseRepository;
    private final EduPlanRepository yearPlanRepository;
    private final EduThemeRepository themeRepository;
    private final com.carenest.repository.eduplan.EduPlanBranchTemplateRepository templateRepository;
    private final EduBreakRepository breakRepository;
    private final EduConfigService eduConfigService;
    private final EduWorkflowService eduWorkflowService;
    private final EduSettingsRepository settingsRepository;
    private final EduYearPlanService yearPlanService;
    private final EduPlanAccessPolicy accessPolicy;
    private final ClassroomService classroomService;
    private final StaffAssignmentService staffAssignmentService;
    private final SchoolStructureService schoolStructureService;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final EduDurationPolicyService durationPolicyService;
    private final SchoolYearService schoolYearService;
    private final com.carenest.service.notification.NotificationService notificationService;

    /** Tuần học của kế hoạch năm: số thứ tự cả năm, thứ Hai đầu tuần, chủ đề. */
    public record TeachingWeek(int no, LocalDate start, Long themeId) {}

    @Override
    @Transactional(readOnly = true)
    public ClassCalendarPreviewResponse previewClassCalendar(
            CurrentUser currentUser, ClassCalendarPreviewRequest request) {
        String logPrefix =
                LOG_PREFIX + "|previewClassCalendar|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            requireView(currentUser);
            Long classId = request.classId();
            ClassroomInfoResponse cls = requireClass(classId);
            requireClassDocAuthor(currentUser, cls);
            EduPlan plan = requireIssuedPlan(request.yearPlanId(), cls);
            if (isSuperseded(plan.getId())) throw GlobalException.of(ApiCode.YEAR_PLAN_SUPERSEDED);
            var currentBranch =
                    classDocRepository.findByDocKey(expectedKey(EduClassDoc.BRANCH, classId, plan, null, null));
            long actualVersion = currentBranch.map(EduClassDoc::getVersion).orElse(-1L);
            if (request.expectedBranchVersion() == null || request.expectedBranchVersion() != actualVersion)
                throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
            if (currentBranch.isPresent() && currentBranch.get().getStatus() == EduClassDocStatus.REVIEW)
                throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            List<EduTheme> themes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
            List<EduBreak> breaks = breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
            List<EduTeachingCalendar.BreakRange> breakRanges = breaks.stream()
                    .map(b -> new EduTeachingCalendar.BreakRange(b.getStartDate(), b.getEndDate()))
                    .toList();

            String snapshot =
                    currentBranch.map(EduClassDoc::getDurationPolicySnapshot).orElse(null);
            EduTeachingCalendar.DurationLimits limits;
            if (snapshot != null && !snapshot.isBlank()) {
                limits = snapshotLimits(snapshot);
            } else if (currentBranch.isPresent()) limits = null;
            else
                limits = parseDurationLimits(
                        durationPolicyService.getEffectivePolicy(plan.getSchoolYearId(), cls.ageGroupId()));

            ClassCalendarPreviewRequest.CalendarDraft baseDraft = request.draftCalendar();
            if (baseDraft == null
                    || baseDraft.themeSchedules() == null
                    || baseDraft.themeSchedules().isEmpty()) {
                List<ClassCalendarPreviewRequest.ThemeSchedule> themeSchedules = themes.stream()
                        .map(t -> new ClassCalendarPreviewRequest.ThemeSchedule(
                                t.getId(), t.getStartDate(), t.getEndDate()))
                        .toList();
                baseDraft = new ClassCalendarPreviewRequest.CalendarDraft(themeSchedules, List.of());
            }

            var sourceThemeIds = themes.stream().map(EduTheme::getId).collect(java.util.stream.Collectors.toSet());
            if (baseDraft.themeSchedules().size() != sourceThemeIds.size()
                    || !baseDraft.themeSchedules().stream()
                            .map(ClassCalendarPreviewRequest.ThemeSchedule::themeId)
                            .collect(java.util.stream.Collectors.toSet())
                            .equals(sourceThemeIds)) throw GlobalException.of(ApiCode.THEME_NOT_IN_PLAN);
            validateBranchIdentity(plan.getId(), baseDraft, currentBranch.orElse(null), cls);
            List<Long> orderedThemeIds = request.orderedThemeIds();
            if (orderedThemeIds == null || orderedThemeIds.isEmpty()) {
                orderedThemeIds = baseDraft.themeSchedules().stream()
                        .map(ClassCalendarPreviewRequest.ThemeSchedule::themeId)
                        .toList();
            }

            EduTeachingCalendar.ReorderResult reorderResult =
                    EduTeachingCalendar.reorderThemeBlocks(baseDraft, orderedThemeIds, breakRanges, limits);
            validateBranchIdentity(plan.getId(), calendarDraft(objectMapper.valueToTree(reorderResult.calendar())),
                    currentBranch.orElse(null), cls);

            log.info("{}|END", logPrefix);
            return new ClassCalendarPreviewResponse(
                    reorderResult.calendar(), reorderResult.countsBefore(), reorderResult.countsAfter(), actualVersion);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private ClassCalendarPreviewRequest.CalendarDraft calendarDraft(ObjectNode doc) {
        return new ClassCalendarPreviewRequest.CalendarDraft(
                objectMapper.convertValue(
                        doc.path("themeSchedules"),
                        new tools.jackson.core.type.TypeReference<
                                List<ClassCalendarPreviewRequest.ThemeSchedule>>() {}),
                objectMapper.convertValue(
                        doc.path("branches"),
                        new tools.jackson.core.type.TypeReference<
                                List<ClassCalendarPreviewRequest.BranchSchedule>>() {}));
    }

    private void validateBranchIdentity(
            Long planId,
            ClassCalendarPreviewRequest.CalendarDraft draft,
            EduClassDoc current,
            ClassroomInfoResponse cls) {
        var sourceThemes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId);
        var themesById = sourceThemes.stream().collect(Collectors.toMap(EduTheme::getId, Function.identity()));
        var themeIds = themesById.keySet();
        if (draft.themeSchedules() == null
                || draft.themeSchedules().size() != themeIds.size()
                || !draft.themeSchedules().stream()
                        .map(ClassCalendarPreviewRequest.ThemeSchedule::themeId)
                        .collect(Collectors.toSet())
                        .equals(themeIds)) throw GlobalException.of(ApiCode.THEME_NOT_IN_PLAN);
        if (!draft.themeSchedules().stream().map(ClassCalendarPreviewRequest.ThemeSchedule::themeId).toList()
                .equals(sourceThemes.stream().map(EduTheme::getId).toList())) {
            throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "thứ tự chủ đề lớn chỉ đọc");
        }
        for (var theme : draft.themeSchedules()) {
            EduTheme source = themesById.get(theme.themeId());
            if (!Objects.equals(theme.startDate(), source.getStartDate()) || !Objects.equals(theme.endDate(), source.getEndDate())) {
                throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "chủ đề lớn chỉ đọc");
            }
            if (theme.startDate() != null && theme.startDate().isBefore(cls.yearStart())
                    || theme.endDate() != null && theme.endDate().isAfter(cls.yearEnd()))
                throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "chủ đề phải nằm trong năm học");
        }
        var templates = templateRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                .collect(Collectors.toMap(
                        com.carenest.entity.eduplan.EduPlanBranchTemplate::getId, Function.identity()));
        Map<String, JsonNode> previous = new java.util.HashMap<>();
        if (current != null)
            readObject(current.getContent())
                    .path("branches")
                    .forEach(b -> previous.put(b.path("key").asText(), b));
        Set<String> keys = new HashSet<>();
        for (var branch :
                draft.branches() == null ? List.<ClassCalendarPreviewRequest.BranchSchedule>of() : draft.branches()) {
            try {
                if (!java.util.UUID.fromString(branch.key()).toString().equalsIgnoreCase(branch.key()))
                    throw new IllegalArgumentException();
            } catch (RuntimeException ex) {
                throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "mã nhánh phải là UUID");
            }
            if (!keys.add(branch.key())
                    || !themeIds.contains(branch.themeId())
                    || branch.name() != null && branch.name().length() > 150)
                throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "nhánh trùng mã hoặc sai chủ đề");
            if (branch.sourceTemplateId() != null
                    && (!templates.containsKey(branch.sourceTemplateId())
                            || !templates
                                    .get(branch.sourceTemplateId())
                                    .getThemeId()
                                    .equals(branch.themeId())))
                throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
            JsonNode old = previous.get(branch.key());
            if (old != null
                    && (old.path("themeId").asLong() != branch.themeId()
                            || !java.util.Objects.equals(
                                    old.hasNonNull("sourceTemplateId")
                                            ? old.path("sourceTemplateId").asLong()
                                            : null,
                                    branch.sourceTemplateId())))
                throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
        }
    }

    private EduTeachingCalendar.DurationLimits snapshotLimits(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return null;
        JsonNode node = objectMapper.readTree(snapshot);
        return "LEGACY_UNBOUNDED".equals(node.path("mode").asString(""))
                ? null
                : new EduTeachingCalendar.DurationLimits(
                        node.path("theme").path("minWeeks").asInt(),
                        node.path("theme").path("maxWeeks").asInt(),
                        node.path("branch").path("minWeeks").asInt(),
                        node.path("branch").path("maxWeeks").asInt());
    }

    private static EduTeachingCalendar.DurationLimits parseDurationLimits(EduDurationPolicyResponse policy) {
        if (policy == null || policy.theme() == null || policy.branch() == null) {
            return null;
        }
        return new EduTeachingCalendar.DurationLimits(
                policy.theme().minWeeks(),
                policy.theme().maxWeeks(),
                policy.branch().minWeeks(),
                policy.branch().maxWeeks());
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BranchFilePreviewResponse previewBranchFile(
            CurrentUser currentUser, Long classId, Long yearPlanId, MultipartFile file) {
        String logPrefix = LOG_PREFIX + "|previewBranchFile|userId=" + currentUser.userId() + "|classId=" + classId
                + "|yearPlanId=" + yearPlanId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            ClassroomInfoResponse authorClass = requireClass(classId);
            requireClassDocAuthor(currentUser, authorClass);
            ClassroomInfoResponse cls = requireClass(classId);
            EduPlan plan = requireIssuedPlan(yearPlanId, cls);
            if (isSuperseded(plan.getId())) throw GlobalException.of(ApiCode.YEAR_PLAN_SUPERSEDED);
            // Spec 003: lịch nhánh khóa khi có gói học kỳ đang Chờ duyệt
            if (semesterInReview(classId, plan.getId())) throw GlobalException.of(ApiCode.CLASS_DOC_SUBMITTED);
            if (file == null || file.isEmpty()) throw GlobalException.of(ApiCode.BRANCH_FILE_UNSUPPORTED);
            if (file.getSize() > 2L * 1024 * 1024) throw GlobalException.of(ApiCode.BRANCH_FILE_TOO_LARGE);
            BranchFileTable.Parsed parsed;
            try {
                parsed = BranchFileTable.interpret(GoalFileGrid.read(file.getOriginalFilename(), file.getBytes()));
            } catch (java.io.IOException ex) {
                throw GlobalException.withCause(ApiCode.BRANCH_FILE_UNSUPPORTED, ex);
            } catch (GlobalException ex) {
                if (ex.getApiCode() == ApiCode.GOAL_FILE_TOO_LARGE)
                    throw GlobalException.withCause(ApiCode.BRANCH_FILE_TOO_LARGE, ex);
                if (ex.getApiCode() == ApiCode.GOAL_FILE_UNSUPPORTED)
                    throw GlobalException.withCause(ApiCode.BRANCH_FILE_UNSUPPORTED, ex);
                throw ex;
            }
            List<EduTheme> themes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
            Map<String, List<EduTheme>> byName =
                    themes.stream().collect(Collectors.groupingBy(t -> BranchFileTable.normalized(t.getName())));
            Map<Long, List<TeachingWeek>> weeks = teachingWeeks(classId, plan.getId(), true).stream()
                    .collect(Collectors.groupingBy(TeachingWeek::themeId));
            Map<Long, Integer> positions = new java.util.HashMap<>();
            List<BranchFilePreviewResponse.Match> matches = new ArrayList<>();
            List<BranchFilePreviewResponse.Warning> warnings = new ArrayList<>(parsed.warnings());
            for (var entry : parsed.entries()) {
                List<EduTheme> candidates =
                        byName.getOrDefault(BranchFileTable.normalized(entry.themeName()), List.of());
                if (candidates.size() != 1) {
                    warnings.add(new BranchFilePreviewResponse.Warning(
                            entry.row(),
                            candidates.isEmpty()
                                    ? "Chủ đề không khớp với kế hoạch năm"
                                    : "Có nhiều chủ đề trùng tên, chưa thể ghép tên nhánh"));
                    continue;
                }
                EduTheme theme = candidates.getFirst();
                List<TeachingWeek> slots = weeks.getOrDefault(theme.getId(), List.of());
                int position = positions.getOrDefault(theme.getId(), 0);
                if (position >= slots.size()) {
                    warnings.add(new BranchFilePreviewResponse.Warning(
                            entry.row(), "Tên nhánh thừa so với số tuần học của chủ đề"));
                    continue;
                }
                matches.add(new BranchFilePreviewResponse.Match(
                        entry.row(),
                        theme.getId(),
                        theme.getName(),
                        slots.get(position).start(),
                        entry.name()));
                positions.put(theme.getId(), position + 1);
            }
            log.info("{}|END", logPrefix);
            return new BranchFilePreviewResponse(List.copyOf(matches), List.copyOf(warnings));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public EduClassDoc requireThemeAccess(CurrentUser currentUser, String docKey, boolean write) {
        String logPrefix = LOG_PREFIX + "|requireThemeAccess|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            requireView(currentUser);
            EduClassDoc found = classDocRepository
                    .findByDocKey(docKey)
                    .orElseThrow(() -> GlobalException.of(ApiCode.THEME_PLAN_NOT_FOUND));
            if (write) {
                settingsRepository.findForShare().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
                yearPlanRepository.findForUpdateById(found.getYearPlanId()).orElseThrow();
            }
            EduClassDoc doc =
                    write ? classDocRepository.findForUpdateById(found.getId()).orElseThrow() : found;
            if (!EduClassDoc.THEME.equals(doc.getKind())) throw GlobalException.of(ApiCode.RESOURCE_THEME_ONLY);
            ClassroomInfoResponse cls = requireClass(doc.getClassroomId());
            LocalDate today = LocalDate.now(clock);
            if (visibleClasses(currentUser, cls.schoolYearId(), today).stream().noneMatch(c -> c.id().equals(cls.id())))
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            if (write) {
                requireClassDocAuthor(currentUser, cls);
                if (!doc.getStatus().editable()) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
                // EDU-26: học liệu của tài liệu trên phiên bản đã thay thế cũng chỉ xem
                if (isSuperseded(doc.getYearPlanId())) throw GlobalException.of(ApiCode.YEAR_PLAN_SUPERSEDED);
            }
            log.info("{}|END", logPrefix);
            return doc;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ClassDocsResponse getClassDocs(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getClassDocs|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            requireView(currentUser);
            LocalDate today = LocalDate.now(clock);
            List<ClassroomInfoResponse> classes = visibleClasses(currentUser, schoolYearId, today);
            List<Long> classIds =
                    classes.stream().map(ClassroomInfoResponse::id).toList();
            Map<Long, List<Long>> teachers = staffAssignmentService.findCurrentTeacherIds(classIds, today);
            Map<Long, Long> homerooms = staffAssignmentService.findHomeroomTeacherIds(classIds, today);
            Map<Long, String> codes = ageGroupCodes();

            // Tổ trưởng của các nhóm tuổi có lớp đang xem (để hiện chuỗi duyệt, tên người duyệt)
            Map<Long, Set<Long>> leaders = new LinkedHashMap<>();
            classes.stream()
                    .map(ClassroomInfoResponse::ageGroupId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(ag -> leaders.put(ag, staffAssignmentService.findActiveTeamLeaderIds(ag, today)));
            Set<Long> staffIds = new HashSet<>();
            teachers.values().forEach(staffIds::addAll);
            leaders.values().forEach(staffIds::addAll);
            Map<Long, String> names = accountService.findFullNames(staffIds);

            Map<Long, ClassDocsResponse.StaffItem> staff = new LinkedHashMap<>();
            leaders.forEach((ag, ids) -> ids.forEach(id -> staff.put(
                    id, new ClassDocsResponse.StaffItem(id, names.get(id), ROLE_TEAM_LEADER, codes.get(ag)))));
            for (ClassroomInfoResponse cls : classes) {
                for (Long id : teachers.getOrDefault(cls.id(), List.of())) {
                    staff.putIfAbsent(
                            id,
                            new ClassDocsResponse.StaffItem(id, names.get(id), "TEACHER", codes.get(cls.ageGroupId())));
                }
            }
            List<ClassDocsResponse.ClassItem> classItems = classes.stream()
                    .map(c -> new ClassDocsResponse.ClassItem(
                            String.valueOf(c.id()),
                            c.name(),
                            codes.get(c.ageGroupId()),
                            c.campusId(),
                            teachers.getOrDefault(c.id(), List.of()).stream()
                                    .distinct()
                                    .toList(),
                            homerooms.get(c.id())))
                    .toList();

            List<EduClassDoc> docs =
                    classIds.isEmpty() ? List.of() : classDocRepository.findAllByClassroomIdInOrderByIdAsc(classIds);
            Map<Long, EduWorkflowPolicy.Evaluation> evaluations = eduWorkflowService.evaluateClassDocs(docs, today);
            Map<Long, ClassroomInfoResponse> classInfoById =
                    classes.stream().collect(Collectors.toMap(ClassroomInfoResponse::id, c -> c, (a, b) -> a));
            Map<String, EduWorkflowPolicy.RolePolicy> policies = eduWorkflowService.loadAllPolicies();
            List<EduPlan> referencedPlans = yearPlanRepository.findAllById(docs.stream()
                    .map(EduClassDoc::getYearPlanId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
            Set<Long> planSchoolYears = referencedPlans.stream()
                    .map(EduPlan::getSchoolYearId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, EduPlan> plans = (planSchoolYears.isEmpty()
                            ? referencedPlans
                            : yearPlanRepository.findAllBySchoolYearIdIn(planSchoolYears))
                    .stream()
                    .filter(p -> p.getPlanLevel() == EduPlanLevel.TEAM)
                    .collect(Collectors.toMap(EduPlan::getId, p -> p));
            Map<String, Integer> latestApprovedVersions = new LinkedHashMap<>();
            plans.values().stream()
                    .filter(p -> p.getStatus() == EduPlanStatus.APPROVED)
                    .forEach(p -> latestApprovedVersions.merge(
                            p.getSchoolYearId() + ":" + p.getAgeGroupId(), p.getVersionNo(), Math::max));
            Set<Long> supersededPlanIds = plans.values().stream()
                    .filter(p -> p.getVersionNo()
                            < latestApprovedVersions.getOrDefault(
                                    p.getSchoolYearId() + ":" + p.getAgeGroupId(), p.getVersionNo()))
                    .map(EduPlan::getId)
                    .collect(Collectors.toSet());
            Map<Long, EduClassDoc> docsById = docs.stream().collect(Collectors.toMap(EduClassDoc::getId, d -> d));
            Map<String, EduClassDoc> docsByKey =
                    docs.stream().collect(Collectors.toMap(EduClassDoc::getDocKey, d -> d));
            Map<Long, SchoolYearDetailResponse> yearsById = new HashMap<>();
            Map<Long, SchoolYearDetailResponse> years = new HashMap<>();
            for (ClassroomInfoResponse c : classes)
                if (c.schoolYearId() != null)
                    years.put(c.id(), yearsById.computeIfAbsent(
                            c.schoolYearId(), id -> schoolYearService.findSchoolYearDetail(id).orElse(null)));
            ClassListContext context = new ClassListContext(
                    classInfoById,
                    policies,
                    eduWorkflowService.getPolicyRevision(),
                    accessPolicy.classScopeSnapshot(currentUser),
                    eduWorkflowService.getUnionAuthorUserIdsForDossiers(docs),
                    plans,
                    supersededPlanIds,
                    docsById,
                    docsByKey,
                    years,
                    classroomService.findOpenedDates(classIds));
            ClassDocsResponse response = new ClassDocsResponse(
                    toNodes(currentUser, docs, EduClassDoc.BRANCH, evaluations, context),
                    toNodes(currentUser, docs, EduClassDoc.THEME, evaluations, context),
                    toNodes(currentUser, docs, EduClassDoc.WEEK, evaluations, context),
                    classItems,
                    List.copyOf(staff.values()),
                    buildCreationCapabilities(classItems, context),
                    toNodes(currentUser, docs, EduClassDoc.SEMESTER, evaluations, context),
                    toNodes(currentUser, docs, EduClassDoc.DOSSIER, evaluations, context),
                    toNodes(currentUser, docs, EduClassDoc.DAY, evaluations, context),
                    !classes.isEmpty() && yearsById.values().stream().anyMatch(y -> !EduSemesters.configured(y)));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<ClassDocsResponse.CreationCapability> buildCreationCapabilities(
            List<ClassDocsResponse.ClassItem> classItems, ClassListContext context) {
        List<ClassDocsResponse.CreationCapability> list = new ArrayList<>();
        for (ClassDocsResponse.ClassItem cls : classItems) {
            Long classId = Long.parseLong(cls.id());
            boolean canAuthorBranch = context.scope().canAuthor(classId);
            boolean canAuthorTheme = context.scope().canAuthor(classId);
            list.add(new ClassDocsResponse.CreationCapability(
                    classId,
                    "branch",
                    canAuthorBranch,
                    "ISSUED_YEAR_PLAN",
                    canAuthorBranch ? null : "AUTHOR_FORBIDDEN"));
            list.add(new ClassDocsResponse.CreationCapability(
                    classId, "theme", canAuthorTheme, "ISSUED_TEAM_PLAN", canAuthorTheme ? null : "AUTHOR_FORBIDDEN"));
            list.add(new ClassDocsResponse.CreationCapability(
                    classId, "week", canAuthorTheme, "THEME_DOSSIER", canAuthorTheme ? null : "AUTHOR_FORBIDDEN"));
            list.add(new ClassDocsResponse.CreationCapability(
                    classId, "day", canAuthorTheme, "THEME_DOSSIER", canAuthorTheme ? null : "AUTHOR_FORBIDDEN"));
        }
        return list;
    }

    @Override
    @Transactional
    public ObjectNode saveClassDoc(
            CurrentUser currentUser,
            String kind,
            String docKey,
            ObjectNode doc,
            Long expectedVersion,
            Long expectedRootVersion) {
        String logPrefix =
                LOG_PREFIX + "|saveClassDoc|userId=" + currentUser.userId() + "|kind=" + kind + "|docKey=" + docKey;
        log.info("{}|START", logPrefix);
        try {
            settingsRepository.findForShare().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
            if (expectedVersion == null)
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "X-Class-Doc-Version");
            requireKind(kind);
            if (EduClassDoc.isRoot(kind)) throw GlobalException.of(ApiCode.INVALID_VALUE, "loại tài liệu");
            Long classId = requireLong(doc, "classId");
            Long yearPlanId = requireLong(doc, "yearPlanId");
            Long themeId = EduClassDoc.BRANCH.equals(kind) ? null : requireLong(doc, "themeId");
            LocalDate weekStart = EduClassDoc.WEEK.equals(kind) || EduClassDoc.DAY.equals(kind)
                    ? requireDate(doc, "weekStart")
                    : null;

            ClassroomInfoResponse cls = requireClass(classId);
            LocalDate today = LocalDate.now(clock);
            // EDU-12: author eligibility according to policy and reviewer precedence
            requireClassDocAuthor(currentUser, cls);
            EduPlan plan = requireIssuedPlan(yearPlanId, cls);
            if (!expectedKey(kind, classId, plan, themeId, weekStart).equals(docKey)) {
                throw GlobalException.of(ApiCode.CLASS_DOC_KEY_MISMATCH);
            }
            // EDU-26: phiên bản kế hoạch năm đã bị thay thế thì tài liệu của nó chỉ xem
            if (isSuperseded(plan.getId())) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_SUPERSEDED);
            }
            List<TeachingWeek> weeks = teachingWeeks(classId, plan.getId(), false);
            if (themeId != null
                    && themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()).stream()
                            .noneMatch(t -> t.getId().equals(themeId))) {
                throw GlobalException.of(ApiCode.THEME_NOT_IN_PLAN);
            }
            if (weekStart != null
                    && weeks.stream()
                            .noneMatch(w ->
                                    w.start().equals(weekStart) && w.themeId().equals(themeId))) {
                throw GlobalException.of(ApiCode.WEEK_NOT_IN_THEME);
            }

            yearPlanRepository.findForUpdateById(plan.getId()).orElseThrow();
            SchoolYearDetailResponse year = yearOf(cls);
            List<EduClassDoc> planDocs = classDocRepository.findAllByClassroomIdAndYearPlanId(classId, plan.getId());
            // Spec 003: tài liệu thuộc một gốc (gói học kỳ / hồ sơ chủ đề); gốc đang Chờ duyệt thì khóa
            EduClassDoc root = null;
            if (!EduClassDoc.BRANCH.equals(kind)) {
                root = classDocRepository
                        .findForUpdateByDocKey(containerKey(kind, classId, plan.getId(), themeId, planDocs, year))
                        .orElse(null);
                if (!EduClassDoc.THEME.equals(kind)) {
                    if (expectedRootVersion == null)
                        throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "X-Dossier-Version");
                    if (root == null) throw GlobalException.of(ApiCode.THEME_PLAN_NOT_SAVED);
                    if (!expectedRootVersion.equals(root.getVersion()))
                        throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
                }
                if (root != null && root.getStatus() == EduClassDocStatus.REVIEW)
                    throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            }
            if (EduClassDoc.BRANCH.equals(kind) && semesterInReview(classId, plan.getId()))
                throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            if (EduClassDoc.WEEK.equals(kind) && (doc.has("days") || doc.has("dayGoals")))
                throw GlobalException.of(ApiCode.INVALID_VALUE, "giáo án ngày lưu ở trang Giáo án ngày");
            if (EduClassDoc.DAY.equals(kind)) {
                ObjectNode dayOnly = objectMapper.createObjectNode();
                contentOf(doc).properties().forEach(e -> {
                    if (!Set.of("days", "dayGoals").contains(e.getKey()))
                        throw GlobalException.of(ApiCode.INVALID_VALUE, e.getKey());
                    dayOnly.set(e.getKey(), e.getValue());
                });
            }
            Optional<EduClassDoc> current = classDocRepository.findForUpdateByDocKey(docKey);
            EduClassDoc entity;
            if (current.isEmpty()) {
                if (expectedVersion != null && expectedVersion != -1L) {
                    throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
                }
                requireCanCreate(kind, cls.id(), plan.getId(), themeId);
                entity = EduClassDoc.create(
                        docKey, kind, classId, plan.getId(), themeId, weekStart, currentUser.userId());
                String sourceKey = doc.path("sourceDocKey").asString("").trim();
                if (!sourceKey.isEmpty()) {
                    entity.linkSource(
                            requireCopySource(sourceKey, kind, classId, plan).getId());
                }
            } else {
                entity = current.get();
                if (expectedVersion != null && !expectedVersion.equals(entity.getVersion())) {
                    throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
                }
                // EDU-64: phần tương lai được sửa; REVIEW vẫn khóa.
                if (entity.getStatus() == EduClassDocStatus.REVIEW) {
                    throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
                }
            }
            if (EduClassDoc.THEME.equals(kind)) {
                String before = current.map(c -> modeOf(readObject(c.getContent()))).orElse(MODE_THEME);
                String after = doc.path("submitMode").asString(MODE_THEME);
                if (!Set.of(MODE_THEME, MODE_SEMESTER).contains(after))
                    throw GlobalException.of(ApiCode.INVALID_VALUE, "cách gửi");
                if (!before.equals(after)) {
                    // FR-002a: đổi cách gửi chỉ khi kế hoạch chủ đề còn Nháp và chưa từng ban hành
                    if (current.isPresent() && (entity.getStatus() != EduClassDocStatus.DRAFT || everApproved(entity)))
                        throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
                    if (MODE_SEMESTER.equals(after)
                            && EduSemesters.of(year, themeStart(themeId, planDocs)).isEmpty())
                        throw GlobalException.of(ApiCode.INVALID_VALUE, "năm học chưa cấu hình học kỳ");
                }
            }
            if (EduClassDoc.BRANCH.equals(kind)) {
                if (current.isPresent()
                        && readObject(entity.getContent()).path("schemaVersion").asInt() == 2
                        && doc.path("schemaVersion").asInt() != 2) throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
                if (entity.getDurationPolicySnapshot() == null) {
                    entity.setDurationPolicySnapshot(
                            current.isEmpty() ? durationPolicyService.snapshot() : "{\"mode\":\"LEGACY_UNBOUNDED\"}");
                }
                if (doc.path("schemaVersion").asInt() == 2) {
                    var draft = calendarDraft(doc);
                    validateBranchIdentity(plan.getId(), draft, current.orElse(null), cls);
                    if (draft.themeSchedules().stream().allMatch(t -> t.startDate() != null && t.endDate() != null)
                            && draft.branches().stream().allMatch(b -> b.startDate() != null && b.endDate() != null)) {
                        var projected = projectBranch(plan.getId(), draft, null).calendar();
                        if (doc.has("names") && !doc.path("names").equals(objectMapper.valueToTree(projected.names()))
                                || doc.has("notes")
                                        && !doc.path("notes").equals(objectMapper.valueToTree(projected.notes())))
                            throw GlobalException.of(
                                    ApiCode.EDUCATION_CALENDAR_INVALID, "dữ liệu tuần không khớp nhánh");
                        doc.set("names", objectMapper.valueToTree(projected.names()));
                        doc.set("notes", objectMapper.valueToTree(projected.notes()));
                    }
                }
                if (doc.has("orderedThemeIds") && !doc.path("orderedThemeIds").isEmpty()) {
                    List<Long> orderedThemeIds = new ArrayList<>();
                    doc.path("orderedThemeIds").forEach(n -> orderedThemeIds.add(n.asLong()));

                    List<ClassCalendarPreviewRequest.ThemeSchedule> themeSchedules = new ArrayList<>();
                    doc.path("themeSchedules").forEach(ts -> {
                        themeSchedules.add(new ClassCalendarPreviewRequest.ThemeSchedule(
                                ts.path("themeId").asLong(),
                                LocalDate.parse(ts.path("startDate").asText()),
                                LocalDate.parse(ts.path("endDate").asText())));
                    });

                    List<ClassCalendarPreviewRequest.BranchSchedule> branchSchedules = new ArrayList<>();
                    doc.path("branches").forEach(bs -> {
                        Map<String, String> weekNotesMap = new LinkedHashMap<>();
                        JsonNode notesNode = bs.path("weekNotes");
                        if (notesNode != null && notesNode.isObject()) {
                            notesNode
                                    .properties()
                                    .forEach(p -> weekNotesMap.put(
                                            p.getKey(), p.getValue().asText()));
                        }
                        branchSchedules.add(new ClassCalendarPreviewRequest.BranchSchedule(
                                bs.path("key").asText(),
                                bs.path("themeId").asLong(),
                                bs.has("sourceTemplateId")
                                                && !bs.path("sourceTemplateId").isNull()
                                        ? bs.path("sourceTemplateId").asLong()
                                        : null,
                                bs.path("name").asText(),
                                LocalDate.parse(bs.path("startDate").asText()),
                                LocalDate.parse(bs.path("endDate").asText()),
                                weekNotesMap));
                    });

                    ClassCalendarPreviewRequest.CalendarDraft baseDraft =
                            new ClassCalendarPreviewRequest.CalendarDraft(themeSchedules, branchSchedules);

                    List<EduBreak> breaks = breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
                    List<EduTeachingCalendar.BreakRange> breakRanges = breaks.stream()
                            .map(b -> new EduTeachingCalendar.BreakRange(b.getStartDate(), b.getEndDate()))
                            .toList();

                    EduTeachingCalendar.DurationLimits limits = snapshotLimits(entity.getDurationPolicySnapshot());
                    EduTeachingCalendar.ReorderResult result =
                            EduTeachingCalendar.reorderThemeBlocks(baseDraft, orderedThemeIds, breakRanges, limits);

                    doc.set(
                            "themeSchedules",
                            objectMapper.valueToTree(result.calendar().themeSchedules()));
                    doc.set(
                            "branches",
                            objectMapper.valueToTree(result.calendar().branches()));
                    doc.set("names", objectMapper.valueToTree(result.calendar().names()));
                    doc.set("notes", objectMapper.valueToTree(result.calendar().notes()));
                    doc.put("calendarRevision", System.currentTimeMillis());
                    validateBranchIdentity(plan.getId(), calendarDraft(doc), current.orElse(null), cls);
                }
            }
            ObjectNode previousContent = readObject(entity.getContent());
            ObjectNode nextContent = contentOf(doc);
            Set<String> reopenKeys = new LinkedHashSet<>();
            if (!previousContent.equals(nextContent)) {
                if (EduClassDoc.BRANCH.equals(kind)) {
                    Map<LocalDate, Long> weekThemes = weeks.stream().collect(Collectors.toMap(
                            TeachingWeek::start, TeachingWeek::themeId, (first, second) -> first));
                    Set<Long> released = new HashSet<>();
                    for (Long t : new HashSet<>(weekThemes.values()))
                        if (semesterReleased(classId, plan.getId(), t, planDocs, year, false)) released.add(t);
                    // FR-002c: đổi tên nhánh tuần chưa dạy của kỳ đã ban hành ⇒ gói học kỳ + hồ sơ chủ đề về Nháp
                    for (Long t : EduClassDocFutureEdit.validateBranch(
                            previousContent, nextContent, weekThemes, today, released)) {
                        EduSemesters.of(year, themeStart(t, planDocs))
                                .ifPresent(no -> reopenKeys.add(semesterKey(classId, plan.getId(), no)));
                        reopenKeys.add(dossierKey(classId, t));
                    }
                } else {
                    boolean released = root != null && !releasesOfRoot(root).isEmpty();
                    if (EduClassDoc.WEEK.equals(kind) || EduClassDoc.DAY.equals(kind)) {
                        EduClassDocFutureEdit.validateWeek(previousContent, nextContent, weekStart, today, released);
                    } else if (released) {
                        EduClassDocFutureEdit.requireFuture(themeStart(themeId, planDocs), today);
                    }
                    if (root != null) reopenKeys.add(root.getDocKey());
                    reopenKeys.add(dossierKey(classId, themeId));
                }
            }
            entity.changeContent(objectMapper.writeValueAsString(nextContent), currentUser.userId());
            if (root != null) root.touchDossier(clock.instant());
            EduClassDoc saved = classDocRepository.saveAndFlush(entity);
            if (EduClassDoc.THEME.equals(kind))
                reopenKeys.add(containerKey(kind, classId, plan.getId(), themeId,
                        classDocRepository.findAllByClassroomIdAndYearPlanId(classId, plan.getId()), year));
            for (String key : reopenKeys) reopenRoot(key, classId, plan.getId(), year, currentUser);
            ensureRoots(classId, plan.getId(), year, currentUser.userId());
            eduWorkflowService.recordAuthor(
                    null,
                    saved.getId(),
                    currentUser.userId(),
                    current.isEmpty() ? "CREATOR" : "CONTENT_WRITE",
                    clock.instant());
            ObjectNode response = toNode(currentUser, saved);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ObjectNode actOnClassDoc(
            CurrentUser currentUser, String kind, String docKey, ClassDocActionRequest request, Long expectedVersion) {
        String logPrefix = LOG_PREFIX + "|actOnClassDoc|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            settingsRepository.findForShare().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
            requireKind(kind);
            // Spec 003: chỉ gửi/duyệt theo gói học kỳ (lịch nhánh + chủ đề "theo kỳ") hoặc hồ sơ chủ đề
            if (EduClassDoc.BRANCH.equals(kind))
                throw GlobalException.of(ApiCode.INVALID_VALUE, "chủ đề nhánh gửi trong gói học kỳ");
            if (!EduClassDoc.isRoot(kind)) throw GlobalException.of(ApiCode.WEEKLY_LESSON_VIA_THEME);
            EduClassDoc found = classDocRepository
                    .findByDocKey(docKey)
                    .filter(d -> d.getKind().equals(kind))
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_NOT_FOUND));
            yearPlanRepository
                    .findForUpdateById(found.getYearPlanId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
            EduClassDoc doc =
                    classDocRepository.findForUpdateById(found.getId()).orElseThrow();
            if (expectedVersion == null)
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "X-Class-Doc-Version");
            if (!expectedVersion.equals(doc.getVersion())) throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
            ClassroomInfoResponse cls = requireClass(doc.getClassroomId());
            Instant at = clock.instant();
            LocalDate today = LocalDate.now(clock);
            var policy = eduWorkflowService.loadPolicy(kind);
            var evaluation = eduWorkflowService.evaluateClassDoc(doc, cls.ageGroupId(), today);
            var approvals = new ArrayList<>(eduWorkflowService.parseApprovals(doc.getApprovals()));
            SchoolYearDetailResponse year = yearOf(cls);
            List<EduClassDoc> planDocs =
                    classDocRepository.findAllByClassroomIdAndYearPlanId(cls.id(), doc.getYearPlanId());
            var all = new ArrayList<EduClassDoc>();
            all.add(doc);
            all.addAll(classDocRepository.findAllByIdInForUpdateOrderByIdAsc(membersOf(doc, planDocs, year).stream()
                    .map(EduClassDoc::getId)
                    .toList()));
            RootInfo info = rootInfo(doc, planDocs, year, openedOn(cls.id()), today);
            String note = request.note() == null ? "" : request.note().trim();
            if (ACTION_EXTEND.equals(request.action())) {
                ObjectNode response = extendDeadline(currentUser, doc, info, note, request.deadline(), today, at);
                log.info("{}|END", logPrefix);
                return response;
            }
            int round = doc.getReviewRound();
            EduWorkflowPolicy.Role actingRole = evaluation.waitingRole();
            EduClassDocStatus next = EduClassDocStatus.REVIEW;
            if (ACTION_SUBMIT.equals(request.action())) {
                requireClassDocAuthor(currentUser, cls);
                if (!doc.getStatus().editable()) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
                if (isSuperseded(doc.getYearPlanId())) throw GlobalException.of(ApiCode.YEAR_PLAN_SUPERSEDED);
                if (EduDeadline.OVERDUE.equals(info.deadline().state()))
                    throw GlobalException.of(ApiCode.CLASS_DOC_DEADLINE_PASSED);
                requireCanSubmit(doc, planDocs, year);
                long unresolved = all.stream()
                        .mapToLong(d -> unresolvedVersionFixes(d, today))
                        .sum();
                if (unresolved > 0) throw GlobalException.of(ApiCode.CLASS_DOC_VERSION_FIX_REQUIRED, unresolved);
                round++;
                approvals.clear();
                actingRole = null;
            } else {
                if (doc.getStatus() != EduClassDocStatus.REVIEW
                        || actingRole == null
                        || eduWorkflowService
                                .getUnionAuthorUserIdsForDossier(doc)
                                .contains(currentUser.userId())
                        || !accessPolicy.canReviewClassDoc(currentUser, actingRole, cls.ageGroupId(), cls.id())) {
                    throw GlobalException.of(ApiCode.NOT_YOUR_REVIEW_STEP, request.action());
                }
                if (ACTION_APPROVE.equals(request.action())) {
                    approvals.add(new EduWorkflowPolicy.Approval(
                            currentUser.userId(), currentUser.fullName(), actingRole, at, note, round));
                    var after = new EduWorkflowPolicy()
                            .evaluate(
                                    policy,
                                    round,
                                    approvals,
                                    eduWorkflowService.getUnionAuthorUserIdsForDossier(doc),
                                    eduWorkflowService.resolveActiveCandidates(
                                            policy, cls.ageGroupId(), cls.id(), today));
                    if (after.complete()) next = EduClassDocStatus.APPROVED;
                } else if (ACTION_REJECT.equals(request.action())) {
                    if (note.isBlank()) throw GlobalException.of(ApiCode.RETURN_REASON_REQUIRED);
                    next = EduClassDocStatus.DRAFT;
                } else throw GlobalException.of(ApiCode.INVALID_VALUE, "hành động");
            }
            long revision = eduWorkflowService.getPolicyRevision();
            String approvalsJson = eduWorkflowService.serializeApprovals(approvals);
            String completionPolicy = objectMapper.writeValueAsString(policy);
            String snapshot = next == EduClassDocStatus.APPROVED
                    ? eduWorkflowService.buildPrintSnapshot(
                            kind,
                            revision,
                            approvals,
                            policy,
                            at,
                            eduWorkflowService.getUnionAuthorUserIdsForDossier(doc))
                    : null;
            for (EduClassDoc member : all) {
                ArrayNode history = readArray(member.getHistory());
                ObjectNode event = objectMapper.createObjectNode();
                event.put("action", request.action());
                event.put("by", currentUser.fullName());
                event.put("actorId", currentUser.userId());
                event.put("round", round);
                event.put("policyRevision", revision);
                event.put("at", at.toString());
                event.put("note", note);
                event.put("roleKey", actingRole == null ? "" : actingRole.name());
                event.put("role", actingRole == null ? "Người soạn" : SIGN_ROLE_LABELS.get(actingRole.name()));
                event.put(
                        "tone",
                        ACTION_REJECT.equals(request.action())
                                ? TONE_ERR
                                : ACTION_APPROVE.equals(request.action()) ? TONE_OK : TONE_NONE);
                history.add(event);
                member.snapshotSignChain(objectMapper.writeValueAsString(policy.reviewers()));
                member.updateWorkflowState(
                        next,
                        round,
                        approvalsJson,
                        history.toString(),
                        revision,
                        next == EduClassDocStatus.APPROVED ? completionPolicy : null,
                        snapshot,
                        next == EduClassDocStatus.APPROVED ? "HUMAN_APPROVAL" : null,
                        next == EduClassDocStatus.APPROVED ? at : null,
                        currentUser.userId());
                member.touchDossier(at);
            }
            classDocRepository.saveAllAndFlush(all);
            if (next == EduClassDocStatus.APPROVED)
                releaseRepository.save(EduClassDocRelease.create(
                        doc.getId(),
                        doc.getVersion(),
                        releaseSnapshot(snapshot, all, semesterBranchNames(doc, planDocs, year)),
                        currentUser.userId(),
                        at));
            notifyAfterAction(currentUser, doc, cls, request.action(), next, note, today);
            ObjectNode response = toNode(currentUser, doc);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 6 * * *", zone = "${carenest.time-zone}")
    public int remindNearDeadlines() {
        String logPrefix = LOG_PREFIX + "|remindNearDeadlines";
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Map<String, List<EduClassDoc>> planDocs = new HashMap<>();
            Map<Long, Optional<ClassroomInfoResponse>> classes = new HashMap<>();
            int reminded = 0;
            for (EduClassDoc root : classDocRepository.findAllByKindInAndStatusIn(
                    List.of(EduClassDoc.SEMESTER, EduClassDoc.DOSSIER),
                    List.of(EduClassDocStatus.DRAFT, EduClassDocStatus.REVIEW))) {
                if (isSuperseded(root.getYearPlanId())) continue;
                var cls = classes.computeIfAbsent(root.getClassroomId(), classroomService::findClassroomInfo);
                if (cls.isEmpty()) continue;
                List<EduClassDoc> docs = planDocs.computeIfAbsent(
                        root.getClassroomId() + ":" + root.getYearPlanId(),
                        k -> classDocRepository.findAllByClassroomIdAndYearPlanId(root.getClassroomId(), root.getYearPlanId()));
                RootInfo info = rootInfo(root, docs, yearOf(cls.get()), openedOn(root.getClassroomId()), today);
                if (!EduDeadline.NEAR.equals(info.deadline().state())) continue;
                boolean waiting = root.getStatus() == EduClassDocStatus.REVIEW;
                Set<Long> to = waiting ? waitingReviewers(root, cls.get(), today) : Set.of();
                String body = (waiting ? "Đang chờ duyệt" : "Chưa gửi duyệt") + ", còn " + info.deadline().daysLeft()
                        + " ngày tới hạn " + info.deadline().effective().format(DAY_FORMAT);
                if (to.isEmpty()) {
                    // FR-013: chưa gửi ⇒ nhắc GVCN; đang chờ mà thiếu người duyệt ⇒ vẫn nhắc GVCN, ghi rõ
                    Long homeroom = homeroomOf(root.getClassroomId(), today);
                    to = homeroom == null ? Set.of() : Set.of(homeroom);
                    if (waiting) body += " (chưa có người duyệt)";
                }
                notificationService.notifyUsers(
                        to,
                        com.carenest.utils.enums.NotificationType.EDU_DEADLINE_NEAR,
                        "Gần đến hạn: " + labelOf(root, cls.get()),
                        body,
                        linkOf(root),
                        true);
                reminded++;
            }
            log.info("{}|reminded={}|END", logPrefix, reminded);
            return reminded;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ObjectNode saveAdjustments(
            CurrentUser currentUser,
            String docKey,
            Map<String, String> adjustments,
            Long expectedVersion,
            Long expectedRootVersion) {
        String logPrefix = LOG_PREFIX + "|saveAdjustments|userId=" + currentUser.userId() + "|docKey=" + docKey;
        log.info("{}|START", logPrefix);
        try {
            settingsRepository.findForShare().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
            // EDU-27 ở giáo án ngày (spec 003 Q9); khóa giáo án tuần cũ wp_ chuyển sang dd_ cùng tuần
            String dayKey = docKey.startsWith("wp_") ? "dd_" + docKey.substring(3) : docKey;
            EduClassDoc found = classDocRepository
                    .findByDocKey(dayKey)
                    .filter(d -> EduClassDoc.DAY.equals(d.getKind()))
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_NOT_FOUND));
            if (expectedVersion == null || expectedRootVersion == null)
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "X-Class-Doc-Version, X-Dossier-Version");
            yearPlanRepository.findForUpdateById(found.getYearPlanId()).orElseThrow();
            EduClassDoc root = classDocRepository
                    .findForUpdateByDocKey(dossierKey(found.getClassroomId(), found.getThemeId()))
                    .orElseThrow(() -> GlobalException.of(ApiCode.THEME_PLAN_NOT_FOUND));
            EduClassDoc doc =
                    classDocRepository.findForUpdateById(found.getId()).orElseThrow();
            if (!expectedVersion.equals(doc.getVersion()) || !expectedRootVersion.equals(root.getVersion()))
                throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
            ClassroomInfoResponse cls = requireClass(doc.getClassroomId());
            requireClassDocAuthor(currentUser, cls);
            if (doc.getStatus() == EduClassDocStatus.REVIEW) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            ObjectNode next = objectMapper.createObjectNode();
            new TreeMap<>(adjustments).forEach((day, text) -> {
                LocalDate date;
                try {
                    date = LocalDate.parse(day);
                } catch (DateTimeParseException ex) {
                    throw GlobalException.withCause(ApiCode.CLASS_DOC_ADJUSTMENT_INVALID, ex);
                }
                String value = text == null ? "" : text.trim();
                if (date.isBefore(doc.getWeekStart())
                        || date.isAfter(doc.getWeekStart().plusDays(4))
                        || value.length() > ADJUSTMENT_MAX_LENGTH) {
                    throw GlobalException.of(ApiCode.CLASS_DOC_ADJUSTMENT_INVALID);
                }
                if (!value.isEmpty()) {
                    next.put(day, value);
                }
            });
            if (!next.equals(readObject(doc.getAdjustments()))) {
                ArrayNode history = readArray(doc.getHistory());
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("action", "Cập nhật kế hoạch điều chỉnh và bổ sung");
                entry.put("by", currentUser.fullName());
                entry.put("role", ROLE_LABELS.get("TEACHER"));
                entry.put("at", now());
                entry.put("tone", TONE_NONE);
                history.add(entry);
                doc.changeAdjustments(
                        objectMapper.writeValueAsString(next),
                        objectMapper.writeValueAsString(history),
                        currentUser.userId());
                root.touchDossier(clock.instant());
                classDocRepository.saveAndFlush(doc);
                eduWorkflowService.recordAuthor(
                        null, doc.getId(), currentUser.userId(), "CONTENT_WRITE", clock.instant());
            }
            ObjectNode response = toNode(currentUser, doc);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Khóa Web: giáo án tuần phiên bản ≥2 thêm id kế hoạch để không trùng tuần của phiên bản cũ (spec 004 A4)
    private static String expectedKey(String kind, Long classId, EduPlan plan, Long themeId, LocalDate weekStart) {
        return switch (kind) {
            case EduClassDoc.BRANCH -> "bp_" + classId + "_" + plan.getId();
            case EduClassDoc.THEME -> "tp_" + classId + "_" + themeId;
            case EduClassDoc.DAY -> "dd_" + classId + "_" + weekStart + (plan.getVersionNo() > 1 ? "_" + plan.getId() : "");
            default -> "wp_" + classId + "_" + weekStart + (plan.getVersionNo() > 1 ? "_" + plan.getId() : "");
        };
    }

    /** Tài liệu nguồn của bản chép: cùng lớp, cùng loại, bản cũ hơn của cùng chuỗi kế hoạch tổ. */
    private EduClassDoc requireCopySource(String sourceKey, String kind, Long classId, EduPlan plan) {
        EduClassDoc source = classDocRepository
                .findByDocKey(sourceKey)
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_KEY_MISMATCH));
        EduPlan sourcePlan = yearPlanRepository
                .findById(source.getYearPlanId())
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_KEY_MISMATCH));
        if (!source.getKind().equals(kind)
                || !source.getClassroomId().equals(classId)
                || sourcePlan.getPlanLevel() != EduPlanLevel.TEAM
                || plan.getPlanLevel() != EduPlanLevel.TEAM
                || !sourcePlan.getSchoolYearId().equals(plan.getSchoolYearId())
                || !sourcePlan.getAgeGroupId().equals(plan.getAgeGroupId())
                || sourcePlan.getVersionNo() >= plan.getVersionNo()) {
            throw GlobalException.of(ApiCode.CLASS_DOC_KEY_MISMATCH);
        }
        return source;
    }

    /**
     * EDU-26 (research R5): số ô đỏ còn lại của bản chép. Tham chiếu MT/tuần không thuộc phiên bản của tài liệu, hoặc
     * MT/chủ đề đã đổi so với phiên bản nguồn mà chưa đánh dấu "Đã kiểm tra". Chủ đề đã kết thúc không tính.
     */
    private long unresolvedVersionFixes(EduClassDoc doc, LocalDate today) {
        if (doc.getSourceDocId() == null) {
            return 0;
        }
        Optional<EduClassDoc> source = classDocRepository.findById(doc.getSourceDocId());
        if (source.isEmpty() || source.get().getYearPlanId().equals(doc.getYearPlanId())) {
            return 0;
        }
        YearPlanChangesResponse changes =
                yearPlanService.compareVersions(source.get().getYearPlanId(), doc.getYearPlanId());
        Set<Long> goalIds = changes.goals().stream()
                .map(YearPlanChangesResponse.Goal::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> changedGoals = changes.goals().stream()
                .filter(g -> g.change() == YearPlanChangesResponse.Change.CHANGED)
                .map(YearPlanChangesResponse.Goal::id)
                .collect(Collectors.toSet());
        Set<Long> changedThemes = changes.themes().stream()
                .filter(t -> t.change() == YearPlanChangesResponse.Change.CHANGED)
                .map(YearPlanChangesResponse.Theme::id)
                .collect(Collectors.toSet());
        Map<Long, EduTheme> themes =
                themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(doc.getYearPlanId()).stream()
                        .collect(Collectors.toMap(EduTheme::getId, t -> t));
        ObjectNode content = readObject(doc.getContent());
        Set<String> checks = new HashSet<>();
        content.path("versionChecks").forEach(c -> checks.add(c.asString("")));

        if (EduClassDoc.BRANCH.equals(doc.getKind())) {
            List<TeachingWeek> weeks =
                    teachingWeeks(doc.getClassroomId(), doc.getYearPlanId(), EduClassDoc.BRANCH.equals(doc.getKind()));
            Set<String> starts = weeks.stream().map(w -> w.start().toString()).collect(Collectors.toSet());
            long dangling = content.path("names").properties().stream()
                    .filter(e -> !e.getValue().asString("").isBlank() && !starts.contains(e.getKey()))
                    .filter(e -> !LocalDate.parse(e.getKey()).plusDays(4).isBefore(today))
                    .count();
            long unchecked = changedThemes.stream()
                    .filter(id -> isOngoing(themes.get(id), today))
                    .filter(id -> !checks.contains(CHECK_THEME + id))
                    .count();
            return dangling + unchecked;
        }
        if (!isOngoing(themes.get(doc.getThemeId()), today)) {
            return 0;
        }
        Set<Long> referenced = new LinkedHashSet<>();
        if (EduClassDoc.THEME.equals(doc.getKind())) {
            content.path("rows").properties().forEach(e -> referenced.add(parseId(e.getKey())));
        } else {
            content.path("picks")
                    .forEach(list -> list.forEach(
                            p -> referenced.add(parseId(p.path("goalId").asString("")))));
        }
        return referenced.stream()
                .filter(id -> !goalIds.contains(id) || (changedGoals.contains(id) && !checks.contains(CHECK_GOAL + id)))
                .count();
    }

    // EDU-26/62: chỉ tổ ban hành mới thay nguồn lớp; năm ban hành hoặc tổ nháp không khóa hồ sơ cũ.
    private boolean isSuperseded(Long yearPlanId) {
        return yearPlanRepository
                .findById(yearPlanId)
                .filter(plan -> plan.getPlanLevel() == EduPlanLevel.TEAM)
                .map(plan -> yearPlanRepository
                        .findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                                EduPlanLevel.TEAM, plan.getSchoolYearId(), plan.getAgeGroupId())
                        .stream()
                        .filter(p -> p.getPlanLevel() == EduPlanLevel.TEAM)
                        .anyMatch(
                                p -> p.getStatus() == EduPlanStatus.APPROVED && p.getVersionNo() > plan.getVersionNo()))
                .orElse(false);
    }

    private static boolean isOngoing(EduTheme theme, LocalDate today) {
        return theme != null
                && (theme.getEndDate() == null || !theme.getEndDate().isBefore(today));
    }

    // Id chuỗi không phải số (dữ liệu cũ, id tạm) coi là tham chiếu không thuộc phiên bản
    private static Long parseId(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    /**
     * Lịch tuần cả năm (giống Web): tuần học đánh số liên tục theo các chủ đề; tuần chỉ bị bỏ khi đợt nghỉ phủ cả Thứ 2
     * → Thứ 6, nghỉ vài ngày thì vẫn là tuần học (EDU-41). Chủ đề bắt đầu Thứ 7 / Chủ nhật (dữ liệu trước EDU-40) thì
     * tuần đầu là Thứ Hai kế tiếp.
     */
    static List<TeachingWeek> teachingWeeks(List<EduTheme> themes, List<EduBreak> breaks) {
        List<TeachingWeek> weeks = new ArrayList<>();
        int no = 0;
        List<EduTheme> dated = themes.stream()
                .filter(t -> t.getStartDate() != null && t.getEndDate() != null)
                .sorted(Comparator.comparing(EduTheme::getStartDate))
                .toList();
        for (EduTheme theme : dated) {
            for (LocalDate monday = firstTeachingMonday(theme.getStartDate());
                    !monday.isAfter(theme.getEndDate());
                    monday = monday.plusDays(7)) {
                boolean wholeWeekOff = monday.datesUntil(monday.plusDays(5)).allMatch(day -> isBreakDay(breaks, day));
                if (!wholeWeekOff) {
                    weeks.add(new TeachingWeek(++no, monday, theme.getId()));
                }
            }
        }
        return weeks;
    }

    private static boolean isBreakDay(List<EduBreak> breaks, LocalDate day) {
        return breaks.stream()
                .filter(b -> b.getStartDate() != null)
                .anyMatch(b -> !day.isBefore(b.getStartDate())
                        && !day.isAfter(b.getEndDate() == null ? b.getStartDate() : b.getEndDate()));
    }

    private static LocalDate firstTeachingMonday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private List<TeachingWeek> teachingWeeks(Long yearPlanId) {
        return teachingWeeks(
                themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(yearPlanId),
                breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(yearPlanId));
    }

    private List<TeachingWeek> teachingWeeks(Long classId, Long planId, boolean includeDraft) {
        var branch = classDocRepository.findAllByClassroomIdAndYearPlanId(classId, planId).stream()
                .filter(d -> EduClassDoc.BRANCH.equals(d.getKind())
                        && (includeDraft || d.getStatus() == EduClassDocStatus.APPROVED))
                .findFirst();
        if (branch.isEmpty()) return teachingWeeks(planId);
        var schedules = readObject(branch.get().getContent()).path("themeSchedules");
        if (!schedules.isArray() || schedules.isEmpty()) return teachingWeeks(planId);
        List<TeachingWeek> weeks = new ArrayList<>();
        List<JsonNode> themes = new ArrayList<>();
        schedules.forEach(themes::add);
        themes.sort(Comparator.comparing(t -> t.path("startDate").asText()));
        var breaks = breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                .map(b -> new EduTeachingCalendar.BreakRange(b.getStartDate(), b.getEndDate()))
                .toList();
        for (var theme : themes) {
            if (!theme.hasNonNull("startDate") || !theme.hasNonNull("endDate")) continue;
            for (LocalDate monday : EduTeachingCalendar.teachingMondays(
                    LocalDate.parse(theme.path("startDate").asText()),
                    LocalDate.parse(theme.path("endDate").asText()),
                    breaks))
                weeks.add(new TeachingWeek(
                        weeks.size() + 1, monday, theme.path("themeId").asLong()));
        }
        return weeks;
    }

    private EduTeachingCalendar.ReorderResult projectBranch(
            Long planId, ClassCalendarPreviewRequest.CalendarDraft draft, EduTeachingCalendar.DurationLimits limits) {
        var breaks = breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                .map(b -> new EduTeachingCalendar.BreakRange(b.getStartDate(), b.getEndDate()))
                .toList();
        var order = draft.themeSchedules().stream()
                .sorted(Comparator.comparing(ClassCalendarPreviewRequest.ThemeSchedule::startDate))
                .map(ClassCalendarPreviewRequest.ThemeSchedule::themeId)
                .toList();
        return EduTeachingCalendar.reorderThemeBlocks(draft, order, breaks, limits);
    }

    private void requireView(CurrentUser user) {
        if (!accessPolicy.hasPermission(user, EduPermission.GOALS_VIEW)) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_ACCESS_DENIED);
        }
    }

    // Phạm vi tác vụ theo campus, lớp và nhóm tuổi được phân công còn hiệu lực.
    private List<ClassroomInfoResponse> visibleClasses(CurrentUser user, Long schoolYearId, LocalDate today) {
        List<ClassroomInfoResponse> classes = classroomService.findYearClassrooms(schoolYearId);
        if (accessPolicy.isPrincipal(user)) {
            return classes;
        }
        Set<Long> campuses = user.hasRole(CareNestRole.VICE_PRINCIPAL)
                ? staffAssignmentService.findActiveCampusIds(
                        user.userId(), com.carenest.utils.enums.StaffRole.VICE_PRINCIPAL, today)
                : Set.of();
        Set<Long> taught = staffAssignmentService.findCurrentTeacherClassroomIds(user.userId(), today);
        Set<Long> led = staffAssignmentService.findActiveTeamLeaderAgeGroupIds(user.userId(), today);
        return classes.stream()
                .filter(c -> campuses.contains(c.campusId())
                        || taught.contains(c.id())
                        || (c.ageGroupId() != null && led.contains(c.ageGroupId())))
                .toList();
    }

    private Map<Long, String> ageGroupCodes() {
        return schoolStructureService.findAgeGroups().stream()
                .collect(Collectors.toMap(AgeGroupResponse::id, AgeGroupResponse::code));
    }

    private ClassroomInfoResponse requireClass(Long classId) {
        return classroomService
                .findClassroomInfo(classId)
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
    }

    /** EDU-64: hai giáo viên cùng xem, nhưng chỉ chủ nhiệm đang hiệu lực được ghi hồ sơ lớp. */
    private boolean isHomeroomAuthor(CurrentUser user, ClassroomInfoResponse classroom) {
        if (!user.hasRole(CareNestRole.TEACHER)) return false;
        Long homeroomId = staffAssignmentService
                .findHomeroomTeacherIds(List.of(classroom.id()), LocalDate.now(clock))
                .get(classroom.id());
        return Objects.equals(homeroomId, user.userId());
    }

    private void requireClassDocAuthor(CurrentUser user, ClassroomInfoResponse classroom) {
        if (!isHomeroomAuthor(user, classroom)) throw GlobalException.of(ApiCode.CLASS_DOC_TEACHER_ONLY);
    }

    /** EDU-12: tài liệu lớp chỉ nhận tổ đã ban hành đúng nhóm tuổi, năm học của lớp. */
    private EduPlan requireIssuedPlan(Long yearPlanId, ClassroomInfoResponse cls) {
        EduPlan plan = yearPlanRepository
                .findById(yearPlanId)
                .orElseThrow(() -> GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
        if (plan.getPlanLevel() != EduPlanLevel.TEAM
                || !plan.getSchoolYearId().equals(cls.schoolYearId())
                || !plan.getAgeGroupId().equals(cls.ageGroupId())) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_CLASS_MISMATCH);
        }
        if (plan.getStatus() != EduPlanStatus.APPROVED) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_NOT_ISSUED);
        }
        return plan;
    }

    /** EDU-64: nhánh và hồ sơ chủ đề được soạn song song; giáo án cần hồ sơ chủ đề đã lưu. */
    private void requireCanCreate(String kind, Long classId, Long yearPlanId, Long themeId) {
        if (EduClassDoc.BRANCH.equals(kind)) {
            return;
        }
        List<EduClassDoc> docs = classDocRepository.findAllByClassroomIdAndYearPlanId(classId, yearPlanId);
        if ((EduClassDoc.WEEK.equals(kind) || EduClassDoc.DAY.equals(kind))
                && docs.stream()
                        .noneMatch(d -> EduClassDoc.THEME.equals(d.getKind()) && themeId.equals(d.getThemeId()))) {
            throw GlobalException.of(ApiCode.THEME_PLAN_NOT_SAVED);
        }
    }

    /**
     * Spec 003 FR-002/005: gói học kỳ cần tên nhánh mọi tuần của kỳ + kế hoạch chủ đề/tuần của chủ đề "theo kỳ"; hồ sơ
     * chủ đề cần gói học kỳ đã từng ban hành, giáo án ngày mọi tuần (+ kế hoạch chủ đề/tuần nếu "theo chủ đề").
     */
    private void requireCanSubmit(EduClassDoc root, List<EduClassDoc> docs, SchoolYearDetailResponse year) {
        Long classId = root.getClassroomId();
        Long planId = root.getYearPlanId();
        boolean semester = EduClassDoc.SEMESTER.equals(root.getKind());
        if (semester && !EduSemesters.configured(year))
            throw GlobalException.of(ApiCode.INVALID_VALUE, "năm học chưa cấu hình học kỳ");
        if (!semester && !semesterReleased(classId, planId, root.getThemeId(), docs, year, true))
            throw GlobalException.of(ApiCode.SEMESTER_PACKAGE_NOT_RELEASED);
        List<Long> themes = semester
                ? semesterThemes(planId, root.getSemesterNo(), docs, year)
                : List.of(root.getThemeId());
        JsonNode names = docs.stream()
                .filter(d -> EduClassDoc.BRANCH.equals(d.getKind()))
                .findFirst()
                .map(d -> (JsonNode) readObject(d.getContent()).path("names"))
                .orElseThrow(() -> GlobalException.of(ApiCode.BRANCH_WEEKS_UNNAMED, 1));
        List<TeachingWeek> weeks = teachingWeeks(classId, planId, true).stream()
                .filter(w -> themes.contains(w.themeId()))
                .toList();
        long empty = weeks.stream()
                .filter(w -> names.path(w.start().toString()).asString("").isBlank())
                .count();
        if (empty > 0) throw GlobalException.of(ApiCode.BRANCH_WEEKS_UNNAMED, empty);
        List<String> missing = new ArrayList<>();
        for (Long themeId : themes) {
            boolean planParts = MODE_SEMESTER.equals(submitModeOf(themeId, docs)) == semester;
            if (planParts
                    && docs.stream()
                            .noneMatch(d -> EduClassDoc.THEME.equals(d.getKind()) && themeId.equals(d.getThemeId())))
                throw GlobalException.of(ApiCode.THEME_PLAN_NOT_SAVED);
            List<TeachingWeek> themeWeeks =
                    weeks.stream().filter(w -> w.themeId().equals(themeId)).toList();
            for (int i = 0; i < themeWeeks.size(); i++) {
                LocalDate start = themeWeeks.get(i).start();
                boolean hasWeek = docs.stream()
                        .anyMatch(d -> EduClassDoc.WEEK.equals(d.getKind()) && start.equals(d.getWeekStart()));
                boolean hasDay = docs.stream()
                        .anyMatch(d -> EduClassDoc.DAY.equals(d.getKind()) && start.equals(d.getWeekStart()));
                if ((planParts && !hasWeek) || (!semester && !hasDay)) missing.add("nhánh " + (i + 1));
            }
        }
        if (!missing.isEmpty())
            throw GlobalException.of(ApiCode.WEEKLY_LESSONS_MISSING, String.join(", ", missing));
    }

    private static void requireKind(String kind) {
        if (!Set.of(EduClassDoc.BRANCH, EduClassDoc.THEME, EduClassDoc.WEEK, EduClassDoc.DAY, EduClassDoc.SEMESTER,
                        EduClassDoc.DOSSIER)
                .contains(kind)) {
            throw GlobalException.of(ApiCode.INVALID_VALUE, "loại tài liệu");
        }
    }

    private static Long requireLong(ObjectNode doc, String field) {
        try {
            return Long.valueOf(doc.path(field).asString("").trim());
        } catch (NumberFormatException ex) {
            throw GlobalException.withCause(ApiCode.REQUIRED_FIELD_MISSING, ex, field);
        }
    }

    private static LocalDate requireDate(ObjectNode doc, String field) {
        try {
            return LocalDate.parse(doc.path(field).asString("").trim());
        } catch (DateTimeParseException ex) {
            throw GlobalException.withCause(ApiCode.INVALID_DATE_FORMAT, ex, field);
        }
    }

    private static ObjectNode contentOf(ObjectNode doc) {
        ObjectNode content = doc.deepCopy();
        content.remove(META_FIELDS);
        return content;
    }

    private String now() {
        return LocalDateTime.now(clock.withZone(SCHOOL_ZONE)).format(HISTORY_TIME);
    }

    private List<ObjectNode> toNodes(
            CurrentUser currentUser,
            List<EduClassDoc> docs,
            String kind,
            Map<Long, EduWorkflowPolicy.Evaluation> evaluations,
            ClassListContext context) {
        return docs.stream()
                .filter(d -> kind.equals(d.getKind()))
                .map(d -> EduClassDoc.BRANCH.equals(kind) && !context.scope().canAuthor(d.getClassroomId())
                        ? releasedBranchNode(currentUser, d, context)
                        : visibleInList(d, context)
                                ? toNode(currentUser, d, evaluations.get(d.getId()), context)
                                : EduClassDoc.isRoot(kind) && isBoard(currentUser)
                                        ? boardRootNode(currentUser, d, context)
                                        : releasedNode(currentUser, d, context))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Người chỉ xem gặp hồ sơ đang mở lại sửa phần tương lai: hiện nội dung bản ban hành gần nhất (FR-009);
     * chưa từng ban hành thì ẩn.
     */
    private ObjectNode releasedNode(CurrentUser currentUser, EduClassDoc doc, ClassListContext context) {
        EduClassDoc root = EduClassDoc.isRoot(doc.getKind())
                ? doc
                : context.docsByKey().get(containerKey(doc, planDocsOf(doc, context), context.years().get(doc.getClassroomId())));
        if (root == null) return null;
        var releases = releaseRepository.findAllByClassDocIdInOrderByReleasedAtAscIdAsc(List.of(root.getId()));
        if (releases.isEmpty()) return null;
        ObjectNode snapshot = readObject(releases.getLast().getPrintSnapshot());
        JsonNode member = null;
        for (JsonNode m : snapshot.path("members"))
            if (doc.getDocKey().equals(m.path("docKey").asString(""))) member = m.path("content");
        if (member == null || !member.isObject()) return null;
        ObjectNode node = toNode(currentUser, doc, null, context);
        readObject(doc.getContent()).propertyNames().forEach(key -> {
            if (!META_FIELDS.contains(key)) node.remove(key);
        });
        member.properties().forEach(e -> {
            if (!META_FIELDS.contains(e.getKey())) node.set(e.getKey(), e.getValue());
        });
        if (EduClassDoc.SEMESTER.equals(doc.getKind()))
            for (JsonNode m : snapshot.path("members"))
                if (EduClassDoc.BRANCH.equals(m.path("kind").asString(""))) node.set("branchNames", m.path("names"));
        node.put("status", EduClassDocStatus.APPROVED.name());
        node.put("releasedView", true);
        return node;
    }

    private boolean isBoard(CurrentUser user) {
        return accessPolicy.isPrincipal(user) || user.hasRole(CareNestRole.VICE_PRINCIPAL);
    }

    /**
     * FR-003, FR-004a: Ban giám hiệu thấy trạng thái, hạn của gói/hồ sơ mọi lớp (để mở hạn) dù chưa ban hành; tên nhánh
     * chỉ lấy từ bản đã ban hành, không lộ bản đang soạn (FR-015).
     */
    private ObjectNode boardRootNode(CurrentUser currentUser, EduClassDoc root, ClassListContext context) {
        ObjectNode node = toNode(currentUser, root, null, context);
        ObjectNode names = objectMapper.createObjectNode();
        var releases = releasesOfRoot(root);
        if (!releases.isEmpty())
            for (JsonNode m : readObject(releases.getLast().getPrintSnapshot()).path("members"))
                if (EduClassDoc.BRANCH.equals(m.path("kind").asString("")) && m.path("names") instanceof ObjectNode n)
                    names.setAll(n);
        if (node.has("branchNames")) node.set("branchNames", names);
        node.set("history", objectMapper.createArrayNode());
        return node;
    }

    /**
     * FR-015 (Q3): người không soạn chỉ thấy tên nhánh của học kỳ có gói đã ban hành (bản ban hành gần nhất); học kỳ
     * chưa có gói ban hành liệt kê trong branchUnreleasedSemesters.
     */
    private ObjectNode releasedBranchNode(CurrentUser currentUser, EduClassDoc branch, ClassListContext context) {
        ObjectNode node = toNode(currentUser, branch, null, context);
        ObjectNode names = objectMapper.createObjectNode();
        ArrayNode unreleased = objectMapper.createArrayNode();
        if (EduSemesters.configured(context.years().get(branch.getClassroomId()))) {
            for (int no = 1; no <= 2; no++) {
                EduClassDoc sp = context.docsByKey().get(semesterKey(branch.getClassroomId(), branch.getYearPlanId(), no));
                var releases = sp == null ? List.<EduClassDocRelease>of() : releasesOfRoot(sp);
                if (releases.isEmpty()) {
                    unreleased.add(no);
                    continue;
                }
                for (JsonNode m : readObject(releases.getLast().getPrintSnapshot()).path("members"))
                    if (EduClassDoc.BRANCH.equals(m.path("kind").asString(""))
                            && m.path("names") instanceof ObjectNode released) names.setAll(released);
            }
        } else if (readObject(branch.getContent()).path("names") instanceof ObjectNode draft) {
            names = draft.deepCopy();
        }
        node.set("names", names);
        node.set("branchUnreleasedSemesters", unreleased);
        node.put("releasedView", true);
        return node;
    }

    /**
     * GVCN thấy mọi trạng thái hồ sơ lớp mình; người duyệt của lớp thấy bản Chờ duyệt; người khác chỉ thấy bản đã ban
     * hành (spec 002 FR-007, FR-016c).
     */
    private boolean visibleInList(EduClassDoc doc, ClassListContext context) {
        if (EduClassDoc.BRANCH.equals(doc.getKind())) return true;
        if (doc.getStatus() == EduClassDocStatus.APPROVED) return true;
        if (context.scope().canAuthor(doc.getClassroomId())) return true;
        if (doc.getStatus() != EduClassDocStatus.REVIEW) return false;
        ClassroomInfoResponse cls = context.classes().get(doc.getClassroomId());
        String type = policyTypeOf(doc, planDocsOf(doc, context), context.years().get(doc.getClassroomId()));
        var policy = context.policies().get(type);
        return cls != null
                && policy != null
                && policy.reviewers().stream()
                        .anyMatch(role -> context.scope().canReview(role, cls.ageGroupId(), cls.campusId()));
    }

    /** Dạng tài liệu của Web: nội dung soạn + id, lớp, kế hoạch năm, chủ đề, tuần, trạng thái, người duyệt, lịch sử. */
    private ObjectNode toNode(CurrentUser currentUser, EduClassDoc doc) {
        var cls = requireClass(doc.getClassroomId());
        var evaluation = doc.getStatus() == EduClassDocStatus.REVIEW
                ? eduWorkflowService.evaluateClassDoc(doc, cls.ageGroupId(), LocalDate.now(clock))
                : null;
        return toNode(currentUser, doc, evaluation);
    }

    private ObjectNode toNode(CurrentUser currentUser, EduClassDoc doc, EduWorkflowPolicy.Evaluation evaluation) {
        return toNode(currentUser, doc, evaluation, null);
    }

    private ObjectNode toNode(
            CurrentUser currentUser,
            EduClassDoc doc,
            EduWorkflowPolicy.Evaluation evaluation,
            ClassListContext context) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", doc.getDocKey());
        node.put("kind", doc.getKind());
        node.put("classId", String.valueOf(doc.getClassroomId()));
        node.put("yearPlanId", String.valueOf(doc.getYearPlanId()));
        if (doc.getThemeId() != null) {
            node.put("themeId", String.valueOf(doc.getThemeId()));
        }
        if (doc.getWeekStart() != null) {
            node.put("weekStart", doc.getWeekStart().toString());
        }
        readObject(doc.getContent()).properties().forEach(e -> {
            if (!META_FIELDS.contains(e.getKey())) {
                node.set(e.getKey(), e.getValue());
            }
        });
        node.put("status", doc.publicStatus().name());
        node.put("version", doc.getVersion() != null ? doc.getVersion() : 0L);
        EduClassDoc calendarDoc = EduClassDoc.BRANCH.equals(doc.getKind())
                ? doc
                : (context == null
                                ? classDocRepository
                                        .findAllByClassroomIdAndYearPlanId(doc.getClassroomId(), doc.getYearPlanId())
                                        .stream()
                                : context.docsByKey().values().stream())
                        .filter(d -> EduClassDoc.BRANCH.equals(d.getKind())
                                && d.getClassroomId().equals(doc.getClassroomId())
                                && d.getYearPlanId().equals(doc.getYearPlanId())
                                && d.getStatus() == EduClassDocStatus.APPROVED)
                        .findFirst()
                        .orElse(null);
        node.put("calendarRevision", calendarDoc == null ? -1L : calendarDoc.getVersion());
        if (calendarDoc != null
                && readObject(calendarDoc.getContent()).path("schemaVersion").asInt() == 2) {
            ObjectNode calendar = readObject(calendarDoc.getContent());
            node.set(
                    "effectiveCalendar",
                    objectMapper.createObjectNode().set("themeSchedules", calendar.path("themeSchedules")));
            ((ObjectNode) node.path("effectiveCalendar")).set("branches", calendar.path("branches"));
        }
        node.set("approvals", readArray(doc.getApprovals()));
        node.set("history", readArray(doc.getHistory()));
        if (doc.getDurationPolicySnapshot() != null
                && !doc.getDurationPolicySnapshot().isBlank()) {
            node.set("durationPolicySnapshot", readObject(doc.getDurationPolicySnapshot()));
        }
        EduPlan cachedPlan = context == null ? null : context.plans().get(doc.getYearPlanId());
        if (context == null) {
            yearPlanRepository
                    .findById(doc.getYearPlanId())
                    .ifPresent(plan -> node.put("yearPlanVersion", plan.getVersionNo()));
        } else if (cachedPlan != null) {
            node.put("yearPlanVersion", cachedPlan.getVersionNo());
        }
        node.put(
                "superseded",
                context == null
                        ? isSuperseded(doc.getYearPlanId())
                        : context.supersededPlanIds().contains(doc.getYearPlanId()));
        if (doc.getSourceDocId() != null) {
            EduClassDoc source = context == null
                    ? classDocRepository.findById(doc.getSourceDocId()).orElse(null)
                    : context.docsById().get(doc.getSourceDocId());
            if (source != null) node.put("sourceDocKey", source.getDocKey());
        }
        ClassroomInfoResponse cls = context == null
                ? requireClass(doc.getClassroomId())
                : context.classes().get(doc.getClassroomId());
        List<EduClassDoc> planDocs = context == null
                ? classDocRepository.findAllByClassroomIdAndYearPlanId(doc.getClassroomId(), doc.getYearPlanId())
                : planDocsOf(doc, context);
        SchoolYearDetailResponse year = context == null ? yearOf(cls) : context.years().get(doc.getClassroomId());
        String type = policyTypeOf(doc, planDocs, year);
        var policy = context == null
                ? eduWorkflowService.loadPolicy(type)
                : context.policies().get(type);
        node.set(
                "review",
                objectMapper.valueToTree(eduWorkflowService.toReviewStateResponse(
                        context == null ? eduWorkflowService.getPolicyRevision() : context.policyRevision(),
                        doc.getReviewRound(),
                        policy,
                        evaluation,
                        doc.getApprovals(),
                        doc.getAuthorCoverage(),
                        doc.getAuthorTrackingSince(),
                        doc.getCompletionMode(),
                        doc.getCompletedAt(),
                        doc.getCompletionPolicy(),
                        doc.getPrintSnapshot())));
        EduClassDoc root = doc;
        String containerKey = containerKey(doc, planDocs, year);
        if (containerKey != null) {
            root = planDocs.stream()
                    .filter(d -> containerKey.equals(d.getDocKey()))
                    .findFirst()
                    .orElse(doc);
            node.put("containerKey", containerKey);
            node.put("containerStatus", root.getStatus().name());
        }
        node.put("dossierVersion", root.getVersion() == null ? 0L : root.getVersion());
        boolean author = isHomeroomAuthor(currentUser, cls);
        boolean activeVersion = context == null
                ? !isSuperseded(doc.getYearPlanId())
                : !context.supersededPlanIds().contains(doc.getYearPlanId());
        boolean reviewable = evaluation != null
                && evaluation.waitingRole() != null
                && !(context == null
                                ? eduWorkflowService.getUnionAuthorUserIdsForDossier(doc)
                                : context.authors().getOrDefault(doc.getId(), Set.of()))
                        .contains(currentUser.userId())
                && (context == null
                        ? accessPolicy.canReviewClassDoc(
                                currentUser, evaluation.waitingRole(), cls.ageGroupId(), cls.id())
                        : context.scope().canReview(evaluation.waitingRole(), cls.ageGroupId(), cls.campusId()));
        // Lịch sử duyệt (ghi chú gửi, lý do trả lại) chỉ cho người soạn và người duyệt (FR-019)
        boolean seesHistory = author
                || reviewable
                || readArray(doc.getHistory()).valueStream()
                        .anyMatch(e -> e.path("actorId").asLong(-1) == currentUser.userId());
        if (!seesHistory) node.set("history", objectMapper.createArrayNode());
        node.set("releases", releasesOf(doc));
        String blocked = null;
        if (EduClassDoc.isRoot(doc.getKind())) {
            RootInfo info = rootInfo(
                    doc,
                    planDocs,
                    year,
                    context == null ? openedOn(doc.getClassroomId()) : context.opened().get(doc.getClassroomId()),
                    LocalDate.now(clock));
            node.put("deadline", info.deadline().deadline() == null ? null : info.deadline().deadline().toString());
            node.put("extendedTo",
                    info.deadline().extendedTo() == null ? null : info.deadline().extendedTo().toString());
            if (info.deadline().daysLeft() != null) node.put("daysLeft", info.deadline().daysLeft());
            node.put("deadlineState", info.deadline().state());
            node.put("everReleased", info.everReleased());
            ArrayNode themeIds = node.putArray("themeIds");
            info.themeIds().forEach(id -> themeIds.add(String.valueOf(id)));
            if (doc.getSemesterNo() != null) node.put("semesterNo", doc.getSemesterNo());
            if (EduClassDoc.DOSSIER.equals(doc.getKind())) node.put("submitMode", submitModeOf(doc.getThemeId(), planDocs));
            if (EduClassDoc.SEMESTER.equals(doc.getKind())) node.set("branchNames", semesterBranchNames(doc, planDocs, year));
            blocked = !doc.getStatus().editable()
                    ? "NOT_DRAFT"
                    : EduDeadline.OVERDUE.equals(info.deadline().state())
                            ? "DEADLINE_PASSED"
                            : EduClassDoc.DOSSIER.equals(doc.getKind())
                                            && !semesterReleased(
                                                    doc.getClassroomId(),
                                                    doc.getYearPlanId(),
                                                    doc.getThemeId(),
                                                    planDocs,
                                                    year,
                                                    true)
                                    ? "SEMESTER_NOT_RELEASED"
                                    : null;
            node.put("blockedReason", blocked);
            node.put(
                    "canExtend",
                    (accessPolicy.isPrincipal(currentUser) || currentUser.hasRole(CareNestRole.VICE_PRINCIPAL))
                            && doc.getStatus() == EduClassDocStatus.DRAFT
                            && EduDeadline.OVERDUE.equals(info.deadline().state()));
        }
        boolean rootKind = EduClassDoc.isRoot(doc.getKind());
        ObjectNode permissions = node.putObject("permissions");
        permissions.put(
                "canEdit",
                author
                        && activeVersion
                        && !rootKind
                        && doc.getStatus() != EduClassDocStatus.REVIEW
                        && root.getStatus() != EduClassDocStatus.REVIEW
                        && hasFutureContent(doc));
        permissions.put(
                "canSubmit",
                author
                        && activeVersion
                        && doc.getStatus().editable()
                        && rootKind
                        && blocked == null);
        permissions.put("canApprove", reviewable && rootKind);
        permissions.put("canReturn", reviewable && rootKind);
        permissions.put("canReject", reviewable && rootKind);
        boolean canAdjust = author && EduClassDoc.DAY.equals(doc.getKind()) && doc.getStatus() != EduClassDocStatus.REVIEW;
        permissions.put("canAdjust", canAdjust);
        permissions.put("canEditAdjustments", canAdjust);
        node.set("adjustments", readObject(doc.getAdjustments()));
        return node;
    }

    // Nội dung kế hoạch chủ đề + giáo án tuần lúc ban hành, kèm chuỗi ký
    private String releaseSnapshot(String signatures, List<EduClassDoc> members, ObjectNode branchNames) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("signatures", signatures == null ? objectMapper.nullNode() : readObject(signatures));
        ArrayNode list = root.putArray("members");
        if (branchNames != null) list.addObject().put("kind", EduClassDoc.BRANCH).set("names", branchNames);
        members.stream()
                .sorted(java.util.Comparator.comparing(EduClassDoc::getId))
                .forEach(m -> {
                    ObjectNode item = list.addObject();
                    item.put("kind", m.getKind());
                    item.put("docKey", m.getDocKey());
                    if (m.getWeekStart() != null) item.put("weekStart", m.getWeekStart().toString());
                    item.set("content", readObject(m.getContent()));
                });
        return root.toString();
    }

    private ArrayNode releasesOf(EduClassDoc doc) {
        ArrayNode list = objectMapper.createArrayNode();
        if (!EduClassDoc.isRoot(doc.getKind())) return list;
        var releases = releaseRepository.findAllByClassDocIdInOrderByReleasedAtAscIdAsc(List.of(doc.getId()));
        Map<Long, String> names = accountService.findFullNames(releases.stream()
                .map(EduClassDocRelease::getApprovedBy)
                .collect(Collectors.toSet()));
        releases.forEach(r -> list.addObject()
                .put("id", r.getId())
                .put("version", r.getDossierVersion())
                .put("releasedAt", r.getReleasedAt().toString())
                .put("approvedBy", names.get(r.getApprovedBy())));
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public ObjectNode getClassDocRelease(CurrentUser currentUser, Long releaseId) {
        String logPrefix = LOG_PREFIX + "|getClassDocRelease|userId=" + currentUser.userId() + "|releaseId=" + releaseId;
        log.info("{}|START", logPrefix);
        try {
            requireView(currentUser);
            EduClassDocRelease release = releaseRepository
                    .findById(releaseId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_NOT_FOUND));
            EduClassDoc doc = classDocRepository
                    .findById(release.getClassDocId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_NOT_FOUND));
            // Bản đã ban hành: ai xem được lớp thì xem được (như bản APPROVED trong danh sách)
            ClassroomInfoResponse cls = requireClass(doc.getClassroomId());
            if (visibleClasses(currentUser, cls.schoolYearId(), LocalDate.now(clock)).stream()
                    .noneMatch(c -> c.id().equals(cls.id())))
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            ObjectNode response = readObject(release.getPrintSnapshot());
            response.put("id", release.getId());
            response.put("version", release.getDossierVersion());
            response.put("releasedAt", release.getReleasedAt().toString());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private boolean hasFutureContent(EduClassDoc doc) {
        LocalDate today = LocalDate.now(clock);
        if (EduClassDoc.isRoot(doc.getKind())) return true;
        if (EduClassDoc.WEEK.equals(doc.getKind()) || EduClassDoc.DAY.equals(doc.getKind()))
            return doc.getWeekStart() != null && doc.getWeekStart().plusDays(4).isAfter(today);
        if (EduClassDoc.THEME.equals(doc.getKind()))
            return themeRepository.findById(doc.getThemeId()).map(EduTheme::getStartDate)
                    .filter(start -> start.isAfter(today)).isPresent();
        return teachingWeeks(doc.getClassroomId(), doc.getYearPlanId(), false).stream()
                .anyMatch(week -> week.start().isAfter(today));
    }

    // ===== Spec 003: gói học kỳ / hồ sơ chủ đề =====

    private record RootInfo(EduDeadline.Info deadline, List<Long> themeIds, boolean everReleased) {}

    private static String dossierKey(Long classId, Long themeId) {
        return "ds_" + classId + "_" + themeId;
    }

    private static String semesterKey(Long classId, Long planId, int no) {
        return "sp_" + classId + "_" + planId + "_" + no;
    }

    private static String modeOf(JsonNode content) {
        return MODE_SEMESTER.equals(content.path("submitMode").asString(MODE_THEME)) ? MODE_SEMESTER : MODE_THEME;
    }

    /** Q7: chưa chọn thì "theo chủ đề". */
    private String submitModeOf(Long themeId, List<EduClassDoc> docs) {
        return docs.stream()
                .filter(d -> EduClassDoc.THEME.equals(d.getKind()) && themeId.equals(d.getThemeId()))
                .findFirst()
                .map(d -> modeOf(readObject(d.getContent())))
                .orElse(MODE_THEME);
    }

    private static boolean everApproved(EduClassDoc doc) {
        if (doc.getStatus() == EduClassDocStatus.APPROVED) return true;
        return doc.getHistory() != null && doc.getHistory().contains("\"revise_future\"");
    }

    private SchoolYearDetailResponse yearOf(ClassroomInfoResponse cls) {
        return cls == null || cls.schoolYearId() == null
                ? null
                : schoolYearService.findSchoolYearDetail(cls.schoolYearId()).orElse(null);
    }

    private LocalDate openedOn(Long classId) {
        return classroomService.findOpenedDates(List.of(classId)).get(classId);
    }

    private List<EduClassDoc> planDocsOf(EduClassDoc doc, ClassListContext context) {
        return context.docsByKey().values().stream()
                .filter(d -> d.getClassroomId().equals(doc.getClassroomId())
                        && d.getYearPlanId().equals(doc.getYearPlanId()))
                .toList();
    }

    /** Ngày bắt đầu chủ đề của lớp: lịch lớp (bản soạn) nếu có, không thì ngày của kế hoạch tổ. */
    private LocalDate themeStart(Long themeId, List<EduClassDoc> docs) {
        for (EduClassDoc d : docs) {
            if (!EduClassDoc.BRANCH.equals(d.getKind())) continue;
            for (JsonNode ts : readObject(d.getContent()).path("themeSchedules"))
                if (ts.path("themeId").asLong() == themeId && ts.hasNonNull("startDate"))
                    return LocalDate.parse(ts.path("startDate").asString(""));
        }
        return themeRepository.findById(themeId).map(EduTheme::getStartDate).orElse(null);
    }

    private List<Long> semesterThemes(Long planId, int no, List<EduClassDoc> docs, SchoolYearDetailResponse year) {
        return themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                .map(EduTheme::getId)
                .filter(id -> EduSemesters.of(year, themeStart(id, docs)).filter(n -> n == no).isPresent())
                .toList();
    }

    /** Gốc chứa tài liệu: giáo án ngày ⇒ hồ sơ chủ đề; kế hoạch chủ đề/tuần ⇒ theo cách gửi của chủ đề (Q2/Q4). */
    private String containerKey(
            String kind, Long classId, Long planId, Long themeId, List<EduClassDoc> docs, SchoolYearDetailResponse year) {
        if (themeId == null || EduClassDoc.isRoot(kind) || EduClassDoc.BRANCH.equals(kind)) return null;
        if (!EduClassDoc.DAY.equals(kind) && MODE_SEMESTER.equals(submitModeOf(themeId, docs))) {
            var no = EduSemesters.of(year, themeStart(themeId, docs));
            if (no.isPresent()) return semesterKey(classId, planId, no.get());
        }
        return dossierKey(classId, themeId);
    }

    private String containerKey(EduClassDoc doc, List<EduClassDoc> docs, SchoolYearDetailResponse year) {
        return containerKey(
                doc.getKind(), doc.getClassroomId(), doc.getYearPlanId(), doc.getThemeId(), docs, year);
    }

    private String policyTypeOf(EduClassDoc doc, List<EduClassDoc> docs, SchoolYearDetailResponse year) {
        String key = containerKey(doc, docs, year);
        return EduClassDoc.policyType(key != null && key.startsWith("sp_") ? EduClassDoc.SEMESTER : doc.getKind());
    }

    // ponytail: quét danh sách tài liệu của lớp cho mỗi gốc (~vài chục dòng/lớp); gom theo themeId nếu lớp lớn
    private List<EduClassDoc> membersOf(EduClassDoc root, List<EduClassDoc> docs, SchoolYearDetailResponse year) {
        return docs.stream()
                .filter(d -> root.getDocKey().equals(containerKey(d, docs, year)))
                .toList();
    }

    private boolean semesterInReview(Long classId, Long planId) {
        return classDocRepository.findAllByClassroomIdAndYearPlanId(classId, planId).stream()
                .anyMatch(d -> EduClassDoc.SEMESTER.equals(d.getKind()) && d.getStatus() == EduClassDocStatus.REVIEW);
    }

    private List<EduClassDocRelease> releasesOfRoot(EduClassDoc root) {
        return root.getId() == null
                ? List.of()
                : releaseRepository.findAllByClassDocIdInOrderByReleasedAtAscIdAsc(List.of(root.getId()));
    }

    /** Gói học kỳ chứa chủ đề: đã từng ban hành? Năm chưa cấu hình học kỳ thì không có gói để chờ. */
    private boolean semesterReleased(
            Long classId, Long planId, Long themeId, List<EduClassDoc> docs, SchoolYearDetailResponse year,
            boolean whenNoSemester) {
        var no = EduSemesters.of(year, themeStart(themeId, docs));
        if (no.isEmpty()) return whenNoSemester;
        String key = semesterKey(classId, planId, no.get());
        return docs.stream()
                .filter(d -> key.equals(d.getDocKey()))
                .findFirst()
                .map(sp -> !releasesOfRoot(sp).isEmpty())
                .orElse(false);
    }

    private RootInfo rootInfo(
            EduClassDoc root, List<EduClassDoc> docs, SchoolYearDetailResponse year, LocalDate opened, LocalDate today) {
        var releases = releasesOfRoot(root);
        boolean semester = EduClassDoc.SEMESTER.equals(root.getKind());
        LocalDate deadline = semester
                ? EduSemesters.start(year, root.getSemesterNo())
                : themeStart(root.getThemeId(), docs);
        List<Long> themeIds = semester
                ? semesterThemes(root.getYearPlanId(), root.getSemesterNo(), docs, year)
                : List.of(root.getThemeId());
        LocalDate extended = null;
        for (JsonNode e : readArray(root.getHistory()))
            if (ACTION_EXTEND.equals(e.path("action").asString(""))) extended = LocalDate.parse(e.path("deadline").asString(""));
        LocalDate firstRelease =
                releases.isEmpty() ? null : LocalDate.ofInstant(releases.getFirst().getReleasedAt(), SCHOOL_ZONE);
        return new RootInfo(
                EduDeadline.of(
                        deadline, extended, today, root.getStatus() == EduClassDocStatus.APPROVED, firstRelease, opened),
                themeIds,
                !releases.isEmpty());
    }

    /** Tên nhánh các tuần thuộc học kỳ của gói (chụp vào bản ban hành, FR-015). */
    private ObjectNode semesterBranchNames(EduClassDoc root, List<EduClassDoc> docs, SchoolYearDetailResponse year) {
        if (!EduClassDoc.SEMESTER.equals(root.getKind())) return null;
        ObjectNode names = objectMapper.createObjectNode();
        LocalDate from = EduSemesters.start(year, root.getSemesterNo());
        LocalDate to = EduSemesters.end(year, root.getSemesterNo());
        if (from == null) return names;
        docs.stream().filter(d -> EduClassDoc.BRANCH.equals(d.getKind())).findFirst().ifPresent(b -> readObject(
                        b.getContent())
                .path("names")
                .properties()
                .forEach(e -> {
                    try {
                        LocalDate week = LocalDate.parse(e.getKey());
                        if (!week.isBefore(from) && !week.isAfter(to)) names.set(e.getKey(), e.getValue());
                    } catch (DateTimeParseException ignored) {
                        // khóa không phải ngày tuần: bỏ qua
                    }
                }));
        return names;
    }

    /** Tạo gốc còn thiếu: hồ sơ chủ đề cho mỗi kế hoạch chủ đề, gói học kỳ khi năm đã cấu hình học kỳ. */
    private void ensureRoots(Long classId, Long planId, SchoolYearDetailResponse year, Long userId) {
        List<EduClassDoc> docs = classDocRepository.findAllByClassroomIdAndYearPlanId(classId, planId);
        Set<String> keys = docs.stream().map(EduClassDoc::getDocKey).collect(Collectors.toSet());
        List<EduClassDoc> created = new ArrayList<>();
        docs.stream()
                .filter(d -> EduClassDoc.THEME.equals(d.getKind()) && !keys.contains(dossierKey(classId, d.getThemeId())))
                .forEach(d -> created.add(EduClassDoc.create(
                        dossierKey(classId, d.getThemeId()), EduClassDoc.DOSSIER, classId, planId, d.getThemeId(), null,
                        userId)));
        if (EduSemesters.configured(year))
            for (int no = 1; no <= 2; no++)
                if (!keys.contains(semesterKey(classId, planId, no)))
                    created.add(EduClassDoc.createSemester(classId, planId, no, userId));
        if (!created.isEmpty()) classDocRepository.saveAllAndFlush(created);
    }

    /** EDU-64 + FR-002c: sửa phần tương lai của gói/hồ sơ đã ban hành ⇒ gốc và thành viên về Nháp, giữ bản ban hành. */
    private void reopenRoot(String rootKey, Long classId, Long planId, SchoolYearDetailResponse year, CurrentUser user) {
        EduClassDoc root = classDocRepository.findForUpdateByDocKey(rootKey).orElse(null);
        if (root == null) return;
        if (root.getStatus() == EduClassDocStatus.REVIEW) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        List<EduClassDoc> docs = classDocRepository.findAllByClassroomIdAndYearPlanId(classId, planId);
        List<EduClassDoc> all = new ArrayList<>();
        all.add(root);
        all.addAll(membersOf(root, docs, year));
        for (EduClassDoc member : all) {
            if (member.getStatus() != EduClassDocStatus.APPROVED) continue;
            ArrayNode history = readArray(member.getHistory());
            ObjectNode event = history.addObject();
            event.put("action", "revise_future");
            event.put("actorId", user.userId());
            event.put("by", user.fullName());
            event.put("at", clock.instant().toString());
            event.put("round", member.getReviewRound());
            event.set("previousApprovals", readArray(member.getApprovals()));
            if (member.getPrintSnapshot() != null)
                event.set("previousPrintSnapshot", objectMapper.readTree(member.getPrintSnapshot()));
            member.updateWorkflowState(EduClassDocStatus.DRAFT, member.getReviewRound(), "[]",
                    objectMapper.writeValueAsString(history), member.getAppliedPolicyRevision(),
                    null, null, null, null, user.userId());
            member.touchDossier(clock.instant());
        }
        classDocRepository.saveAllAndFlush(all);
    }

    /** FR-004a (Q1, Q8): HT/PHT mọi điểm trường mở hạn gói/hồ sơ quá hạn còn Nháp; bắt buộc lý do và hạn mới. */
    private ObjectNode extendDeadline(
            CurrentUser user, EduClassDoc root, RootInfo info, String note, LocalDate deadline, LocalDate today,
            Instant at) {
        if (!(accessPolicy.isPrincipal(user) || user.hasRole(CareNestRole.VICE_PRINCIPAL)))
            throw GlobalException.of(ApiCode.NOT_YOUR_REVIEW_STEP, ACTION_EXTEND);
        if (note.isBlank()) throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "lý do mở hạn");
        if (deadline == null || !deadline.isAfter(today)) throw GlobalException.of(ApiCode.INVALID_VALUE, "hạn mới");
        if (root.getStatus() != EduClassDocStatus.DRAFT || !EduDeadline.OVERDUE.equals(info.deadline().state()))
            throw GlobalException.of(ApiCode.INVALID_VALUE, "chỉ mở hạn khi quá hạn");
        ArrayNode history = readArray(root.getHistory());
        ObjectNode event = history.addObject();
        event.put("action", ACTION_EXTEND);
        event.put("deadline", deadline.toString());
        event.put("note", note);
        event.put("by", user.fullName());
        event.put("actorId", user.userId());
        event.put("at", at.toString());
        event.put("round", root.getReviewRound());
        event.put("role", accessPolicy.isPrincipal(user) ? "Hiệu trưởng" : "Phó hiệu trưởng");
        event.put("tone", TONE_NONE);
        root.changeHistory(history.toString(), user.userId());
        root.touchDossier(at);
        classDocRepository.saveAndFlush(root);
        Long homeroom = homeroomOf(root.getClassroomId(), today);
        if (homeroom != null)
            notificationService.notifyUsers(
                    Set.of(homeroom),
                    com.carenest.utils.enums.NotificationType.EDU_DEADLINE_EXTENDED,
                    "Đã mở hạn: " + labelOf(root, requireClass(root.getClassroomId())),
                    user.fullName() + " mở hạn tới " + deadline.format(DAY_FORMAT) + ". Lý do: " + note,
                    linkOf(root),
                    false);
        return toNode(user, root);
    }

    // ===== Thông báo kế hoạch giáo dục (spec 003 FR-010..013) =====
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static String linkOf(EduClassDoc root) {
        return EduClassDoc.SEMESTER.equals(root.getKind())
                ? "/education/class/" + root.getClassroomId() + "/semesters/" + root.getSemesterNo()
                : "/education/class/" + root.getClassroomId() + "/themes/" + root.getThemeId();
    }

    private String labelOf(EduClassDoc root, ClassroomInfoResponse cls) {
        String what = EduClassDoc.SEMESTER.equals(root.getKind())
                ? "Gói học kỳ " + root.getSemesterNo()
                : "Hồ sơ chủ đề " + themeRepository.findById(root.getThemeId()).map(EduTheme::getName).orElse("");
        return what + " · " + cls.name();
    }

    private Long homeroomOf(Long classId, LocalDate today) {
        return staffAssignmentService.findHomeroomTeacherIds(List.of(classId), today).get(classId);
    }

    /** Người duyệt hợp lệ của bước đang chờ (trừ người đã soạn). */
    private Set<Long> waitingReviewers(EduClassDoc root, ClassroomInfoResponse cls, LocalDate today) {
        var evaluation = eduWorkflowService.evaluateClassDoc(root, cls.ageGroupId(), today);
        if (evaluation.waitingRole() == null) return Set.of();
        var policy = eduWorkflowService.loadPolicy(root.getKind());
        Set<Long> ids = new HashSet<>(eduWorkflowService
                .resolveActiveCandidates(policy, cls.ageGroupId(), cls.id(), today)
                .getOrDefault(evaluation.waitingRole(), Set.of()));
        ids.removeAll(eduWorkflowService.getUnionAuthorUserIdsForDossier(root));
        return ids;
    }

    private void notifyAfterAction(
            CurrentUser user,
            EduClassDoc root,
            ClassroomInfoResponse cls,
            String action,
            EduClassDocStatus next,
            String note,
            LocalDate today) {
        String what = labelOf(root, cls);
        String noteText = note.isBlank() ? "" : ". Ghi chú: " + note;
        if (ACTION_SUBMIT.equals(action) || (ACTION_APPROVE.equals(action) && next == EduClassDocStatus.REVIEW)) {
            boolean submit = ACTION_SUBMIT.equals(action);
            notificationService.notifyUsers(
                    waitingReviewers(root, cls, today),
                    submit
                            ? com.carenest.utils.enums.NotificationType.EDU_SUBMITTED
                            : com.carenest.utils.enums.NotificationType.EDU_STEP_APPROVED,
                    (submit ? "Chờ bạn duyệt: " : "Chuyển bạn duyệt tiếp: ") + what,
                    user.fullName() + (submit ? " gửi duyệt" : " đã duyệt bước trước") + noteText,
                    linkOf(root),
                    false);
            return;
        }
        Long homeroom = homeroomOf(cls.id(), today);
        if (homeroom == null) return;
        if (ACTION_APPROVE.equals(action) && next == EduClassDocStatus.APPROVED)
            notificationService.notifyUsers(
                    Set.of(homeroom),
                    com.carenest.utils.enums.NotificationType.EDU_RELEASED,
                    "Đã ban hành: " + what,
                    user.fullName() + " đã duyệt ban hành" + noteText,
                    linkOf(root),
                    false);
        else if (ACTION_REJECT.equals(action))
            notificationService.notifyUsers(
                    Set.of(homeroom),
                    com.carenest.utils.enums.NotificationType.EDU_RETURNED,
                    "Bị trả lại: " + what,
                    user.fullName() + " trả lại. Lý do: " + note,
                    linkOf(root),
                    false);
    }

    private ObjectNode readObject(String json) {
        JsonNode node = objectMapper.readTree(json);
        return node instanceof ObjectNode object ? object : objectMapper.createObjectNode();
    }

    private ArrayNode readArray(String json) {
        JsonNode node = objectMapper.readTree(json);
        return node instanceof ArrayNode array ? array : objectMapper.createArrayNode();
    }
}
