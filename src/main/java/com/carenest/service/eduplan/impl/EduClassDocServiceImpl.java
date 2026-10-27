package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.eduplan.EduBreak;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduBreakRepository;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.repository.eduplan.EduYearPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduConfigService;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class EduClassDocServiceImpl implements EduClassDocService {
    private static final String LOG_PREFIX = "[EduClassDocServiceImpl]";

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
    private static final Set<String> META_FIELDS =
            Set.of("id", "kind", "classId", "yearPlanId", "themeId", "weekStart", "status", "approvals", "history");
    private static final Map<String, EduClassDocStatus> PENDING_OF = Map.of(
            ROLE_TEAM_LEADER, EduClassDocStatus.PENDING_TL,
            ROLE_VICE_PRINCIPAL, EduClassDocStatus.PENDING_VP,
            ROLE_PRINCIPAL, EduClassDocStatus.PENDING_HT);
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
    @Transactional(readOnly = true)
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
            ClassDocsResponse response = new ClassDocsResponse(
                    toNodes(docs, EduClassDoc.BRANCH),
                    toNodes(docs, EduClassDoc.THEME),
                    toNodes(docs, EduClassDoc.WEEK),
                    classItems,
                    List.copyOf(staff.values()));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ObjectNode saveClassDoc(CurrentUser currentUser, String kind, String docKey, ObjectNode doc) {
        String logPrefix =
                LOG_PREFIX + "|saveClassDoc|userId=" + currentUser.userId() + "|kind=" + kind + "|docKey=" + docKey;
        log.info("{}|START", logPrefix);
        try {
            requireKind(kind);
            Long classId = requireLong(doc, "classId");
            Long yearPlanId = requireLong(doc, "yearPlanId");
            Long themeId = EduClassDoc.BRANCH.equals(kind) ? null : requireLong(doc, "themeId");
            LocalDate weekStart = EduClassDoc.WEEK.equals(kind) ? requireDate(doc, "weekStart") : null;
            String expectedKey =
                    switch (kind) {
                        case EduClassDoc.BRANCH -> "bp_" + classId + "_" + yearPlanId;
                        case EduClassDoc.THEME -> "tp_" + classId + "_" + themeId;
                        default -> "wp_" + classId + "_" + weekStart;
                    };
            if (!expectedKey.equals(docKey)) {
                throw GlobalException.of(ApiCode.CLASS_DOC_KEY_MISMATCH);
            }

            ClassroomInfoResponse cls = requireClass(classId);
            LocalDate today = LocalDate.now(clock);
            // EDU-12: chỉ giáo viên được phân công vào lớp soạn
            if (!staffAssignmentService
                    .findCurrentTeacherClassroomIds(currentUser.userId(), today)
                    .contains(classId)) {
                throw GlobalException.of(ApiCode.CLASS_DOC_TEACHER_ONLY);
            }
            EduYearPlan plan = requireIssuedPlan(yearPlanId, cls);
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

            Optional<EduClassDoc> current = classDocRepository.findForUpdateByDocKey(docKey);
            EduClassDoc entity;
            if (current.isEmpty()) {
                requireCanCreate(kind, cls.id(), plan.getId(), themeId);
                entity = EduClassDoc.create(
                        docKey, kind, classId, plan.getId(), themeId, weekStart, currentUser.userId());
            } else {
                entity = current.get();
                // EDU-18: chỉ sửa khi Nháp hoặc Bị trả lại
                if (!entity.getStatus().editable()) {
                    throw GlobalException.of(ApiCode.CLASS_DOC_SUBMITTED);
                }
            }
            entity.changeContent(objectMapper.writeValueAsString(contentOf(doc)), currentUser.userId());
            EduClassDoc saved = classDocRepository.saveAndFlush(entity);
            ObjectNode response = toNode(saved);
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
            CurrentUser currentUser, String kind, String docKey, ClassDocActionRequest request) {
        String logPrefix = LOG_PREFIX + "|actOnClassDoc|userId=" + currentUser.userId() + "|kind=" + kind + "|docKey="
                + docKey + "|action=" + request.action();
        log.info("{}|START", logPrefix);
        try {
            requireKind(kind);
            // EDU-17: giáo án tuần đi cùng hồ sơ chủ đề, không gửi riêng
            if (EduClassDoc.WEEK.equals(kind)) {
                throw GlobalException.of(ApiCode.WEEKLY_LESSON_VIA_THEME);
            }
            EduClassDoc doc = classDocRepository
                    .findForUpdateByDocKey(docKey)
                    .filter(d -> d.getKind().equals(kind))
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_NOT_FOUND));
            ClassroomInfoResponse cls = requireClass(doc.getClassroomId());
            LocalDate today = LocalDate.now(clock);
            Set<Long> leaders = cls.ageGroupId() == null
                    ? Set.of()
                    : staffAssignmentService.findActiveTeamLeaderIds(cls.ageGroupId(), today);
            List<Long> classTeachers = staffAssignmentService
                    .findCurrentTeacherIds(List.of(cls.id()), today)
                    .getOrDefault(cls.id(), List.of());
            // EDU-18: bỏ bước Tổ trưởng khi nhóm tuổi không có Tổ trưởng hoặc Tổ trưởng dạy chính lớp đó
            boolean skipLeader = leaders.isEmpty() || leaders.stream().anyMatch(classTeachers::contains);
            EduConfigResponse.Signers signers = eduConfigService.getEduConfig().signers();
            List<String> chain = (EduClassDoc.BRANCH.equals(kind) ? signers.branch() : signers.week())
                    .stream()
                            .filter(r -> !ROLE_TEAM_LEADER.equals(r) || !skipLeader)
                            .toList();
            String waiting = PENDING_OF.entrySet().stream()
                    .filter(e -> e.getValue() == doc.getStatus())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);

            List<EduClassDoc> all = new ArrayList<>(List.of(doc));
            if (EduClassDoc.THEME.equals(kind)) {
                all.addAll(dossierWeekDocs(doc));
            }
            String note = request.note() == null || request.note().isBlank()
                    ? null
                    : request.note().trim();
            String actorRole = actorRole(currentUser, leaders);
            EduClassDocStatus next;
            ObjectNode approval = null;
            String action;
            String tone;
            switch (request.action()) {
                case ACTION_SUBMIT -> {
                    if (!classTeachers.contains(currentUser.userId())) {
                        throw GlobalException.of(ApiCode.CLASS_DOC_TEACHER_ONLY);
                    }
                    if (!doc.getStatus().editable()) {
                        throw GlobalException.of(ApiCode.CLASS_DOC_SUBMITTED);
                    }
                    requireCanSubmit(doc);
                    next = chain.isEmpty() ? EduClassDocStatus.APPROVED : PENDING_OF.get(chain.getFirst());
                    action = chain.isEmpty()
                            ? "Gửi (không cần duyệt)"
                            : "Gửi " + SIGN_ROLE_LABELS.get(chain.getFirst()) + " duyệt";
                    tone = TONE_NONE;
                }
                case ACTION_APPROVE -> {
                    requireWaitingFor(currentUser, waiting, leaders, "duyệt");
                    int index = chain.indexOf(waiting);
                    String nextRole = index >= 0 && index + 1 < chain.size() ? chain.get(index + 1) : null;
                    next = nextRole == null ? EduClassDocStatus.APPROVED : PENDING_OF.get(nextRole);
                    approval = objectMapper.createObjectNode();
                    approval.put("userId", currentUser.userId());
                    approval.put("name", currentUser.fullName());
                    approval.put("role", ROLE_LABELS.get(actorRole));
                    approval.put("roleKey", actorRole);
                    approval.put("at", now());
                    approval.put("note", note == null ? "" : note);
                    action = nextRole == null
                            ? SIGN_ROLE_LABELS.get(waiting) + " duyệt"
                            : SIGN_ROLE_LABELS.get(waiting) + " duyệt, chuyển " + SIGN_ROLE_LABELS.get(nextRole);
                    tone = TONE_OK;
                }
                case ACTION_REJECT -> {
                    requireWaitingFor(currentUser, waiting, leaders, "trả lại");
                    if (note == null) {
                        throw GlobalException.of(ApiCode.RETURN_REASON_REQUIRED);
                    }
                    next = EduClassDocStatus.REJECTED;
                    action = "Trả lại để sửa";
                    tone = TONE_ERR;
                }
                default -> throw GlobalException.of(ApiCode.INVALID_VALUE, "hành động");
            }

            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("action", action);
            entry.put("by", currentUser.fullName());
            entry.put("role", ROLE_LABELS.get(actorRole));
            entry.put("at", now());
            entry.put("tone", tone);
            if (note != null) {
                entry.put("note", note);
            }
            for (int i = 0; i < all.size(); i++) {
                EduClassDoc d = all.get(i);
                ArrayNode approvals = approval == null ? objectMapper.createArrayNode() : readArray(d.getApprovals());
                if (approval != null) {
                    approvals.add(approval);
                }
                ArrayNode history = readArray(d.getHistory());
                ObjectNode logged = entry.deepCopy();
                if (i > 0) {
                    logged.put("action", action + " (kế hoạch chủ đề)");
                }
                history.add(logged);
                d.changeStatus(
                        next,
                        objectMapper.writeValueAsString(approvals),
                        objectMapper.writeValueAsString(history),
                        currentUser.userId());
            }
            classDocRepository.saveAllAndFlush(all);
            ObjectNode response = toNode(doc);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /**
     * Lịch tuần cả năm (giống Web): tuần học đánh số liên tục theo các chủ đề, bỏ tuần trùng đợt nghỉ; chủ đề bắt đầu
     * Thứ 7 / Chủ nhật thì tuần đầu là Thứ Hai kế tiếp.
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
                LocalDate friday = monday.plusDays(4);
                LocalDate start = monday;
                boolean onBreak = breaks.stream()
                        .filter(b -> b.getStartDate() != null)
                        .anyMatch(b -> !start.isAfter(b.getEndDate() == null ? b.getStartDate() : b.getEndDate())
                                && !friday.isBefore(b.getStartDate()));
                if (!onBreak) {
                    weeks.add(new TeachingWeek(++no, monday, theme.getId()));
                }
            }
        }
        return weeks;
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

    /** Phó HT, HT xem mọi lớp; giáo viên xem lớp mình dạy và các lớp thuộc nhóm tuổi mình làm tổ trưởng. */
    private List<ClassroomInfoResponse> visibleClasses(CurrentUser user, Long schoolYearId, LocalDate today) {
        List<ClassroomInfoResponse> classes = classroomService.findYearClassrooms(schoolYearId);
        if (accessPolicy.isVicePrincipal(user) || user.hasRole(CareNestRole.PRINCIPAL)) {
            return classes;
        }
        Set<Long> taught = staffAssignmentService.findCurrentTeacherClassroomIds(user.userId(), today);
        Set<Long> led = staffAssignmentService.findActiveTeamLeaderAgeGroupIds(user.userId(), today);
        return classes.stream()
                .filter(c -> taught.contains(c.id()) || (c.ageGroupId() != null && led.contains(c.ageGroupId())))
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

    /** EDU-18: đúng lượt, đúng vai trò; Tổ trưởng chỉ duyệt lớp thuộc nhóm tuổi mình. */
    private void requireWaitingFor(CurrentUser user, String waiting, Set<Long> leaders, String action) {
        boolean allowed = waiting != null
                && switch (waiting) {
                    case ROLE_TEAM_LEADER -> leaders.contains(user.userId());
                    case ROLE_VICE_PRINCIPAL -> accessPolicy.isVicePrincipal(user);
                    case ROLE_PRINCIPAL -> user.hasRole(CareNestRole.PRINCIPAL);
                    default -> false;
                };
        if (!allowed) {
            throw GlobalException.of(ApiCode.NOT_YOUR_REVIEW_STEP, action);
        }
    }

    private String actorRole(CurrentUser user, Set<Long> leaders) {
        if (accessPolicy.isVicePrincipal(user)) {
            return ROLE_VICE_PRINCIPAL;
        }
        if (user.hasRole(CareNestRole.PRINCIPAL)) {
            return ROLE_PRINCIPAL;
        }
        return leaders.contains(user.userId()) ? ROLE_TEAM_LEADER : "TEACHER";
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

    private List<ObjectNode> toNodes(List<EduClassDoc> docs, String kind) {
        return docs.stream()
                .filter(d -> kind.equals(d.getKind()))
                .map(this::toNode)
                .toList();
    }

    /** Dạng tài liệu của Web: nội dung soạn + id, lớp, kế hoạch năm, chủ đề, tuần, trạng thái, người duyệt, lịch sử. */
    private ObjectNode toNode(EduClassDoc doc) {
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
        node.put("status", doc.getStatus().name());
        node.set("approvals", readArray(doc.getApprovals()));
        node.set("history", readArray(doc.getHistory()));
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
