package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.BranchFilePreviewResponse;
import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.dto.eduplan.YearPlanChangesResponse;
import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.eduplan.EduBreak;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduBreakRepository;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduSettingsRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.repository.eduplan.EduYearPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.EduPermission;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduClassDocStatus;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
            Map<Long, EduYearPlan> plans,
            Set<Long> supersededPlanIds,
            Map<Long, EduClassDoc> docsById,
            Map<String, EduClassDoc> docsByKey) {}

    private static final String ACTION_SUBMIT = "submit";
    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_REJECT = "reject";
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
            "superseded");
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
    private final EduYearPlanRepository yearPlanRepository;
    private final EduThemeRepository themeRepository;
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

    /** Tuần học của kế hoạch năm: số thứ tự cả năm, thứ Hai đầu tuần, chủ đề. */
    record TeachingWeek(int no, LocalDate start, Long themeId) {}

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
            if (!eduWorkflowService.isAuthorEligible(currentUser, "branch", authorClass.ageGroupId(), classId)) {
                throw GlobalException.of(ApiCode.CLASS_DOC_TEACHER_ONLY);
            }
            ClassroomInfoResponse cls = requireClass(classId);
            EduYearPlan plan = requireIssuedPlan(yearPlanId, cls);
            if (isSuperseded(plan.getId())) throw GlobalException.of(ApiCode.YEAR_PLAN_SUPERSEDED);
            classDocRepository
                    .findByDocKey(expectedKey(EduClassDoc.BRANCH, classId, plan, null, null))
                    .filter(doc -> !doc.getStatus().editable())
                    .ifPresent(doc -> {
                        throw GlobalException.of(ApiCode.CLASS_DOC_SUBMITTED);
                    });
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
            Map<Long, List<TeachingWeek>> weeks =
                    teachingWeeks(themes, breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId())).stream()
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
                if (!eduWorkflowService.isAuthorEligible(currentUser, "theme", cls.ageGroupId(), cls.id()))
                    throw GlobalException.of(ApiCode.CLASS_DOC_TEACHER_ONLY);
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
                                    .toList()))
                    .toList();

            List<EduClassDoc> docs =
                    classIds.isEmpty() ? List.of() : classDocRepository.findAllByClassroomIdInOrderByIdAsc(classIds);
            Map<Long, EduWorkflowPolicy.Evaluation> evaluations = eduWorkflowService.evaluateClassDocs(docs, today);
            Map<Long, ClassroomInfoResponse> classInfoById =
                    classes.stream().collect(Collectors.toMap(ClassroomInfoResponse::id, c -> c, (a, b) -> a));
            Map<String, EduWorkflowPolicy.RolePolicy> policies = eduWorkflowService.loadAllPolicies();
            List<EduYearPlan> referencedPlans = yearPlanRepository.findAllById(docs.stream()
                    .map(EduClassDoc::getYearPlanId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
            Set<Long> planSchoolYears = referencedPlans.stream()
                    .map(EduYearPlan::getSchoolYearId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, EduYearPlan> plans = (planSchoolYears.isEmpty()
                            ? referencedPlans
                            : yearPlanRepository.findAllBySchoolYearIdIn(planSchoolYears))
                    .stream().collect(Collectors.toMap(EduYearPlan::getId, p -> p));
            Map<String, Integer> latestApprovedVersions = new LinkedHashMap<>();
            plans.values().stream()
                    .filter(p -> p.getStatus() == EduPlanStatus.APPROVED)
                    .forEach(p -> latestApprovedVersions.merge(
                            p.getSchoolYearId() + ":" + p.getAgeGroupId(), p.getVersionNo(), Math::max));
            Set<Long> supersededPlanIds = plans.values().stream()
                    .filter(p -> p.getVersionNo()
                            < latestApprovedVersions.getOrDefault(
                                    p.getSchoolYearId() + ":" + p.getAgeGroupId(), p.getVersionNo()))
                    .map(EduYearPlan::getId)
                    .collect(Collectors.toSet());
            Map<Long, EduClassDoc> docsById = docs.stream().collect(Collectors.toMap(EduClassDoc::getId, d -> d));
            Map<String, EduClassDoc> docsByKey =
                    docs.stream().collect(Collectors.toMap(EduClassDoc::getDocKey, d -> d));
            ClassListContext context = new ClassListContext(
                    classInfoById,
                    policies,
                    eduWorkflowService.getPolicyRevision(),
                    accessPolicy.classScopeSnapshot(currentUser),
                    eduWorkflowService.getUnionAuthorUserIdsForDossiers(docs),
                    plans,
                    supersededPlanIds,
                    docsById,
                    docsByKey);
            ClassDocsResponse response = new ClassDocsResponse(
                    toNodes(currentUser, docs, EduClassDoc.BRANCH, evaluations, context),
                    toNodes(currentUser, docs, EduClassDoc.THEME, evaluations, context),
                    toNodes(currentUser, docs, EduClassDoc.WEEK, evaluations, context),
                    classItems,
                    List.copyOf(staff.values()),
                    buildCreationCapabilities(classItems, codes, context));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<ClassDocsResponse.CreationCapability> buildCreationCapabilities(
            List<ClassDocsResponse.ClassItem> classItems, Map<Long, String> codes, ClassListContext context) {
        List<ClassDocsResponse.CreationCapability> list = new ArrayList<>();
        for (ClassDocsResponse.ClassItem cls : classItems) {
            Long classId = Long.parseLong(cls.id());
            Long ageGroupId = codes.entrySet().stream()
                    .filter(e -> Objects.equals(e.getValue(), cls.ageGroupId()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            ClassroomInfoResponse classroom = context.classes().get(classId);
            boolean canAuthorBranch = context.scope()
                    .canAuthor(
                            context.policies().get("branch"),
                            ageGroupId,
                            classroom == null ? null : classroom.campusId(),
                            classId);
            boolean canAuthorTheme = context.scope()
                    .canAuthor(
                            context.policies().get("theme"),
                            ageGroupId,
                            classroom == null ? null : classroom.campusId(),
                            classId);
            list.add(new ClassDocsResponse.CreationCapability(
                    classId,
                    "branch",
                    canAuthorBranch,
                    "ISSUED_YEAR_PLAN",
                    canAuthorBranch ? null : "AUTHOR_FORBIDDEN"));
            list.add(new ClassDocsResponse.CreationCapability(
                    classId, "theme", canAuthorTheme, "APPROVED_BRANCH", canAuthorTheme ? null : "AUTHOR_FORBIDDEN"));
            list.add(new ClassDocsResponse.CreationCapability(
                    classId, "week", canAuthorTheme, "THEME_DOSSIER", canAuthorTheme ? null : "AUTHOR_FORBIDDEN"));
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
            Long classId = requireLong(doc, "classId");
            Long yearPlanId = requireLong(doc, "yearPlanId");
            Long themeId = EduClassDoc.BRANCH.equals(kind) ? null : requireLong(doc, "themeId");
            LocalDate weekStart = EduClassDoc.WEEK.equals(kind) ? requireDate(doc, "weekStart") : null;

            ClassroomInfoResponse cls = requireClass(classId);
            LocalDate today = LocalDate.now(clock);
            // EDU-12: author eligibility according to policy and reviewer precedence
            if (!eduWorkflowService.isAuthorEligible(currentUser, kind, cls.ageGroupId(), classId)) {
                throw GlobalException.of(ApiCode.CLASS_DOC_TEACHER_ONLY);
            }
            EduYearPlan plan = requireIssuedPlan(yearPlanId, cls);
            if (!expectedKey(kind, classId, plan, themeId, weekStart).equals(docKey)) {
                throw GlobalException.of(ApiCode.CLASS_DOC_KEY_MISMATCH);
            }
            // EDU-26: phiên bản kế hoạch năm đã bị thay thế thì tài liệu của nó chỉ xem
            if (isSuperseded(plan.getId())) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_SUPERSEDED);
            }
            List<TeachingWeek> weeks = teachingWeeks(plan.getId());
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
            EduClassDoc root = null;
            if (EduClassDoc.WEEK.equals(kind)) {
                if (expectedRootVersion == null)
                    throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "X-Dossier-Version");
                root = lockDossierRoot(classId, plan.getId(), themeId);
                if (!expectedRootVersion.equals(root.getVersion())) throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
                if (!root.getStatus().editable()) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
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
                // EDU-18: chỉ sửa khi Nháp hoặc Bị trả lại
                if (!entity.getStatus().editable()) {
                    throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
                }
            }
            entity.changeContent(objectMapper.writeValueAsString(contentOf(doc)), currentUser.userId());
            if (root != null) root.touchDossier(clock.instant());
            EduClassDoc saved = classDocRepository.saveAndFlush(entity);
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
            if (EduClassDoc.WEEK.equals(kind)) throw GlobalException.of(ApiCode.WEEKLY_LESSON_VIA_THEME);
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
            var all = new ArrayList<EduClassDoc>();
            all.add(doc);
            if (EduClassDoc.THEME.equals(kind)) {
                classDocRepository
                        .findAllByDossierForUpdateOrderByIdAsc(cls.id(), doc.getYearPlanId(), doc.getThemeId())
                        .stream()
                        .filter(d -> !d.getId().equals(doc.getId()))
                        .forEach(all::add);
            }
            String note = request.note() == null ? "" : request.note().trim();
            int round = doc.getReviewRound();
            EduWorkflowPolicy.Role actingRole = evaluation.waitingRole();
            EduClassDocStatus next = EduClassDocStatus.REVIEW;
            if (ACTION_SUBMIT.equals(request.action())) {
                if (!eduWorkflowService.isAuthorEligible(currentUser, kind, cls.ageGroupId(), cls.id()))
                    throw GlobalException.of(ApiCode.CLASS_DOC_TEACHER_ONLY);
                if (!doc.getStatus().editable()) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
                if (isSuperseded(doc.getYearPlanId())) throw GlobalException.of(ApiCode.YEAR_PLAN_SUPERSEDED);
                requireCanSubmit(doc);
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
            EduClassDoc found = classDocRepository
                    .findByDocKey(docKey)
                    .filter(d -> EduClassDoc.WEEK.equals(d.getKind()))
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_NOT_FOUND));
            if (expectedVersion == null || expectedRootVersion == null)
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "X-Class-Doc-Version, X-Dossier-Version");
            yearPlanRepository.findForUpdateById(found.getYearPlanId()).orElseThrow();
            EduClassDoc root = lockDossierRoot(found.getClassroomId(), found.getYearPlanId(), found.getThemeId());
            EduClassDoc doc =
                    classDocRepository.findForUpdateById(found.getId()).orElseThrow();
            if (!expectedVersion.equals(doc.getVersion()) || !expectedRootVersion.equals(root.getVersion()))
                throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
            ClassroomInfoResponse cls = requireClass(doc.getClassroomId());
            if (!eduWorkflowService.isAuthorEligible(currentUser, "theme", cls.ageGroupId(), cls.id()))
                throw GlobalException.of(ApiCode.CLASS_DOC_TEACHER_ONLY);
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

    private EduClassDoc lockDossierRoot(Long classId, Long yearPlanId, Long themeId) {
        return classDocRepository
                .findForUpdateByDocKey("tp_" + classId + "_" + themeId)
                .filter(d -> Objects.equals(d.getYearPlanId(), yearPlanId))
                .orElseThrow(() -> GlobalException.of(ApiCode.THEME_PLAN_NOT_FOUND));
    }

    // Khóa Web: giáo án tuần phiên bản ≥2 thêm id kế hoạch để không trùng tuần của phiên bản cũ (spec 004 A4)
    private static String expectedKey(String kind, Long classId, EduYearPlan plan, Long themeId, LocalDate weekStart) {
        return switch (kind) {
            case EduClassDoc.BRANCH -> "bp_" + classId + "_" + plan.getId();
            case EduClassDoc.THEME -> "tp_" + classId + "_" + themeId;
            default -> "wp_" + classId + "_" + weekStart + (plan.getVersionNo() > 1 ? "_" + plan.getId() : "");
        };
    }

    /** Tài liệu nguồn của bản chép: cùng lớp, cùng loại, phiên bản thấp hơn của cùng chuỗi kế hoạch năm. */
    private EduClassDoc requireCopySource(String sourceKey, String kind, Long classId, EduYearPlan plan) {
        EduClassDoc source = classDocRepository
                .findByDocKey(sourceKey)
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_KEY_MISMATCH));
        EduYearPlan sourcePlan = yearPlanRepository
                .findById(source.getYearPlanId())
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_KEY_MISMATCH));
        if (!source.getKind().equals(kind)
                || !source.getClassroomId().equals(classId)
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
            List<TeachingWeek> weeks = teachingWeeks(doc.getYearPlanId());
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

    // EDU-26: có phiên bản ban hành số lớn hơn trong cùng chuỗi (năm học, nhóm tuổi)
    private boolean isSuperseded(Long yearPlanId) {
        return yearPlanRepository
                .findById(yearPlanId)
                .map(plan -> yearPlanRepository
                        .findAllBySchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                                plan.getSchoolYearId(), plan.getAgeGroupId())
                        .stream()
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

    /** EDU-12: tài liệu lớp dựa trên kế hoạch năm đã ban hành của đúng nhóm tuổi, năm học của lớp. */
    private EduYearPlan requireIssuedPlan(Long yearPlanId, ClassroomInfoResponse cls) {
        EduYearPlan plan = yearPlanRepository
                .findById(yearPlanId)
                .orElseThrow(() -> GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
        if (!plan.getSchoolYearId().equals(cls.schoolYearId())
                || !plan.getAgeGroupId().equals(cls.ageGroupId())) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_CLASS_MISMATCH);
        }
        if (plan.getStatus() != EduPlanStatus.APPROVED) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_NOT_ISSUED);
        }
        return plan;
    }

    /** EDU-13: chủ đề nhánh đã duyệt mới lập kế hoạch chủ đề; có kế hoạch chủ đề mới lập giáo án tuần. */
    private void requireCanCreate(String kind, Long classId, Long yearPlanId, Long themeId) {
        if (EduClassDoc.BRANCH.equals(kind)) {
            return;
        }
        List<EduClassDoc> docs = classDocRepository.findAllByClassroomIdAndYearPlanId(classId, yearPlanId);
        boolean branchApproved = docs.stream()
                .anyMatch(d -> EduClassDoc.BRANCH.equals(d.getKind()) && d.getStatus() == EduClassDocStatus.APPROVED);
        if (!branchApproved) {
            throw GlobalException.of(ApiCode.BRANCH_THEMES_NOT_APPROVED);
        }
        if (EduClassDoc.WEEK.equals(kind)
                && docs.stream()
                        .noneMatch(d -> EduClassDoc.THEME.equals(d.getKind()) && themeId.equals(d.getThemeId()))) {
            throw GlobalException.of(ApiCode.THEME_PLAN_NOT_SAVED);
        }
    }

    /** EDU-14, EDU-17: điều kiện gửi chủ đề nhánh / hồ sơ chủ đề. */
    private void requireCanSubmit(EduClassDoc doc) {
        List<TeachingWeek> weeks = teachingWeeks(doc.getYearPlanId());
        if (EduClassDoc.BRANCH.equals(doc.getKind())) {
            JsonNode names = readObject(doc.getContent()).path("names");
            long empty = weeks.stream()
                    .filter(w -> names.path(w.start().toString()).asString("").isBlank())
                    .count();
            if (empty > 0) {
                throw GlobalException.of(ApiCode.BRANCH_WEEKS_UNNAMED, empty);
            }
            return;
        }
        boolean branchApproved =
                classDocRepository.findAllByClassroomIdAndYearPlanId(doc.getClassroomId(), doc.getYearPlanId()).stream()
                        .anyMatch(d ->
                                EduClassDoc.BRANCH.equals(d.getKind()) && d.getStatus() == EduClassDocStatus.APPROVED);
        if (!branchApproved) {
            throw GlobalException.of(ApiCode.BRANCH_THEMES_NOT_APPROVED);
        }
        List<TeachingWeek> themeWeeks =
                weeks.stream().filter(w -> w.themeId().equals(doc.getThemeId())).toList();
        Set<LocalDate> written =
                dossierWeekDocs(doc).stream().map(EduClassDoc::getWeekStart).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < themeWeeks.size(); i++) {
            if (!written.contains(themeWeeks.get(i).start())) {
                missing.add("nhánh " + (i + 1));
            }
        }
        if (!missing.isEmpty()) {
            throw GlobalException.of(ApiCode.WEEKLY_LESSONS_MISSING, String.join(", ", missing));
        }
    }

    /** Giáo án các tuần nhánh của hồ sơ chủ đề (EDU-17), cùng lớp và kế hoạch năm. */
    private List<EduClassDoc> dossierWeekDocs(EduClassDoc themeDoc) {
        Set<LocalDate> starts = teachingWeeks(themeDoc.getYearPlanId()).stream()
                .filter(w -> w.themeId().equals(themeDoc.getThemeId()))
                .map(TeachingWeek::start)
                .collect(Collectors.toSet());
        return classDocRepository
                .findAllByClassroomIdAndYearPlanId(themeDoc.getClassroomId(), themeDoc.getYearPlanId())
                .stream()
                .filter(d -> EduClassDoc.WEEK.equals(d.getKind()) && starts.contains(d.getWeekStart()))
                .toList();
    }

    private static void requireKind(String kind) {
        if (!Set.of(EduClassDoc.BRANCH, EduClassDoc.THEME, EduClassDoc.WEEK).contains(kind)) {
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
                .map(d -> toNode(currentUser, d, evaluations.get(d.getId()), context))
                .toList();
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
        node.set("approvals", readArray(doc.getApprovals()));
        node.set("history", readArray(doc.getHistory()));
        EduYearPlan cachedPlan = context == null ? null : context.plans().get(doc.getYearPlanId());
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
        String type = EduClassDoc.WEEK.equals(doc.getKind()) ? "theme" : doc.getKind();
        ClassroomInfoResponse cls = context == null
                ? requireClass(doc.getClassroomId())
                : context.classes().get(doc.getClassroomId());
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
        if (EduClassDoc.WEEK.equals(doc.getKind())) {
            String rootKey = "tp_" + doc.getClassroomId() + "_" + doc.getThemeId();
            root = context == null
                    ? classDocRepository.findByDocKey(rootKey).orElse(doc)
                    : context.docsByKey().getOrDefault(rootKey, doc);
        }
        node.put("dossierVersion", root.getVersion() == null ? 0L : root.getVersion());
        boolean author = context == null
                ? eduWorkflowService.isAuthorEligible(currentUser, type, cls.ageGroupId(), cls.id())
                : context.scope().canAuthor(policy, cls.ageGroupId(), cls.campusId(), cls.id());
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
        ObjectNode permissions = node.putObject("permissions");
        permissions.put(
                "canEdit",
                author
                        && activeVersion
                        && doc.getStatus().editable()
                        && root.getStatus().editable());
        permissions.put(
                "canSubmit",
                author && activeVersion && doc.getStatus().editable() && !EduClassDoc.WEEK.equals(doc.getKind()));
        permissions.put("canApprove", reviewable && !EduClassDoc.WEEK.equals(doc.getKind()));
        permissions.put("canReturn", reviewable && !EduClassDoc.WEEK.equals(doc.getKind()));
        permissions.put("canReject", reviewable && !EduClassDoc.WEEK.equals(doc.getKind()));
        boolean canAdjust = author && doc.getStatus() != EduClassDocStatus.REVIEW;
        permissions.put("canAdjust", canAdjust);
        permissions.put("canEditAdjustments", canAdjust);
        node.set("adjustments", readObject(doc.getAdjustments()));
        return node;
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
