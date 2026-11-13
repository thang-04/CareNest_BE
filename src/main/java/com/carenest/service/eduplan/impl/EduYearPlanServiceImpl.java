package com.carenest.service.eduplan.impl;

import com.carenest.config.EduPlanProperties;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.CurriculumRef;
import com.carenest.dto.eduplan.YearPlanChangesResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.eduplan.EduBreak;
import com.carenest.entity.eduplan.EduContentThemeTick;
import com.carenest.entity.eduplan.EduDomain;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduPlanBranchTemplate;
import com.carenest.entity.eduplan.EduPlanEvent;
import com.carenest.entity.eduplan.EduPlanExpectedResult;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.entity.eduplan.EduPlanSubDomain;
import com.carenest.entity.eduplan.EduSettings;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.eduplan.EduYearPlanMapper;
import com.carenest.repository.eduplan.EduBreakRepository;
import com.carenest.repository.eduplan.EduContentThemeTickRepository;
import com.carenest.repository.eduplan.EduDomainRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanBranchTemplateRepository;
import com.carenest.repository.eduplan.EduPlanEventRepository;
import com.carenest.repository.eduplan.EduPlanExpectedResultRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.repository.eduplan.EduPlanSubDomainRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.eduplan.EduCurriculumService;
import com.carenest.service.eduplan.EduDurationPolicyService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.EduPermission;
import com.carenest.utils.enums.EduGoalRowKind;
import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EduYearPlanServiceImpl implements EduYearPlanService {
    private static final String LOG_PREFIX = "[EduYearPlanServiceImpl]";

    // Nhãn lịch sử và chức danh theo UI (GoalFormPage, GoalDetailPage, seed kế
    // hoạch năm)
    private static final String TITLE_VICE_PRINCIPAL = "Phó hiệu trưởng";
    private static final String TITLE_TEAM_LEADER = "Tổ trưởng nhóm tuổi";
    private static final String ACTION_SUBMIT = "SUBMIT";
    private static final String ACTION_ISSUE = "ISSUE";
    private static final String ACTION_RETURN = "RETURN";
    private static final String ACTION_VP_EDIT = "VP_EDIT";
    private static final String ACTION_CREATE_VERSION = "CREATE_VERSION";
    private static final String TONE_NONE = "";
    private static final String TONE_OK = "ok";
    private static final String TONE_ERR = "err";

    private final EduPlanRepository yearPlanRepository;
    private final EduPlanSubDomainRepository subDomainRepository;
    private final EduPlanExpectedResultRepository expectedResultRepository;
    private final EduPlanGoalRepository goalRepository;
    private final EduGoalContentRepository goalContentRepository;
    private final EduThemeRepository themeRepository;
    private final EduBreakRepository breakRepository;
    private final EduContentThemeTickRepository tickRepository;
    private final EduDomainRepository domainRepository;
    private final EduPlanEventRepository eventRepository;
    private final EduPlanAccessPolicy accessPolicy;
    private final EduCurriculumService curriculumService;
    private final SchoolStructureService schoolStructureService;
    private final AccountService accountService;
    private final EduYearPlanMapper mapper;
    private final EduPlanProperties properties;
    private final EduWorkflowService eduWorkflowService;
    private final com.carenest.repository.eduplan.EduSettingsRepository settingsRepository;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final Clock clock;
    private final EduDurationPolicyService durationPolicyService;
    private final EduPlanBranchTemplateRepository templateRepository;
    private final YearPlanPublicationValidator publicationValidator;
    private final com.carenest.service.eduplan.EduTeamPlanService teamService;
    private final EduTeamUpdateGenerator teamUpdates;
    private final EduPlanTimeLock timeLock;
    private final com.carenest.service.notification.NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<YearPlanSummaryResponse> getYearPlans(
            CurrentUser user, Long yearId, Long ageId, String keyword, Pageable pageable) {
        return getYearPlans(user, yearId, ageId, keyword, pageable, EduPlanLevel.TEAM);
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<YearPlanSummaryResponse> getYearPlans(
            CurrentUser currentUser,
            Long schoolYearId,
            Long ageGroupId,
            String keyword,
            Pageable pageable,
            EduPlanLevel level) {
        String logPrefix = LOG_PREFIX + "|getYearPlans|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            requirePermission(currentUser, EduPermission.GOALS_VIEW);
            EduPlanAccessPolicy.ViewScope scope = listScope(currentUser, level, schoolYearId);
            // Spec 005 FR-021a: HT cần thấy bản Nháp quá hạn chưa ban hành để mở hạn ở màn Kế hoạch quá hạn
            boolean principal = accessPolicy.isPrincipal(currentUser);
            if (principal) {
                Set<EduPlanStatus> statuses = new HashSet<>(scope.schoolWideStatuses());
                statuses.add(EduPlanStatus.DRAFT);
                scope = new EduPlanAccessPolicy.ViewScope(
                        Set.copyOf(statuses),
                        scope.fullAgeGroupIds(),
                        scope.approvedAgeGroupIds(),
                        scope.reviewAgeGroupIds());
            }
            String pattern = keyword == null || keyword.isBlank()
                    ? null
                    : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            Page<EduPlan> page = yearPlanRepository.search(
                    level,
                    schoolYearId,
                    ageGroupId,
                    scope.schoolWideStatuses(),
                    scope.fullAgeGroupIds(),
                    scope.approvedAgeGroupIds(),
                    scope.reviewAgeGroupIds(),
                    pattern,
                    pageable);
            if (principal) {
                // ponytail: lọc sau truy vấn (tổng số lệch khi có bản Nháp chưa quá hạn); mỗi năm chỉ vài kế hoạch
                List<EduPlan> visible = page.getContent().stream()
                        .filter(p -> canView(currentUser, p))
                        .toList();
                page = new org.springframework.data.domain.PageImpl<>(
                        visible,
                        pageable,
                        page.getTotalElements() - (page.getContent().size() - visible.size()));
            }
            PageResponse<YearPlanSummaryResponse> response =
                    PageResponse.from(page, summaryMapper(currentUser, page.getContent()));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<YearPlanSummaryResponse> getReviewQueue(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getReviewQueue|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            requirePermission(currentUser, EduPermission.GOALS_VIEW);
            // Bản Chờ duyệt chỉ hiện ở hàng chờ, cho đúng người duyệt hợp lệ của bước hiện tại
            List<EduPlan> plans = Stream.of(EduPlanLevel.SCHOOL_YEAR, EduPlanLevel.TEAM)
                    .flatMap(level -> yearPlanRepository
                            .search(
                                    level,
                                    null,
                                    null,
                                    Set.of(EduPlanStatus.REVIEW),
                                    Set.of(),
                                    Set.of(),
                                    Set.of(),
                                    null,
                                    Pageable.unpaged())
                            .getContent()
                            .stream())
                    .filter(plan -> isCurrentApprover(currentUser, plan))
                    .sorted(Comparator.comparing(EduPlan::getSentAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            List<YearPlanSummaryResponse> response =
                    plans.stream().map(summaryMapper(currentUser, plans)).toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private java.util.function.Function<EduPlan, YearPlanSummaryResponse> summaryMapper(
            CurrentUser currentUser, List<EduPlan> plans) {
        List<Long> planIds = plans.stream().map(EduPlan::getId).toList();
        Map<Long, Long> goalCounts =
                planIds.isEmpty() ? Map.of() : toCountMap(goalRepository.countGoalsByPlan(planIds));
        Map<Long, Long> themeCounts = planIds.isEmpty() ? Map.of() : toCountMap(themeRepository.countByPlan(planIds));
        Map<Long, String> names = accountService.findFullNames(plans.stream()
                .flatMap(p -> Stream.of(p.getOwnerId(), p.getSentBy(), p.getIssuedBy(), p.getPublishedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, String> schoolYearNames = schoolStructureService.findSchoolYears().stream()
                .collect(Collectors.toMap(SchoolYearResponse::id, SchoolYearResponse::name));
        Map<Long, String> ageGroupNames = schoolStructureService.findAgeGroups().stream()
                .collect(Collectors.toMap(AgeGroupResponse::id, AgeGroupResponse::name));
        Set<Long> currentIds = plans.stream()
                .map(p -> List.<Object>of(p.getPlanLevel(), p.getSchoolYearId(), p.getAgeGroupId()))
                .distinct()
                .map(key -> currentVersion(
                        yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                                (EduPlanLevel) key.get(0), (Long) key.get(1), (Long) key.get(2))))
                .flatMap(Optional::stream)
                .map(EduPlan::getId)
                .collect(Collectors.toSet());
        return plan -> mapper.toSummaryResponse(
                plan,
                schoolYearNames.get(plan.getSchoolYearId()),
                ageGroupNames.get(plan.getAgeGroupId()),
                goalCounts.getOrDefault(plan.getId(), 0L),
                themeCounts.getOrDefault(plan.getId(), 0L),
                names,
                null,
                currentIds.contains(plan.getId()),
                buildReviewState(plan),
                permissionsOf(currentUser, plan, List.of(plan), null),
                timeLockOf(currentUser, plan));
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public YearPlanDetailResponse getYearPlan(CurrentUser currentUser, Long planId) {
        String logPrefix = LOG_PREFIX + "|getYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadViewablePlan(currentUser, planId);
            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse createYearPlan(
            CurrentUser currentUser, YearPlanRequest request, YearPlanEditRequest edit) {
        String logPrefix =
                LOG_PREFIX + "|createYearPlan|userId=" + currentUser.userId() + "|ageGroupId=" + request.ageGroupId();
        log.info("{}|START", logPrefix);
        try {
            settingsRepository.findForUpdate().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
            if (request.schoolYearId() == null || request.ageGroupId() == null) {
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "năm học, nhóm tuổi");
            }
            SchoolYearResponse schoolYear = schoolStructureService
                    .findSchoolYear(request.schoolYearId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
            AgeGroupResponse ageGroup = schoolStructureService
                    .findAgeGroup(request.ageGroupId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));
            // Cấu hình người tạo theo workflow policy
            if (!eduWorkflowService.isAuthorEligible(
                    currentUser, request.planLevel().workflowKey(), schoolYear.id(), ageGroup.id(), null)) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_CREATE_FORBIDDEN);
            }
            requireEditContext(edit);
            Optional<EduPlan> retried =
                    yearPlanRepository.findByOwnerIdAndCreatedRequestId(currentUser.userId(), edit.requestId());
            if (retried.isPresent()) {
                EduPlan previous = retried.get();
                if (previous.isDisabled()) throw GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND);
                if (!previous.getSchoolYearId().equals(request.schoolYearId())
                        || !previous.getAgeGroupId().equals(request.ageGroupId())) {
                    throw GlobalException.of(ApiCode.YEAR_PLAN_REQUEST_REUSED);
                }
                log.info("{}|END", logPrefix);
                return toDetail(currentUser, previous);
            }

            // EDU-01
            if (yearPlanRepository.existsByPlanLevelAndSchoolYearIdAndAgeGroupId(
                    request.planLevel(), schoolYear.id(), ageGroup.id())) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_EXISTS);
            }

            Instant now = clock.instant();
            EduPlan plan = EduPlan.createDraft(
                    request.planLevel(),
                    (request.planLevel() == EduPlanLevel.SCHOOL_YEAR ? "KHN-" : "") + planCode(schoolYear, ageGroup),
                    schoolYear.id(),
                    ageGroup.id(),
                    request.title().trim(),
                    trimToEmpty(request.description()),
                    currentUser.userId(),
                    actorTitle(currentUser),
                    now);
            EduPlan curriculumSource = null;
            if (request.planLevel() == EduPlanLevel.TEAM)
                curriculumSource = requireCurriculumSource(
                        currentUser, request.curriculumSourcePlanId(), schoolYear.id(), ageGroup.id());
            else if (request.curriculumSourcePlanId() != null)
                throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
            plan.initializePlanSource(
                    request.planLevel(), request.curriculumSourcePlanId(), false, durationPolicyService.snapshot());
            if (curriculumSource != null) validateFixedSnapshot(plan, curriculumSource.getId(), request, true);
            plan.initializeSave(edit.requestId());
            try {
                plan = yearPlanRepository.saveAndFlush(plan);
            } catch (DataIntegrityViolationException ex) {
                // Hai người cùng tạo cho một nhóm tuổi: bản thứ hai vấp unique (school_year_id,
                // age_group_id)
                throw GlobalException.withCause(ApiCode.YEAR_PLAN_EXISTS, ex);
            }
            eduWorkflowService.recordAuthor(plan.getId(), null, currentUser.userId(), "CREATOR", now);
            if (curriculumSource == null) {
                saveContent(plan, request);
                saveTemplates(plan, request);
            } else copyContent(curriculumSource.getId(), plan.getId());

            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse updateYearPlan(
            CurrentUser currentUser, Long planId, YearPlanRequest request, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|updateYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadPlanForUpdate(currentUser, planId);
            Instant now = clock.instant();
            requireEditable(currentUser, plan, now);
            requireSession(currentUser, plan, edit, now);
            if (edit.requestId().equals(plan.getLastSaveRequestId())) {
                log.info("{}|END", logPrefix);
                return toDetail(currentUser, plan);
            }
            requireRevision(plan, edit);
            if (request.planLevel() != plan.getPlanLevel()
                    || (request.curriculumSourcePlanId() != null
                            && !Objects.equals(request.curriculumSourcePlanId(), plan.getCurriculumSourcePlanId())))
                throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
            plan.updateInfo(request.title().trim(), trimToEmpty(request.description()), now);
            saveContent(plan, request);
            teamService.validateMappings(plan, false);
            saveTemplates(plan, request);
            eduWorkflowService.recordAuthor(plan.getId(), null, currentUser.userId(), "CONTENT_WRITE", now);
            plan.recordSave(edit.requestId());

            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse resolveReview(
            CurrentUser user, Long planId, Long itemId, Long expectedSourcePlanId, YearPlanEditRequest edit) {
        String prefix = LOG_PREFIX + "|resolveReview|planId=" + planId;
        log.info("{}|START", prefix);
        try {
            EduPlan plan = loadPlanForUpdate(user, planId);
            Instant now = clock.instant();
            requireEditable(user, plan, now);
            requireSession(user, plan, edit, now);
            requireRevision(plan, edit);
            if (plan.getPlanLevel() != EduPlanLevel.TEAM
                    || !Objects.equals(expectedSourcePlanId, plan.getCurriculumSourcePlanId()))
                throw GlobalException.of(ApiCode.YEAR_PLAN_REVISION_STALE);
            teamService.resolve(plan, itemId, user.userId());
            if (!Objects.equals(plan.getLastSaveRequestId(), edit.requestId())) plan.recordSave(edit.requestId());
            log.info("{}|END", prefix);
            return toDetail(user, plan);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", prefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse submitYearPlan(
            CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|submitYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadPlanForUpdate(currentUser, planId);
            Instant now = clock.instant();
            if (!isEditor(currentUser, plan)) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_SUBMIT_FORBIDDEN);
            }
            if (plan.publicStatus() != EduPlanStatus.DRAFT) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_INVALID_STATUS, "gửi duyệt");
            }
            requireSession(currentUser, plan, edit, now);
            requireRevision(plan, edit);
            // Spec 004 (Q3b, Q4, Q5): từ ngày bắt đầu năm học không gửi bản chưa ban hành nào, tới khi HT mở khóa
            if (timeLock.locked(plan)) throw GlobalException.of(ApiCode.PLAN_TIME_LOCKED);
            requireReadyToIssue(plan);

            plan.submit(currentUser.userId(), now);
            plan.recordSave(null);
            yearPlanRepository.save(plan);
            recordEvent(
                    plan,
                    ACTION_SUBMIT,
                    "Gửi duyệt kế hoạch",
                    currentUser,
                    roleLabel(currentUser),
                    TONE_NONE,
                    note,
                    now);
            notifyReviewers(plan, currentUser, com.carenest.utils.enums.NotificationType.EDU_SUBMITTED, note);

            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse unlockYearPlan(
            CurrentUser currentUser, Long planId, String note, LocalDate deadline) {
        String logPrefix = LOG_PREFIX + "|unlockYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadPlanForUpdate(currentUser, planId);
            if (!accessPolicy.isPrincipal(currentUser)) throw GlobalException.of(ApiCode.FORBIDDEN);
            String reason = note == null ? "" : note.trim();
            if (reason.isEmpty()) throw GlobalException.of(ApiCode.RETURN_REASON_REQUIRED);
            LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
            if (deadline == null || !deadline.isAfter(today))
                throw GlobalException.of(ApiCode.INVALID_VALUE, "hạn mới");
            if (!timeLock.locked(plan)) throw GlobalException.of(ApiCode.INVALID_VALUE, "chỉ mở khóa khi đã khóa");
            if (timeLock.released(plan))
                throw GlobalException.of(ApiCode.INVALID_VALUE, "kế hoạch đã ban hành không mở khóa");
            Instant now = clock.instant();
            EduPlanEvent event = EduPlanEvent.of(
                            plan.getId(),
                            EduPlanTimeLock.ACTION_UNLOCK,
                            "Mở khóa tới "
                                    + deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            currentUser.userId(),
                            currentUser.fullName(),
                            roleLabel(currentUser),
                            TONE_NONE,
                            reason,
                            now)
                    .withDeadline(deadline);
            event.initializeOwner(plan);
            eventRepository.save(event);
            Long owner = plan.getOwnerId();
            if (owner != null && !owner.equals(currentUser.userId()))
                notificationService.notifyUsers(
                        Set.of(owner),
                        com.carenest.utils.enums.NotificationType.EDU_DEADLINE_EXTENDED,
                        "Đã mở khóa: " + planLabel(plan),
                        currentUser.fullName() + " mở khóa tới "
                                + deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                + ". Lý do: " + reason,
                        planLink(plan),
                        false);
            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse issueYearPlan(
            CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|issueYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadPlanForUpdate(currentUser, planId);
            if (edit != null && edit.requestId() != null && edit.requestId().equals(plan.getLastSaveRequestId())) {
                log.info("{}|END", logPrefix);
                return toDetail(currentUser, plan);
            }
            Instant now = clock.instant();
            if (plan.publicStatus() != EduPlanStatus.REVIEW) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_INVALID_STATUS, "ban hành");
            }
            requireRevision(plan, edit);

            EduWorkflowPolicy.RolePolicy policy = eduWorkflowService.loadPolicy(plan.workflowKey());
            LocalDate today = LocalDate.ofInstant(now, clock.getZone());
            EduWorkflowPolicy.Evaluation eval = eduWorkflowService.evaluateYearPlan(plan, today);
            if (eval.waitingRole() == null) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_INVALID_STATUS, "ban hành");
            }
            if (eduWorkflowService.getAuthorUserIdsForYearPlan(plan.getId()).contains(currentUser.userId())) {
                throw GlobalException.of(ApiCode.NOT_YOUR_REVIEW_STEP, "duyệt");
            }
            if (!isRoleMatch(currentUser, eval.waitingRole(), plan.getSchoolYearId(), plan.getAgeGroupId())) {
                if (eduWorkflowService.hasReviewerPrecedence(
                        currentUser, plan.workflowKey(), plan.getAgeGroupId(), null)) {
                    throw GlobalException.of(ApiCode.NOT_YOUR_REVIEW_STEP, "duyệt");
                }
                throw GlobalException.of(ApiCode.FORBIDDEN);
            }

            List<EduWorkflowPolicy.Approval> approvals =
                    new ArrayList<>(eduWorkflowService.parseApprovals(plan.getApprovals()));
            approvals.add(new EduWorkflowPolicy.Approval(
                    currentUser.userId(),
                    currentUser.fullName(),
                    eval.waitingRole(),
                    now,
                    note,
                    plan.getReviewRound()));
            String updatedApprovalsJson = eduWorkflowService.serializeApprovals(approvals);

            Set<Long> excludedAuthors = eduWorkflowService.getAuthorUserIdsForYearPlan(plan.getId());
            Map<EduWorkflowPolicy.Role, Set<Long>> candidates =
                    eduWorkflowService.resolveActiveCandidates(policy, plan.getAgeGroupId(), null, today);
            EduWorkflowPolicy.Evaluation nextEval =
                    evaluator.evaluate(policy, plan.getReviewRound(), approvals, excludedAuthors, candidates);

            if (nextEval.complete()) {
                requireReadyToIssue(plan);
                long rev = settingsRepository
                        .findById((short) 1)
                        .map(EduSettings::getWorkflowRevision)
                        .orElse(1L);
                String snapshotJson = eduWorkflowService.buildPrintSnapshot(
                        plan.workflowKey(), rev, approvals, policy, now, excludedAuthors);
                String policyJson;
                try {
                    policyJson = objectMapper.writeValueAsString(policy);
                } catch (Exception ex) {
                    throw new IllegalStateException("Cannot serialize publication policy", ex);
                }
                Long legacyVpUserId = approvals.stream()
                        .filter(a -> a.roleKey() == EduWorkflowPolicy.Role.VICE_PRINCIPAL)
                        .map(EduWorkflowPolicy.Approval::userId)
                        .reduce((first, second) -> second)
                        .orElse(null);
                Instant legacyVpApprovedAt = approvals.stream()
                        .filter(a -> a.roleKey() == EduWorkflowPolicy.Role.VICE_PRINCIPAL)
                        .map(EduWorkflowPolicy.Approval::at)
                        .reduce((first, second) -> second)
                        .orElse(null);
                plan.publish(
                        approvals.stream()
                                .filter(a -> a.round() == plan.getReviewRound()
                                        && a.roleKey() == nextEval.completionRole()
                                        && !excludedAuthors.contains(a.userId()))
                                .map(EduWorkflowPolicy.Approval::userId)
                                .filter(java.util.Objects::nonNull)
                                .findFirst()
                                .orElseThrow(),
                        legacyVpUserId,
                        legacyVpApprovedAt,
                        updatedApprovalsJson,
                        rev,
                        policyJson,
                        snapshotJson,
                        now);
                yearPlanRepository.save(plan);
                teamUpdates.updateAfterPublication(plan, now);
                recordEvent(plan, ACTION_ISSUE, "Ban hành", currentUser, roleLabel(currentUser), TONE_OK, note, now);
                notifyAuthor(
                        plan,
                        currentUser,
                        com.carenest.utils.enums.NotificationType.EDU_RELEASED,
                        "Đã ban hành: ",
                        " đã duyệt ban hành",
                        note);
            } else {
                plan.advanceReview(updatedApprovalsJson, now);
                yearPlanRepository.save(plan);
                recordEvent(
                        plan,
                        ACTION_ISSUE,
                        "Duyệt bước " + eval.waitingRole(),
                        currentUser,
                        roleLabel(currentUser),
                        TONE_OK,
                        note,
                        now);
                notifyReviewers(plan, currentUser, com.carenest.utils.enums.NotificationType.EDU_STEP_APPROVED, note);
            }

            plan.recordSave(edit.requestId());
            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse returnYearPlan(
            CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|returnYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            // Thử lại cùng requestId: kế hoạch đã về Nháp nên người duyệt không còn quyền xem (EDU-73) — nhận ra lần
            // thử
            // lại trước khi kiểm quyền xem, trả kết quả như lần đầu
            EduPlan plan = loadPlanForUpdate(currentUser, planId, false);
            if (edit != null && edit.requestId() != null && edit.requestId().equals(plan.getLastSaveRequestId())) {
                log.info("{}|END", logPrefix);
                return toDetail(currentUser, plan);
            }
            requireViewable(currentUser, plan);
            if (plan.publicStatus() != EduPlanStatus.REVIEW) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_INVALID_STATUS, "trả lại");
            }
            if (note == null || note.isBlank()) {
                throw GlobalException.of(ApiCode.RETURN_REASON_REQUIRED);
            }
            if (!isUserWaitingRole(currentUser, plan)) {
                throw GlobalException.of(ApiCode.NOT_YOUR_REVIEW_STEP, "trả lại");
            }
            if (eduWorkflowService.getAuthorUserIdsForYearPlan(plan.getId()).contains(currentUser.userId())) {
                throw GlobalException.of(ApiCode.NOT_YOUR_REVIEW_STEP, "trả lại");
            }

            Instant now = clock.instant();
            requireRevision(plan, edit);
            plan.returnToDraft(now);
            plan.recordSave(edit.requestId());
            yearPlanRepository.save(plan);
            recordEvent(
                    plan, ACTION_RETURN, "Trả lại để sửa", currentUser, roleLabel(currentUser), TONE_ERR, note, now);
            notifyAuthor(
                    plan,
                    currentUser,
                    com.carenest.utils.enums.NotificationType.EDU_RETURNED,
                    "Bị trả lại: ",
                    " trả lại. Lý do",
                    note);

            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void deleteYearPlan(CurrentUser currentUser, Long planId, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|deleteYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadPlanForUpdate(currentUser, planId);
            // EDU-07: chỉ Phó HT xóa, và chỉ khi Nháp (kể cả đang do chính mình sửa)
            if (plan.getPlanLevel() == EduPlanLevel.TEAM || !canDeleteAsVicePrincipal(currentUser)) {
                throw GlobalException.of(ApiCode.VICE_PRINCIPAL_ONLY, "xóa");
            }
            if (plan.publicStatus() != EduPlanStatus.DRAFT) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_DELETE_DRAFT_ONLY);
            }
            requireEditContext(edit);
            requireRevision(plan, edit);
            // BR-10, BR-17, FR-002: Tuyệt đối cấm xóa cứng (no hard delete), chuyển sang Soft Delete (disabled = true)
            plan.markDisabled();
            plan.touch(clock.instant());
            yearPlanRepository.save((com.carenest.entity.eduplan.EduYearPlan) plan);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse startEditing(CurrentUser currentUser, Long planId, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|startEditing|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadPlanForUpdate(currentUser, planId);
            Instant now = clock.instant();
            requireEditable(currentUser, plan, now);
            requireEditContext(edit);
            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|status={}|END", logPrefix, plan.getStatus());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse finishEditing(CurrentUser currentUser, Long planId, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|finishEditing|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadPlanForUpdate(currentUser, planId);
            // Không giữ khóa thì bỏ qua: trang soạn gọi khi thoát, kể cả lúc khóa đã hết
            // hạn
            requireEditContext(edit);
            requireDesignatedAuthor(currentUser, plan, edit);
            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|status={}|END", logPrefix, plan.getStatus());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public int releaseExpiredEditLocks() {
        String logPrefix = LOG_PREFIX + "|releaseExpiredEditLocks";
        log.info("{}|START", logPrefix);
        try {
            log.info("{}|count=0|END", logPrefix);
            return 0;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse heartbeat(CurrentUser currentUser, Long planId, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|heartbeat|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadPlanForUpdate(currentUser, planId);
            Instant now = clock.instant();
            requireEditable(currentUser, plan, now);
            requireSession(currentUser, plan, edit, now);
            log.info("{}|END", logPrefix);
            return toDetail(currentUser, plan);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse createVersion(CurrentUser user, Long planId, YearPlanEditRequest edit) {
        return createVersion(user, planId, edit, null);
    }

    @Override
    @Transactional
    public YearPlanDetailResponse createVersion(
            CurrentUser currentUser, Long planId, YearPlanEditRequest edit, Long curriculumSourcePlanId) {
        String logPrefix = LOG_PREFIX + "|createVersion|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan source = loadPlanForUpdate(currentUser, planId);
            if (source.getPlanLevel() == EduPlanLevel.TEAM)
                throw GlobalException.of(ApiCode.YEAR_PLAN_VERSION_COMPARE_INVALID);
            // EDU-25: người được sửa kế hoạch (PHT, TT nhóm tuổi) tạo phiên bản
            if (!eduWorkflowService.isAuthorEligible(
                    currentUser, source.workflowKey(), source.getSchoolYearId(), source.getAgeGroupId(), null)) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_CREATE_FORBIDDEN);
            }
            requireEditContext(edit);
            Optional<EduPlan> retried =
                    yearPlanRepository.findByOwnerIdAndCreatedRequestId(currentUser.userId(), edit.requestId());
            if (retried.isPresent()) {
                if (!planId.equals(retried.get().getSourcePlanId())) {
                    throw GlobalException.of(ApiCode.YEAR_PLAN_REQUEST_REUSED);
                }
                log.info("{}|END", logPrefix);
                return toDetail(currentUser, retried.get());
            }
            List<EduPlan> chain = yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                    source.getPlanLevel(), source.getSchoolYearId(), source.getAgeGroupId());
            Optional<EduPlan> open = chain.stream()
                    .filter(p -> p.getStatus() != EduPlanStatus.APPROVED)
                    .findFirst();
            if (open.isPresent()) {
                throw GlobalException.of(
                        ApiCode.YEAR_PLAN_OPEN_VERSION_EXISTS, open.get().getCode());
            }
            // EDU-25: chỉ chép từ phiên bản ban hành mới nhất; bản đã ban hành giữ nguyên
            if (!currentVersion(chain).map(p -> p.getId().equals(planId)).orElse(false)) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_VERSION_SOURCE_INVALID);
            }
            // Spec 004 (Q3b): phiên bản điều chỉnh chỉ tạo trước ngày bắt đầu năm học, trừ khi HT mở khóa
            if (timeLock.locked(source)) throw GlobalException.of(ApiCode.PLAN_TIME_LOCKED);
            requireRevision(source, edit);

            Instant now = clock.instant();
            int versionNo = source.getVersionNo() + 1;
            EduPlan plan = EduPlan.createVersion(
                    source,
                    chain.getFirst().getCode() + "-V" + versionNo,
                    currentUser.userId(),
                    actorTitle(currentUser),
                    now);
            EduPlan nextCurriculum = curriculumSourcePlanId == null
                    ? null
                    : requireCurriculumSource(
                            currentUser, curriculumSourcePlanId, source.getSchoolYearId(), source.getAgeGroupId());
            if (nextCurriculum != null && source.getPlanLevel() != EduPlanLevel.TEAM)
                throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
            plan.initializePlanSource(
                    source.getPlanLevel(),
                    nextCurriculum == null ? source.getCurriculumSourcePlanId() : nextCurriculum.getId(),
                    nextCurriculum == null && source.isLegacyTeamWithoutSource(),
                    durationPolicyService.snapshot());
            plan.initializeSave(edit.requestId());
            try {
                plan = yearPlanRepository.saveAndFlush(plan);
            } catch (DataIntegrityViolationException ex) {
                // Hai người cùng tạo: bản sau vấp unique phiên bản mở / số phiên bản
                throw GlobalException.withCause(ApiCode.YEAR_PLAN_OPEN_VERSION_EXISTS, ex, "khác");
            }
            eduWorkflowService.recordAuthor(plan.getId(), null, currentUser.userId(), "CREATOR", now);
            copyContent(nextCurriculum == null ? source.getId() : nextCurriculum.getId(), plan.getId());
            recordEvent(
                    plan,
                    ACTION_CREATE_VERSION,
                    "Tạo phiên bản " + versionNo + " từ phiên bản " + source.getVersionNo(),
                    currentUser,
                    actorTitle(currentUser),
                    TONE_NONE,
                    null,
                    now);

            YearPlanDetailResponse response = toDetail(currentUser, plan);
            log.info("{}|newPlanId={}|END", logPrefix, plan.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public YearPlanChangesResponse getChanges(CurrentUser currentUser, Long planId, Long fromPlanId) {
        String logPrefix = LOG_PREFIX + "|getChanges|userId=" + currentUser.userId() + "|planId=" + planId
                + "|fromPlanId=" + fromPlanId;
        log.info("{}|START", logPrefix);
        try {
            EduPlan plan = loadViewablePlan(currentUser, planId);
            if (plan.getPlanLevel() == EduPlanLevel.TEAM)
                throw GlobalException.of(ApiCode.YEAR_PLAN_VERSION_COMPARE_INVALID);
            Long fromId = fromPlanId != null ? fromPlanId : plan.getSourcePlanId();
            if (fromId == null) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_VERSION_COMPARE_INVALID);
            }
            YearPlanChangesResponse response = compare(loadViewablePlan(currentUser, fromId), plan);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public YearPlanChangesResponse compareVersions(Long fromPlanId, Long planId) {
        String logPrefix = LOG_PREFIX + "|compareVersions|planId=" + planId + "|fromPlanId=" + fromPlanId;
        log.info("{}|START", logPrefix);
        try {
            YearPlanChangesResponse response = compare(requirePlan(fromPlanId), requirePlan(planId));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private EduPlan requireCurriculumSource(CurrentUser user, Long sourceId, Long yearId, Long ageId) {
        if (sourceId == null) throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
        EduPlan source = loadPlanForUpdate(user, sourceId);
        if (source.getPlanLevel() != EduPlanLevel.SCHOOL_YEAR
                || source.publicStatus() != EduPlanStatus.APPROVED
                || !source.getSchoolYearId().equals(yearId)
                || !source.getAgeGroupId().equals(ageId)) throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
        if (!currentVersion(yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                        EduPlanLevel.SCHOOL_YEAR, yearId, ageId))
                .map(p -> p.getId().equals(sourceId))
                .orElse(false)) throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
        return source;
    }

    private void saveTemplates(EduPlan plan, YearPlanRequest request) {
        Map<Long, EduPlanBranchTemplate> existing =
                templateRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()).stream()
                        .collect(Collectors.toMap(EduPlanBranchTemplate::getId, Function.identity()));
        Map<String, Long> themes = new HashMap<>();
        List<EduTheme> savedThemes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
        List<YearPlanRequest.Theme> requested = nullToEmpty(request.themes());
        for (int i = 0; i < requested.size(); i++)
            themes.put(requested.get(i).key(), savedThemes.get(i).getId());
        Set<Long> kept = new HashSet<>();
        int order = 0;
        for (YearPlanRequest.BranchTemplate t : request.branchTemplates()) {
            Long themeId = themes.get(t.themeKey());
            if (themeId == null || (t.id() != null && !existing.containsKey(t.id())))
                throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
            if (t.startDate() != null || t.endDate() != null) {
                EduTheme parent = savedThemes.stream()
                        .filter(theme -> theme.getId().equals(themeId))
                        .findFirst()
                        .orElseThrow();
                if (parent.getStartDate() == null
                        || parent.getEndDate() == null
                        || t.startDate() != null
                                && (t.startDate().isBefore(parent.getStartDate())
                                        || t.startDate().isAfter(parent.getEndDate()))
                        || t.endDate() != null
                                && (t.endDate().isBefore(parent.getStartDate())
                                        || t.endDate().isAfter(parent.getEndDate()))
                        || t.startDate() != null
                                && t.endDate() != null
                                && t.endDate().isBefore(t.startDate()))
                    throw GlobalException.of(
                            ApiCode.EDUCATION_CALENDAR_INVALID, "ngày nhánh phải nằm trong chủ đề cha");
            }
            EduPlanBranchTemplate template = t.id() == null
                    ? EduPlanBranchTemplate.create(
                            plan.getId(), themeId, null, null, trimToEmpty(t.name()), t.startDate(), t.endDate(), order)
                    : existing.get(t.id());
            template.update(themeId, trimToEmpty(t.name()), t.startDate(), t.endDate(), order++);
            template.initializeOwner(plan);
            kept.add(templateRepository.save(template).getId());
        }
        List<EduPlanBranchTemplate> removed = existing.values().stream()
                .filter(t -> !kept.contains(t.getId()))
                .toList();
        // Kế hoạch tổ chỉ xóa được nhánh do tổ thêm; nhánh kế thừa từ kế hoạch năm chỉ sửa tên/ngày (FR-013)
        if (plan.getPlanLevel() == EduPlanLevel.TEAM && removed.stream().anyMatch(t -> t.getSourceTemplateId() != null))
            throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
        templateRepository.deleteAllInBatch(removed);
    }

    private EduPlan requirePlan(Long planId) {
        return yearPlanRepository.findById(planId).orElseThrow(() -> GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
    }

    private void requireEditContext(YearPlanEditRequest edit) {
        if (edit == null || edit.revision() == null || edit.requestId() == null || edit.revision() < 0) {
            throw GlobalException.of(ApiCode.EDIT_SESSION_REQUIRED);
        }
    }

    private void requireSession(CurrentUser user, EduPlan plan, YearPlanEditRequest edit, Instant now) {
        requireDesignatedAuthor(user, plan, edit);
    }

    static void requireDesignatedAuthor(CurrentUser user, EduPlan plan, YearPlanEditRequest edit) {
        if (edit == null || edit.revision() == null || edit.revision() < 0 || edit.requestId() == null) {
            throw GlobalException.of(ApiCode.EDIT_SESSION_REQUIRED);
        }
        if (user == null || !Objects.equals(user.userId(), plan.getOwnerId())) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN);
        }
    }

    private void requireRevision(EduPlan plan, YearPlanEditRequest edit) {
        if (edit.revision() != plan.getContentRevision()) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_REVISION_STALE);
        }
    }

    private void requirePermission(CurrentUser user, String permission) {
        if (!accessPolicy.hasPermission(user, permission)) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_ACCESS_DENIED);
        }
    }

    // EDU-06: không tồn tại ⇒ 404; ngoài phạm vi xem ⇒ 403
    private EduPlan loadViewablePlan(CurrentUser user, Long planId) {
        requirePermission(user, EduPermission.GOALS_VIEW);
        EduPlan plan =
                yearPlanRepository.findById(planId).orElseThrow(() -> GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
        requireViewable(user, plan);
        return plan;
    }

    private void requireViewable(CurrentUser user, EduPlan plan) {
        if (canView(user, plan)) return;
        // Trong phạm vi xem nhưng bản chưa ban hành ⇒ báo rõ thay vì "không có quyền"
        if (listScope(user, plan.getPlanLevel(), plan.getSchoolYearId())
                .allows(plan.getAgeGroupId(), EduPlanStatus.APPROVED)) {
            throw GlobalException.of(ApiCode.EDU_PLAN_NOT_RELEASED);
        }
        throw GlobalException.of(ApiCode.YEAR_PLAN_ACCESS_DENIED);
    }

    /**
     * Người phụ trách thấy mọi trạng thái; người duyệt của bước đang chờ thấy bản Chờ duyệt; người khác trong phạm vi
     * chỉ thấy bản đã ban hành (spec 002 FR-007).
     */
    private boolean canView(CurrentUser user, EduPlan plan) {
        if (isAuthor(user, plan)) return true;
        EduPlanStatus status = plan.publicStatus();
        // Spec 005 FR-021a: HT xem (chỉ đọc) và mở hạn bản Nháp quá hạn hoặc đã được mở hạn, chưa từng ban hành
        if (status == EduPlanStatus.DRAFT && accessPolicy.isPrincipal(user))
            return (timeLock.locked(plan) || timeLock.info(plan).extendedTo() != null) && !timeLock.released(plan);
        if (status == EduPlanStatus.APPROVED)
            return listScope(user, plan.getPlanLevel(), plan.getSchoolYearId())
                    .allows(plan.getAgeGroupId(), EduPlanStatus.APPROVED);
        return status == EduPlanStatus.REVIEW && isCurrentApprover(user, plan);
    }

    // Người phụ trách: đủ điều kiện soạn theo cấu hình (với kế hoạch tổ là Tổ trưởng hiện hành); không xét trạng thái
    private boolean isAuthor(CurrentUser user, EduPlan plan) {
        return eduWorkflowService.isAuthorEligible(
                user, plan.workflowKey(), plan.getSchoolYearId(), plan.getAgeGroupId(), null);
    }

    private boolean isCurrentApprover(CurrentUser user, EduPlan plan) {
        return plan.publicStatus() == EduPlanStatus.REVIEW
                && !eduWorkflowService.getAuthorUserIdsForYearPlan(plan.getId()).contains(user.userId())
                && isUserWaitingRole(user, plan);
    }

    /** Danh sách: người phụ trách thấy mọi trạng thái nhóm tuổi mình; người khác chỉ bản đã ban hành. */
    private EduPlanAccessPolicy.ViewScope listScope(CurrentUser user, EduPlanLevel level, Long schoolYearId) {
        if (!accessPolicy.hasPermission(user, EduPermission.GOALS_VIEW))
            return new EduPlanAccessPolicy.ViewScope(Set.of(), Set.of(), Set.of());
        var policy = eduWorkflowService.loadPolicy(level.workflowKey());
        Set<EduPlanStatus> schoolWide = accessPolicy.isVicePrincipal(user) || accessPolicy.isPrincipal(user)
                ? Set.of(EduPlanStatus.APPROVED)
                : Set.of();
        Set<Long> full = new HashSet<>();
        Set<Long> approved = new HashSet<>();
        for (var age : schoolStructureService.findAgeGroups()) {
            if (accessPolicy.canAuthorYearPlan(user, policy, schoolYearId, age.id())) full.add(age.id());
            // GV (chủ nhiệm hoặc GV thứ hai) và Tổ trưởng xem bản ban hành của nhóm tuổi mình, gồm kế hoạch năm
            if (accessPolicy.isTeacherOfAgeGroup(user, age.id())
                    || accessPolicy.isTeamLeaderOf(user, schoolYearId, age.id())) approved.add(age.id());
        }
        return new EduPlanAccessPolicy.ViewScope(schoolWide, Set.copyOf(full), Set.copyOf(approved));
    }

    private EduPlan loadPlanForUpdate(CurrentUser user, Long planId) {
        return loadPlanForUpdate(user, planId, true);
    }

    private EduPlan loadPlanForUpdate(CurrentUser user, Long planId, boolean checkViewable) {
        settingsRepository.findForUpdate().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
        requirePermission(user, EduPermission.GOALS_VIEW);
        EduPlan plan = yearPlanRepository
                .findForUpdateById(planId)
                .orElseThrow(() -> GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
        if (checkViewable) requireViewable(user, plan);
        return plan;
    }

    private void requireEditable(CurrentUser user, EduPlan plan, Instant now) {
        if (plan.publicStatus() != EduPlanStatus.DRAFT) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_INVALID_STATUS, "chỉnh sửa");
        }
        if (!isEditor(user, plan)) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN);
        }
    }

    // Kế hoạch tổ: Tổ trưởng hiện hành sửa được dù không phải người tạo bản (FR-016a); kế hoạch năm giữ người soạn
    private boolean isEditor(CurrentUser user, EduPlan plan) {
        return isAuthor(user, plan) && (plan.getPlanLevel() == EduPlanLevel.TEAM || isOwner(user, plan));
    }

    private boolean canDeleteAsVicePrincipal(CurrentUser user) {
        return accessPolicy.isVicePrincipal(user) && accessPolicy.hasPermission(user, EduPermission.GOALS_EDIT);
    }

    private boolean isOwner(CurrentUser user, EduPlan plan) {
        return plan.getOwnerId().equals(user.userId());
    }

    // Nháp / bị trả lại, kể cả khi đang có người sửa
    private static boolean isDraftLike(EduPlan plan) {
        return plan.publicStatus() == EduPlanStatus.DRAFT;
    }

    private boolean canEdit(CurrentUser user, EduPlan plan, Instant now) {
        if (plan.publicStatus() != EduPlanStatus.DRAFT) {
            return false;
        }
        return isEditor(user, plan);
    }

    // Điều kiện của bước "Chủ đề và sự kiện" + "Mục tiêu" trên UI, kiểm khi gửi /
    // ban hành (EDU-04, EDU-05)
    private void requireReadyForReview(EduPlan plan) {
        teamService.validateMappings(plan, true);
        if (plan.getTitle().isBlank()) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_TITLE_REQUIRED);
        }
        SchoolYearResponse year = schoolStructureService
                .findSchoolYear(plan.getSchoolYearId())
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
        List<EduBreak> breaks = breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
        for (EduBreak item : breaks) {
            if (item.getStartDate() != null
                    && item.getEndDate() != null
                    && item.getEndDate().isBefore(item.getStartDate())) {
                throw GlobalException.of(ApiCode.BREAK_END_BEFORE_START);
            }
        }
        List<YearPlanRequest.Theme> dateThemes =
                themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()).stream()
                        .map(t -> new YearPlanRequest.Theme(
                                "date", t.getId(), t.getName(), t.getAbbr(), t.getStartDate(), t.getEndDate(), "", ""))
                        .toList();
        requireDatesInSchoolYear(
                year,
                new YearPlanRequest(
                        null,
                        null,
                        plan.getTitle(),
                        "",
                        List.of(),
                        dateThemes,
                        breaks.stream()
                                .map(b -> new YearPlanRequest.Break(
                                        b.getId(), b.getLabel(), b.getStartDate(), b.getEndDate()))
                                .toList()));
        List<EduTheme> themes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
        Set<String> abbrs = new HashSet<>();
        for (EduTheme theme : themes) {
            if (theme.getName().isBlank() || theme.getAbbr().isBlank()) {
                throw GlobalException.of(ApiCode.THEME_NAME_REQUIRED);
            }
            if (!abbrs.add(theme.getAbbr().trim().toLowerCase(Locale.ROOT))) {
                throw GlobalException.of(ApiCode.THEME_ABBR_DUPLICATE);
            }
            if (theme.getStartDate() != null
                    && theme.getEndDate() != null
                    && theme.getEndDate().isBefore(theme.getStartDate())) {
                throw GlobalException.of(ApiCode.THEME_END_BEFORE_START);
            }
        }
        if (plan.getPlanLevel() == EduPlanLevel.SCHOOL_YEAR) {
            YearPlanPublicationValidator.requireMinimumThemes(themes.size());
            publicationValidator.validateSubmissionSchedule(plan);
        }
        requireThemeWeeks(themes, breaks);
        publicationValidator.validateBranchDates(plan.getId());
        if (findRows(plan.getId()).stream()
                .anyMatch(row ->
                        row.getKind() == EduGoalRowKind.GOAL && row.getText().isBlank())) {
            throw GlobalException.of(ApiCode.GOAL_CONTENT_REQUIRED);
        }
        List<EduGoalRow> rows = findRows(plan.getId());
        if (plan.getPlanLevel() == EduPlanLevel.SCHOOL_YEAR) {
            List<Long> goalIds = rows.stream()
                    .filter(r -> r.getKind() == EduGoalRowKind.GOAL)
                    .map(EduGoalRow::getId)
                    .toList();
            YearPlanPublicationValidator.requireAnnualContents(
                    rows,
                    goalIds.isEmpty()
                            ? List.of()
                            : goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(goalIds));
        }
        boolean hasGoal = rows.stream().anyMatch(row -> row.getKind() == EduGoalRowKind.GOAL);
        if (!hasGoal) {
            throw GlobalException.of(ApiCode.PLAN_GOAL_REQUIRED);
        }
        requireUniqueNames(rows);
        requireAllOrNoSubDomains(rows);
    }

    // EDU-53: một lĩnh vực hoặc không có lĩnh vực con nào, hoặc mọi nhóm kết quả /
    // mục tiêu đều thuộc một lĩnh vực con
    private static void requireAllOrNoSubDomains(List<EduGoalRow> rows) {
        Set<Long> domainsWithSub = rows.stream()
                .filter(row -> row.getKind() == EduGoalRowKind.SUB)
                .map(EduGoalRow::getDomainId)
                .collect(Collectors.toSet());
        rows.stream()
                .filter(row -> row.getKind() != EduGoalRowKind.SUB
                        && row.getSubDomainId() == null
                        && domainsWithSub.contains(row.getDomainId()))
                .findFirst()
                .ifPresent(row -> {
                    throw GlobalException.of(
                            ApiCode.GOAL_SUB_DOMAIN_MIXED, row.getText().trim());
                });
    }

    // EDU-51: không trùng tên lĩnh vực con trong một lĩnh vực; trong cùng lĩnh vực
    // con không trùng nhóm kết quả
    // mong đợi, không trùng mục tiêu; trong một mục tiêu không trùng nội dung (bỏ
    // khoảng trắng thừa, không phân biệt
    // hoa thường)
    private void requireUniqueNames(List<EduGoalRow> rows) {
        Set<Long> goalIds = rows.stream()
                .filter(row -> row.getKind() == EduGoalRowKind.GOAL)
                .map(EduGoalRow::getId)
                .collect(Collectors.toSet());
        Map<Long, List<EduGoalContent>> contentsByGoal = goalIds.isEmpty()
                ? Map.of()
                : goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(goalIds).stream()
                        .collect(Collectors.groupingBy(EduGoalContent::getGoalRowId));
        Set<String> names = new HashSet<>();
        for (EduGoalRow row : rows) {
            String scope = row.getKind() == EduGoalRowKind.SUB
                    ? row.getKind() + "|" + row.getDomainId()
                    : row.getKind() + "|" + row.getDomainId() + "|" + row.getSubDomainId();
            if (!names.add(scope + "|" + nameKey(row.getText()))) {
                throw GlobalException.of(
                        ApiCode.GOAL_NAME_DUPLICATE, row.getText().trim());
            }
            Set<String> contents = new HashSet<>();
            for (EduGoalContent content : contentsByGoal.getOrDefault(row.getId(), List.of())) {
                if (!contents.add(nameKey(content.getText()))) {
                    throw GlobalException.of(
                            ApiCode.GOAL_NAME_DUPLICATE, content.getText().trim());
                }
            }
        }
    }

    private static String nameKey(String text) {
        return trimToEmpty(text).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * EDU-52 (Tiền 10/11/2026): mọi điều kiện bắt buộc khi ban hành chặn ngay từ bước gửi duyệt — gửi và ban hành dùng
     * chung một bộ kiểm.
     */
    private void requireReadyToIssue(EduPlan plan) {
        publicationValidator.validateForPublication(plan);
        requireReadyForReview(plan);
        requireThemeDates(plan);
    }

    // EDU-52/EDU-66: gửi duyệt và ban hành cần mọi chủ đề có đủ ngày bắt đầu, kết thúc; nháp chưa bắt buộc
    private void requireThemeDates(EduPlan plan) {
        for (EduTheme theme : themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId())) {
            if (theme.getStartDate() == null || theme.getEndDate() == null) {
                throw GlobalException.of(ApiCode.THEME_DATES_REQUIRED, theme.getAbbr());
            }
        }
    }

    // EDU-40: chủ đề trọn tuần học (Thứ 2 → Thứ 6); EDU-39: các chủ đề không trùng
    // thời gian; không trống ngày học giữa các chủ đề
    static void requireThemeWeeks(List<EduTheme> themes, List<EduBreak> breaks) {
        List<EduTheme> dated = themes.stream()
                .filter(t -> t.getStartDate() != null && t.getEndDate() != null)
                .sorted(Comparator.comparing(EduTheme::getStartDate))
                .toList();
        for (EduTheme theme : dated) {
            if (theme.getStartDate().getDayOfWeek() != DayOfWeek.MONDAY
                    || theme.getEndDate().getDayOfWeek() != DayOfWeek.FRIDAY) {
                throw GlobalException.of(ApiCode.THEME_WEEK_ALIGNMENT, theme.getAbbr());
            }
        }
        for (int i = 1; i < dated.size(); i++) {
            EduTheme previous = dated.get(i - 1);
            EduTheme current = dated.get(i);
            if (!current.getStartDate().isAfter(previous.getEndDate())) {
                throw GlobalException.of(ApiCode.THEME_DATES_OVERLAP, current.getAbbr(), previous.getAbbr());
            }
        }
        requireNoThemeGaps(dated, breaks);
    }

    static void requireNoThemeGaps(List<EduTheme> dated, List<EduBreak> breaks) {
        if (dated.size() < 2) return;
        LocalDate minStart = dated.get(0).getStartDate();
        LocalDate maxEnd = dated.stream()
                .map(EduTheme::getEndDate)
                .max(Comparator.naturalOrder())
                .orElse(dated.get(0).getEndDate());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate gapStart = null;
        LocalDate gapEnd = null;

        for (LocalDate d = minStart; !d.isAfter(maxEnd); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                final LocalDate currentDay = d;
                boolean inTheme = dated.stream()
                        .anyMatch(t -> !currentDay.isBefore(t.getStartDate()) && !currentDay.isAfter(t.getEndDate()));
                boolean inBreak = breaks != null
                        && breaks.stream()
                                .anyMatch(b -> b.getStartDate() != null
                                        && !currentDay.isBefore(b.getStartDate())
                                        && !currentDay.isAfter(
                                                b.getEndDate() != null ? b.getEndDate() : b.getStartDate()));
                if (!inTheme && !inBreak) {
                    if (gapStart == null) {
                        gapStart = d;
                    }
                    gapEnd = d;
                } else if (gapStart != null) {
                    break;
                }
            }
        }

        if (gapStart != null && gapEnd != null) {
            String detail = gapStart.equals(gapEnd)
                    ? "ngày " + gapStart.format(fmt)
                    : "từ " + gapStart.format(fmt) + " đến " + gapEnd.format(fmt);
            throw GlobalException.of(ApiCode.THEME_DATES_GAP, detail);
        }
    }

    // EDU-23: ngày của chủ đề, đợt nghỉ nằm trong năm học của kế hoạch
    private void requireDatesInSchoolYear(SchoolYearResponse year, YearPlanRequest request) {
        if (year.startDate() == null || year.endDate() == null) {
            return;
        }
        Stream<LocalDate> dates = Stream.concat(
                nullToEmpty(request.themes()).stream().flatMap(t -> Stream.of(t.startDate(), t.endDate())),
                nullToEmpty(request.breaks()).stream().flatMap(b -> Stream.of(b.startDate(), b.endDate())));
        boolean outside =
                dates.filter(Objects::nonNull).anyMatch(d -> d.isBefore(year.startDate()) || d.isAfter(year.endDate()));
        if (outside) {
            throw GlobalException.of(ApiCode.THEME_DATE_OUT_OF_YEAR, year.startDate(), year.endDate());
        }
    }

    // Lưu cả cây nội dung: lưu/cập nhật trước, xóa phần bị bỏ sau để nội dung
    // chuyển dòng không bị cascade xóa
    // EDU-28: lĩnh vực con, nhóm, mục tiêu lấy từ danh mục của Bộ phải đúng nhóm
    // tuổi của kế hoạch
    private Map<EduGoalRowKind, Set<Long>> requireCurriculum(
            EduPlan plan, List<YearPlanRequest.Domain> requestDomains) {
        Map<EduGoalRowKind, Set<Long>> valid = new EnumMap<>(EduGoalRowKind.class);
        for (EduGoalRowKind kind : EduGoalRowKind.values()) {
            Set<Long> ids = requestDomains.stream()
                    .flatMap(d -> nullToEmpty(d.rows()).stream())
                    .filter(r -> kind.code().equals(r.kind()))
                    .map(r -> r.curriculumId(kind))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, CurriculumRef> refs = curriculumService.findRefs(kind, ids);
            for (Long id : ids) {
                CurriculumRef ref = refs.get(id);
                if (ref == null || !ref.ageGroupId().equals(plan.getAgeGroupId())) {
                    throw GlobalException.of(ApiCode.CURRICULUM_GOAL_INVALID);
                }
            }
            valid.put(kind, ids);
        }
        return valid;
    }

    private void validateFixedSnapshot(EduPlan plan, Long sourceId, YearPlanRequest request, boolean creating) {
        if (!creating || request.domains() != null && !request.domains().isEmpty()) {
            var rows = findRows(sourceId);
            var contents = rows.isEmpty()
                    ? Map.<Long, List<EduGoalContent>>of()
                    : goalContentRepository
                            .findAllByGoalRowIdInOrderBySortOrderAsc(
                                    rows.stream().map(EduGoalRow::getId).collect(Collectors.toSet()))
                            .stream()
                            .collect(Collectors.groupingBy(EduGoalContent::getGoalRowId));
            InheritedCurriculumGuard.validateInheritedTree(plan, request, rows, contents);
        }
        if (!creating || request.themes() != null && !request.themes().isEmpty()) {
            var expected = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId).stream()
                    .map(t -> java.util.Arrays.asList(
                            t.getId(),
                            t.getName(),
                            t.getAbbr(),
                            t.getStartDate(),
                            t.getEndDate(),
                            t.getFestivals(),
                            t.getNote()))
                    .toList();
            var actual = nullToEmpty(request.themes()).stream()
                    .map(t -> java.util.Arrays.asList(
                            t.id(),
                            trimToEmpty(t.name()),
                            trimToEmpty(t.abbr()),
                            t.startDate(),
                            t.endDate(),
                            trimToEmpty(t.festivals()),
                            trimToEmpty(t.note())))
                    .toList();
            if (!expected.equals(actual)) throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
        }
        if (!creating || request.breaks() != null && !request.breaks().isEmpty()) {
            var expected = breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId).stream()
                    .map(b -> java.util.Arrays.asList(b.getId(), b.getLabel(), b.getStartDate(), b.getEndDate()))
                    .toList();
            var actual = nullToEmpty(request.breaks()).stream()
                    .map(b -> java.util.Arrays.asList(b.id(), trimToEmpty(b.label()), b.startDate(), b.endDate()))
                    .toList();
            if (!expected.equals(actual)) throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
        }
        if (plan.getPlanLevel() != EduPlanLevel.TEAM || creating) {
            Map<String, Long> themeIds = new HashMap<>();
            nullToEmpty(request.themes()).forEach(t -> themeIds.put(t.key(), t.id()));
            var expected = templateRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId).stream()
                    .map(t -> java.util.Arrays.asList(
                            t.getId(), t.getThemeId(), t.getName(), t.getStartDate(), t.getEndDate()))
                    .toList();
            var actual = request.branchTemplates().stream()
                    .map(t -> java.util.Arrays.asList(
                            t.id(), themeIds.get(t.themeKey()), trimToEmpty(t.name()), t.startDate(), t.endDate()))
                    .toList();
            if (!expected.equals(actual)) throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
        }
    }

    private void saveContent(EduPlan plan, YearPlanRequest request) {
        Long planId = plan.getId();
        List<EduGoalRow> existingRowList = findRows(planId);
        if (plan.getCurriculumSourcePlanId() != null) validateFixedSnapshot(plan, planId, request, false);

        Map<String, Long> themeIdByKey = saveThemes(planId, nullToEmpty(request.themes()));
        saveBreaks(planId, nullToEmpty(request.breaks()));

        Map<Long, EduDomain> domains =
                domainRepository.findAll().stream().collect(Collectors.toMap(EduDomain::getId, Function.identity()));
        Map<Long, EduGoalRow> existingRows =
                existingRowList.stream().collect(Collectors.toMap(EduGoalRow::getId, Function.identity()));
        Map<Long, EduGoalContent> existingContents = existingRows.isEmpty()
                ? Map.of()
                : goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(existingRows.keySet()).stream()
                        .collect(Collectors.toMap(EduGoalContent::getId, Function.identity()));

        List<YearPlanRequest.Domain> requestDomains = new ArrayList<>(nullToEmpty(request.domains()));
        Map<EduGoalRowKind, Set<Long>> curriculum = requireCurriculum(plan, requestDomains);
        Set<Long> requestedDomainIds = new HashSet<>();
        Set<Long> requestedRowIds = new HashSet<>();
        Set<Long> requestedContentIds = new HashSet<>();
        for (YearPlanRequest.Domain domain : requestDomains) {
            if (!requestedDomainIds.add(domain.domainId())) {
                throw GlobalException.of(ApiCode.INVALID_VALUE, "lĩnh vực trùng");
            }
            for (YearPlanRequest.Row row : nullToEmpty(domain.rows())) {
                if (row.id() != null) {
                    EduGoalRow existing = existingRows.get(row.id());
                    if (existing == null
                            || existing.getKind() != rowKind(row.kind())
                            || !requestedRowIds.add(row.id())) {
                        throw GlobalException.of(ApiCode.INVALID_VALUE, "dòng mục tiêu");
                    }
                }
                for (YearPlanRequest.Content content : nullToEmpty(row.contents())) {
                    if (content.id() != null
                            && (!existingContents.containsKey(content.id())
                                    || !requestedContentIds.add(content.id()))) {
                        throw GlobalException.of(ApiCode.INVALID_VALUE, "nội dung mục tiêu");
                    }
                }
            }
            if (!domains.containsKey(domain.domainId())) {
                throw GlobalException.of(ApiCode.EDU_DOMAIN_NOT_FOUND, domain.domainId());
            }
        }
        // EDU-09: MT đánh số liên tục cả năm theo thứ tự lĩnh vực đã cấu hình, rồi thứ
        // tự dòng
        requestDomains.sort(Comparator.comparing(d -> domains.get(d.domainId()).getSortOrder()));

        Set<Long> keptRowIds = new HashSet<>();
        Set<Long> keptContentIds = new HashSet<>();
        List<EduContentThemeTick> ticks = new ArrayList<>();
        short goalNo = 0;
        for (YearPlanRequest.Domain domain : requestDomains) {
            short sortOrder = 0;
            Long subDomainId = null;
            Long expectedResultId = null;
            for (YearPlanRequest.Row requestRow : nullToEmpty(domain.rows())) {
                String text = trimToEmpty(requestRow.text());
                if (text.isEmpty()) {
                    continue;
                }
                EduGoalRowKind kind = rowKind(requestRow.kind());
                EduGoalRow row = Optional.ofNullable(requestRow.id())
                        .map(existingRows::get)
                        .orElseGet(() -> EduGoalRow.create(planId, kind));
                row.place(domain.domainId(), sortOrder++, kind, text, kind == EduGoalRowKind.GOAL ? ++goalNo : null);
                if (row instanceof com.carenest.entity.eduplan.EduPlanGoal goal && requestRow.displayLabel() != null) {
                    String label = requestRow.displayLabel().strip();
                    label = label.isEmpty() ? null : label;
                    if (plan.getPlanLevel() == EduPlanLevel.TEAM && !Objects.equals(goal.getDisplayLabel(), label)) {
                        throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                    }
                    goal.label(label);
                }
                Long curriculumId = requestRow.curriculumId(kind);
                row.linkCurriculum(curriculum.get(kind).contains(curriculumId) ? curriculumId : null);
                row.attachParents(
                        kind == EduGoalRowKind.SUB ? null : subDomainId,
                        kind == EduGoalRowKind.GOAL ? expectedResultId : null);
                row = saveRow(row);
                if (kind == EduGoalRowKind.SUB) {
                    subDomainId = row.getId();
                    expectedResultId = null;
                } else if (kind == EduGoalRowKind.GROUP) {
                    expectedResultId = row.getId();
                }
                keptRowIds.add(row.getId());
                if (kind != EduGoalRowKind.GOAL) {
                    continue;
                }
                short contentOrder = 0;
                for (YearPlanRequest.Content requestContent : nullToEmpty(requestRow.contents())) {
                    String contentText = trimToEmpty(requestContent.text());
                    if (contentText.isEmpty()) {
                        continue;
                    }
                    EduGoalContent content = Optional.ofNullable(requestContent.id())
                            .map(existingContents::get)
                            .orElseGet(EduGoalContent::create);
                    content.place(row.getId(), contentOrder++, contentText);
                    content = goalContentRepository.save(content);
                    keptContentIds.add(content.getId());
                    // Key chủ đề không có trong request (chủ đề trống đã bị bỏ) thì bỏ qua dấu tích
                    for (Long themeId : (plan.getPlanLevel() == EduPlanLevel.SCHOOL_YEAR
                                    ? List.<String>of()
                                    : nullToEmpty(requestContent.themeKeys()))
                            .stream()
                                    .map(themeIdByKey::get)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toCollection(LinkedHashSet::new))) {
                        ticks.add(EduContentThemeTick.of(content.getId(), themeId));
                    }
                }
            }
        }
        goalContentRepository.flush();

        // Chỉ xóa tick bị bỏ, thêm tick mới: xóa JPQL rồi merge lại sẽ bỏ sót tick còn
        // trong persistence context
        Set<EduContentThemeTick.Key> wanted =
                ticks.stream().map(EduContentThemeTick::key).collect(Collectors.toSet());
        Set<Long> planThemeIds = new HashSet<>(themeIdByKey.values());
        List<EduContentThemeTick> existingTicks =
                planThemeIds.isEmpty() ? List.of() : tickRepository.findAllByThemeIdIn(planThemeIds);
        Set<EduContentThemeTick.Key> existingKeys =
                existingTicks.stream().map(EduContentThemeTick::key).collect(Collectors.toSet());
        tickRepository.deleteAll(existingTicks.stream()
                .filter(tick -> !wanted.contains(tick.key()))
                .toList());
        tickRepository.flush();
        goalContentRepository.deleteAllInBatch(existingContents.values().stream()
                .filter(content -> !keptContentIds.contains(content.getId()))
                .toList());
        // Cập nhật cha trước khi xóa tiêu đề cũ để CASCADE không xóa mục tiêu đang
        // chuyển.
        goalRepository.flush();
        List<EduGoalRow> removedRows = existingRows.values().stream()
                .filter(row -> !keptRowIds.contains(row.getId()))
                .toList();
        goalRepository.deleteAllInBatch(removedRows.stream()
                .filter(EduPlanGoal.class::isInstance)
                .map(EduPlanGoal.class::cast)
                .toList());
        expectedResultRepository.deleteAllInBatch(removedRows.stream()
                .filter(EduPlanExpectedResult.class::isInstance)
                .map(EduPlanExpectedResult.class::cast)
                .toList());
        subDomainRepository.deleteAllInBatch(removedRows.stream()
                .filter(EduPlanSubDomain.class::isInstance)
                .map(EduPlanSubDomain.class::cast)
                .toList());
        tickRepository.saveAll(ticks.stream()
                .filter(tick -> !existingKeys.contains(tick.key()))
                .toList());
    }

    private List<EduGoalRow> findRows(Long planId) {
        List<EduGoalRow> rows = new ArrayList<>();
        rows.addAll(subDomainRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId));
        rows.addAll(expectedResultRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId));
        rows.addAll(goalRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId));
        rows.sort(Comparator.comparing(EduGoalRow::getDomainId)
                .thenComparing(EduGoalRow::getSortOrder)
                .thenComparing(EduGoalRow::getId));
        return rows;
    }

    private EduGoalRow saveRow(EduGoalRow row) {
        row.initializeOwner(requirePlan(row.getYearPlanId()));
        return switch (row.getKind()) {
            case SUB -> subDomainRepository.save((EduPlanSubDomain) row);
            case GROUP -> expectedResultRepository.save((EduPlanExpectedResult) row);
            case GOAL -> goalRepository.save((EduPlanGoal) row);
        };
    }

    private Map<String, Long> saveThemes(Long planId, List<YearPlanRequest.Theme> requestThemes) {
        Map<Long, EduTheme> existing = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                .collect(Collectors.toMap(EduTheme::getId, Function.identity()));
        Map<String, Long> idByKey = new LinkedHashMap<>();
        Set<Long> kept = new HashSet<>();
        Set<String> abbrs = new HashSet<>();
        short sortOrder = 0;
        for (YearPlanRequest.Theme requestTheme : requestThemes) {
            if (idByKey.containsKey(requestTheme.key())) {
                throw GlobalException.of(ApiCode.THEME_KEY_DUPLICATE, requestTheme.key());
            }
            // EDU-42: viết tắt trùng chặn ngay khi lưu nháp; ô trống chưa tính, gửi mới bắt
            // buộc
            String abbrKey = trimToEmpty(requestTheme.abbr()).toLowerCase(Locale.ROOT);
            if (!abbrKey.isEmpty() && !abbrs.add(abbrKey)) {
                throw GlobalException.of(ApiCode.THEME_ABBR_DUPLICATE);
            }
            EduTheme theme =
                    Optional.ofNullable(requestTheme.id()).map(existing::get).orElseGet(() -> EduTheme.create(planId));
            theme.update(
                    sortOrder++,
                    trimToEmpty(requestTheme.name()),
                    trimToEmpty(requestTheme.abbr()),
                    requestTheme.startDate(),
                    requestTheme.endDate(),
                    trimToEmpty(requestTheme.festivals()),
                    trimToEmpty(requestTheme.note()));
            theme.initializeOwner(requirePlan(planId));
            theme = themeRepository.save(theme);
            kept.add(theme.getId());
            idByKey.put(requestTheme.key(), theme.getId());
        }
        themeRepository.flush();
        List<EduTheme> removed = existing.values().stream()
                .filter(t -> !kept.contains(t.getId()))
                .toList();
        // Dấu tích vào chủ đề bị bỏ xóa theo ON DELETE CASCADE
        themeRepository.deleteAllInBatch(removed);
        return idByKey;
    }

    private void saveBreaks(Long planId, List<YearPlanRequest.Break> requestBreaks) {
        Map<Long, EduBreak> existing = breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                .collect(Collectors.toMap(EduBreak::getId, Function.identity()));
        Set<Long> kept = new HashSet<>();
        short sortOrder = 0;
        for (YearPlanRequest.Break requestBreak : requestBreaks) {
            EduBreak item =
                    Optional.ofNullable(requestBreak.id()).map(existing::get).orElseGet(() -> EduBreak.create(planId));
            item.update(
                    sortOrder++, trimToEmpty(requestBreak.label()), requestBreak.startDate(), requestBreak.endDate());
            item.initializeOwner(requirePlan(planId));
            item = breakRepository.save(item);
            kept.add(item.getId());
        }
        breakRepository.deleteAllInBatch(existing.values().stream()
                .filter(b -> !kept.contains(b.getId()))
                .toList());
    }

    // UI ghi 1 dòng "Phó hiệu trưởng chỉnh sửa" mỗi lượt sửa; lưu nhiều lần nên
    // không ghi lặp khi dòng cuối đã là nó
    private void recordVicePrincipalEdit(CurrentUser user, EduPlan plan, Instant now) {
        boolean lastIsEdit = eventRepository
                .findFirstByYearPlanIdOrderByOccurredAtDescIdDesc(plan.getId())
                .map(event -> ACTION_VP_EDIT.equals(event.getAction()))
                .orElse(false);
        if (!lastIsEdit) {
            recordEvent(
                    plan,
                    ACTION_VP_EDIT,
                    "Phó hiệu trưởng chỉnh sửa",
                    user,
                    TITLE_VICE_PRINCIPAL,
                    TONE_NONE,
                    null,
                    now);
        }
    }

    private void recordEvent(
            EduPlan plan,
            String action,
            String label,
            CurrentUser actor,
            String actorRoleLabel,
            String tone,
            String note,
            Instant now) {
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        EduPlanEvent event = EduPlanEvent.of(
                plan.getId(), action, label, actor.userId(), actor.fullName(), actorRoleLabel, tone, cleanNote, now);
        event.initializeOwner(plan);
        eventRepository.save(event);
    }

    // Hiện hành = phiên bản đã ban hành có số lớn nhất của chuỗi (spec 004 FR-013)
    private static Optional<EduPlan> currentVersion(List<EduPlan> chain) {
        return chain.stream()
                .filter(p -> p.publicStatus() == EduPlanStatus.APPROVED)
                .max(Comparator.comparingInt(EduPlan::getVersionNo));
    }

    // Chép cả cây sang phiên bản mới, giữ lineage; dấu tích ánh xạ theo id mới của
    // nội dung và chủ đề
    private void copyContent(Long sourceId, Long targetId) {
        teamUpdates.copyContent(sourceId, targetId);
    }

    private YearPlanChangesResponse compare(EduPlan from, EduPlan plan) {
        if (from.getPlanLevel() != plan.getPlanLevel()
                || !from.getSchoolYearId().equals(plan.getSchoolYearId())
                || !from.getAgeGroupId().equals(plan.getAgeGroupId())
                || from.getId().equals(plan.getId())) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_VERSION_COMPARE_INVALID);
        }
        return YearPlanVersionDiff.compare(snapshot(from), snapshot(plan));
    }

    private YearPlanVersionDiff.Snapshot snapshot(EduPlan plan) {
        List<EduTheme> themes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
        Map<Long, Long> themeLineage = themes.stream().collect(Collectors.toMap(EduTheme::getId, EduTheme::lineage));
        List<EduGoalRow> goals = findRows(plan.getId()).stream()
                .filter(r -> r.getKind() == EduGoalRowKind.GOAL)
                .sorted(Comparator.comparing(EduGoalRow::getGoalNo))
                .toList();
        Map<Long, Long> goalLineage = goals.stream().collect(Collectors.toMap(EduGoalRow::getId, EduGoalRow::lineage));
        Map<Long, Set<Long>> ticks = themes.isEmpty()
                ? Map.of()
                : tickRepository.findAllByThemeIdIn(themeLineage.keySet()).stream()
                        .collect(Collectors.groupingBy(
                                EduContentThemeTick::getContentId,
                                Collectors.mapping(t -> themeLineage.get(t.getThemeId()), Collectors.toSet())));
        List<EduGoalContent> contents = goals.isEmpty()
                ? List.of()
                : goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(goalLineage.keySet());
        return new YearPlanVersionDiff.Snapshot(
                plan.getId(),
                plan.getVersionNo(),
                goals.stream()
                        .map(r -> new YearPlanVersionDiff.GoalItem(r.getId(), r.lineage(), r.getGoalNo(), r.getText()))
                        .toList(),
                contents.stream()
                        .map(c -> new YearPlanVersionDiff.ContentItem(
                                c.getId(),
                                c.lineage(),
                                goalLineage.get(c.getGoalRowId()),
                                c.getText(),
                                ticks.getOrDefault(c.getId(), Set.of())))
                        .toList(),
                themes.stream()
                        .map(t -> new YearPlanVersionDiff.ThemeItem(
                                t.getId(), t.lineage(), t.getName(), t.getAbbr(), t.getStartDate(), t.getEndDate()))
                        .toList());
    }

    private YearPlanDetailResponse toDetail(CurrentUser user, EduPlan plan) {
        Long planId = plan.getId();
        List<EduGoalRow> rows = findRows(planId);
        List<EduTheme> themes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId);
        List<EduGoalContent> contents = rows.isEmpty()
                ? List.of()
                : goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(
                        rows.stream().map(EduGoalRow::getId).toList());
        Map<Long, Short> themeOrder =
                themes.stream().collect(Collectors.toMap(EduTheme::getId, EduTheme::getSortOrder));
        Map<Long, List<Long>> themeIdsByContent = themes.isEmpty()
                ? Map.of()
                : tickRepository.findAllByThemeIdIn(themeOrder.keySet()).stream()
                        .sorted(Comparator.comparing(tick -> themeOrder.get(tick.getThemeId())))
                        .collect(Collectors.groupingBy(
                                EduContentThemeTick::getContentId,
                                Collectors.mapping(EduContentThemeTick::getThemeId, Collectors.toList())));
        Map<Long, List<YearPlanDetailResponse.Content>> contentsByRow = contents.stream()
                .collect(Collectors.groupingBy(
                        EduGoalContent::getGoalRowId,
                        Collectors.mapping(
                                c -> mapper.toContent(c, themeIdsByContent.getOrDefault(c.getId(), List.of())),
                                Collectors.toList())));
        Map<Long, List<EduGoalRow>> rowsByDomain =
                rows.stream().collect(Collectors.groupingBy(EduGoalRow::getDomainId));
        Map<EduGoalRowKind, Map<Long, CurriculumRef>> curriculum = new EnumMap<>(EduGoalRowKind.class);
        for (EduGoalRowKind kind : EduGoalRowKind.values()) {
            curriculum.put(
                    kind,
                    curriculumService.findRefs(
                            kind,
                            rows.stream()
                                    .filter(r -> r.getKind() == kind)
                                    .map(EduGoalRow::getCurriculumId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet())));
        }

        // UI luôn hiện đủ các lĩnh vực đang dùng; lĩnh vực đã tắt vẫn hiện nếu kế hoạch
        // còn dòng của nó
        List<YearPlanDetailResponse.Domain> domains = domainRepository.findAll().stream()
                .filter(d -> d.isActive() || rowsByDomain.containsKey(d.getId()))
                .sorted(Comparator.comparing(EduDomain::getSortOrder))
                .map(d -> new YearPlanDetailResponse.Domain(
                        d.getId(),
                        d.getName(),
                        d.getDisplayLabel() == null ? d.getName() : d.getDisplayLabel(),
                        rowsByDomain.getOrDefault(d.getId(), List.of()).stream()
                                .map(r -> mapper.toRow(
                                        r,
                                        contentsByRow.getOrDefault(r.getId(), List.of()),
                                        r.getCurriculumId() == null
                                                ? null
                                                : curriculum.get(r.getKind()).get(r.getCurriculumId())))
                                .toList()))
                .toList();

        List<EduPlan> chain = yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                plan.getPlanLevel(), plan.getSchoolYearId(), plan.getAgeGroupId());
        Long currentId = currentVersion(chain).map(EduPlan::getId).orElse(null);
        List<EduPlan> visibleChain =
                chain.stream().filter(p -> canView(user, p)).toList();
        List<EduPlanEvent> events = eventRepository.findAllByYearPlanIdOrderByOccurredAtAscIdAsc(planId);
        // Lịch sử duyệt chỉ cho người phụ trách và người duyệt; người chỉ xem thấy danh sách bản ban hành (FR-019)
        boolean seesHistory = isAuthor(user, plan)
                || isCurrentApprover(user, plan)
                || events.stream().anyMatch(e -> user.userId().equals(e.getActorId()));
        String lastReturnNote = events.stream()
                .filter(e -> ACTION_RETURN.equals(e.getAction()))
                .reduce((first, second) -> second)
                .map(EduPlanEvent::getNote)
                .orElse(null);
        Long publisherId = plan.getPublishedBy() != null ? plan.getPublishedBy() : plan.getIssuedBy();
        Instant publishedAt = plan.getCompletedAt() != null ? plan.getCompletedAt() : plan.getIssuedAt();
        Map<Long, String> names = accountService.findFullNames(Stream.concat(
                        Stream.of(plan.getOwnerId(), plan.getSentBy(), plan.getIssuedBy(), plan.getPublishedBy()),
                        visibleChain.stream()
                                .flatMap(p -> Stream.of(p.getOwnerId(), p.getIssuedBy(), p.getPublishedBy())))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        String schoolYearName = schoolStructureService
                .findSchoolYear(plan.getSchoolYearId())
                .map(SchoolYearResponse::name)
                .orElse(null);
        String ageGroupName = schoolStructureService
                .findAgeGroup(plan.getAgeGroupId())
                .map(AgeGroupResponse::name)
                .orElse(null);

        return new YearPlanDetailResponse(
                planId,
                plan.getCode(),
                plan.getSchoolYearId(),
                schoolYearName,
                plan.getAgeGroupId(),
                ageGroupName,
                plan.getTitle(),
                plan.getDescription(),
                plan.publicStatus(),
                mapper.toOwner(plan, names),
                mapper.toActor(plan.getSentBy(), plan.getSentAt(), names),
                mapper.toActor(publisherId, publishedAt, names),
                null,
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                domains,
                themes.stream().map(mapper::toTheme).toList(),
                breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                        .map(mapper::toBreak)
                        .toList(),
                seesHistory ? events.stream().map(mapper::toHistory).toList() : List.of(),
                seesHistory ? lastReturnNote : null,
                buildReviewState(plan),
                permissionsOf(user, plan, chain, currentId),
                plan.getContentRevision(),
                null,
                plan.getVersionNo(),
                plan.getSourcePlanId(),
                plan.getId().equals(currentId),
                visibleChain.stream()
                        .map(p -> new YearPlanDetailResponse.VersionSummary(
                                p.getId(),
                                p.getVersionNo(),
                                p.getCode(),
                                p.publicStatus(),
                                names.get(p.getOwnerId()),
                                p.getCreatedAt(),
                                p.getIssuedBy() == null ? null : names.get(p.getIssuedBy()),
                                p.getIssuedAt(),
                                p.getId().equals(currentId)))
                        .toList(),
                plan.getPlanLevel(),
                plan.getCurriculumSourcePlanId(),
                plan.sourceMode(),
                plan.getDurationPolicySnapshot(),
                templateRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                        .map(t -> new YearPlanDetailResponse.BranchTemplate(
                                t.getId(),
                                t.getThemeId(),
                                t.lineage(),
                                t.getSourceTemplateId(),
                                t.getName(),
                                t.getStartDate(),
                                t.getEndDate()))
                        .toList(),
                canEdit(user, plan, clock.instant()) && plan.getCurriculumSourcePlanId() == null,
                canEdit(user, plan, clock.instant()) && plan.getPlanLevel() == EduPlanLevel.TEAM,
                canEdit(user, plan, clock.instant()),
                List.of(),
                plan instanceof com.carenest.entity.eduplan.EduTeamPlan team ? team.getPreviousSourcePlanId() : null,
                plan.getPlanLevel() == EduPlanLevel.TEAM ? teamService.reviewItems(plan) : List.of(),
                plan.getPlanLevel() == EduPlanLevel.TEAM ? teamService.newItems(plan) : List.of(),
                timeLockOf(user, plan));
    }

    private YearPlanDetailResponse.TimeLock timeLockOf(CurrentUser user, EduPlan plan) {
        EduDeadline.Info info = timeLock.info(plan);
        boolean locked = timeLock.locked(plan);
        return new YearPlanDetailResponse.TimeLock(
                info.deadline(),
                info.extendedTo(),
                info.state(),
                locked,
                locked && accessPolicy.isPrincipal(user) && !timeLock.released(plan));
    }

    private YearPlanDetailResponse.Permissions permissionsOf(
            CurrentUser user, EduPlan plan, List<EduPlan> chain, Long currentId) {
        Instant now = clock.instant();
        boolean isDraft = plan.publicStatus() == EduPlanStatus.DRAFT;
        boolean isReview = plan.publicStatus() == EduPlanStatus.REVIEW;
        boolean author = isEditor(user, plan);
        boolean excluded =
                eduWorkflowService.getAuthorUserIdsForYearPlan(plan.getId()).contains(user.userId());
        boolean canApprove = isReview && !excluded && isUserWaitingRole(user, plan);
        boolean canReturn = canApprove;
        return new YearPlanDetailResponse.Permissions(
                canEdit(user, plan, now),
                isDraft && author && !timeLock.locked(plan),
                canApprove,
                canApprove,
                canReturn,
                isDraft && plan.getPlanLevel() == EduPlanLevel.SCHOOL_YEAR && accessPolicy.isVicePrincipal(user),
                plan.getPlanLevel() == EduPlanLevel.SCHOOL_YEAR
                        && plan.getId().equals(currentId)
                        && chain.stream().allMatch(p -> p.publicStatus() == EduPlanStatus.APPROVED)
                        && author
                        && !timeLock.locked(plan));
    }

    private final EduWorkflowPolicy evaluator = new EduWorkflowPolicy();

    private boolean isRoleMatch(CurrentUser user, EduWorkflowPolicy.Role role, Long schoolYearId, Long ageGroupId) {
        if (role == EduWorkflowPolicy.Role.PRINCIPAL) {
            return accessPolicy.isPrincipal(user);
        }
        if (role == EduWorkflowPolicy.Role.VICE_PRINCIPAL) {
            return accessPolicy.isVicePrincipal(user);
        }
        if (role == EduWorkflowPolicy.Role.TEAM_LEADER) {
            return ageGroupId != null && accessPolicy.isTeamLeaderOf(user, schoolYearId, ageGroupId);
        }
        if (role == EduWorkflowPolicy.Role.TEACHER) {
            return ageGroupId != null && accessPolicy.isTeacherOfAgeGroup(user, ageGroupId);
        }
        return false;
    }

    // Spec 003 FR-010/011: thông báo người duyệt bước đang chờ / người gửi, kèm đường dẫn mở thẳng kế hoạch
    private String planLink(EduPlan plan) {
        return (plan.getPlanLevel() == EduPlanLevel.SCHOOL_YEAR ? "/education/school-year-plans/" : "/education/goals/")
                + plan.getId();
    }

    private String planLabel(EduPlan plan) {
        return (plan.getPlanLevel() == EduPlanLevel.SCHOOL_YEAR ? "Kế hoạch năm học " : "Kế hoạch tổ ")
                + plan.getTitle();
    }

    private void notifyReviewers(
            EduPlan plan, CurrentUser user, com.carenest.utils.enums.NotificationType type, String note) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        EduWorkflowPolicy.Evaluation eval = eduWorkflowService.evaluateYearPlan(plan, today);
        if (eval.waitingRole() == null) return;
        Set<Long> to = new java.util.HashSet<>(eduWorkflowService
                .resolveActiveCandidates(
                        eduWorkflowService.loadPolicy(plan.workflowKey()), plan.getAgeGroupId(), null, today)
                .getOrDefault(eval.waitingRole(), Set.of()));
        to.removeAll(eduWorkflowService.getAuthorUserIdsForYearPlan(plan.getId()));
        to.remove(user.userId());
        boolean submit = type == com.carenest.utils.enums.NotificationType.EDU_SUBMITTED;
        notificationService.notifyUsers(
                to,
                type,
                (submit ? "Chờ bạn duyệt: " : "Chuyển bạn duyệt tiếp: ") + planLabel(plan),
                user.fullName()
                        + (submit ? " gửi duyệt" : " đã duyệt bước trước")
                        + (note == null || note.isBlank() ? "" : ". Ghi chú: " + note),
                planLink(plan),
                false);
    }

    private void notifyAuthor(
            EduPlan plan,
            CurrentUser user,
            com.carenest.utils.enums.NotificationType type,
            String titlePrefix,
            String verb,
            String note) {
        Long author = plan.getSentBy() != null ? plan.getSentBy() : plan.getOwnerId();
        if (author == null || author.equals(user.userId())) return;
        notificationService.notifyUsers(
                Set.of(author),
                type,
                titlePrefix + planLabel(plan),
                user.fullName() + verb + (note == null || note.isBlank() ? "" : ": " + note),
                planLink(plan),
                false);
    }

    private String roleLabel(CurrentUser user) {
        if (accessPolicy.isPrincipal(user)) return "Hiệu trưởng";
        if (accessPolicy.isVicePrincipal(user)) return "Phó hiệu trưởng";
        return "Tổ trưởng nhóm tuổi";
    }

    private boolean isUserWaitingRole(CurrentUser user, EduPlan plan) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        EduWorkflowPolicy.Evaluation eval = eduWorkflowService.evaluateYearPlan(plan, today);
        return eval.waitingRole() != null
                && isRoleMatch(user, eval.waitingRole(), plan.getSchoolYearId(), plan.getAgeGroupId());
    }

    private boolean isUserReviewer(CurrentUser user, EduPlan plan) {
        return eduWorkflowService.hasReviewerPrecedence(user, plan.workflowKey(), plan.getAgeGroupId(), null);
    }

    private com.carenest.dto.eduplan.EduReviewStateResponse buildReviewState(EduPlan plan) {
        EduWorkflowPolicy.RolePolicy policy = eduWorkflowService.loadPolicy(plan.workflowKey());
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        EduWorkflowPolicy.Evaluation eval =
                plan.publicStatus() == EduPlanStatus.REVIEW ? eduWorkflowService.evaluateYearPlan(plan, today) : null;
        long rev = plan.getAppliedPolicyRevision() != null
                ? plan.getAppliedPolicyRevision()
                : settingsRepository
                        .findById((short) 1)
                        .map(com.carenest.entity.eduplan.EduSettings::getWorkflowRevision)
                        .orElse(1L);
        return eduWorkflowService.toReviewStateResponse(
                rev,
                plan.getReviewRound(),
                policy,
                eval,
                plan.getApprovals(),
                plan.getAuthorCoverage(),
                plan.getAuthorTrackingSince(),
                plan.getCompletionMode(),
                plan.getCompletedAt(),
                plan.getCompletionPolicy(),
                plan.getPrintSnapshot());
    }

    private String actorTitle(CurrentUser user) {
        return accessPolicy.isVicePrincipal(user) ? TITLE_VICE_PRINCIPAL : TITLE_TEAM_LEADER;
    }

    // Mã như UI: KHNH-<2 số cuối năm đầu><2 số cuối năm sau>-<chữ đầu tên nhóm
    // tuổi, tối đa 3>, vd. KHNH-2627-MGN
    private static String planCode(SchoolYearResponse schoolYear, AgeGroupResponse ageGroup) {
        String digits = schoolYear.name().replaceAll("\\D", "");
        String years = digits.length() >= 8 ? digits.substring(2, 4) + digits.substring(6, 8) : digits;
        String baseName = ageGroup.name().split("\\(")[0].trim();
        String initials = Stream.of(baseName.split("\\s+"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT))
                .limit(3)
                .collect(Collectors.joining());
        return "KHNH-" + years + "-" + initials;
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    // Loại dòng do client gửi sai: 400 thay vì 500 (IllegalArgumentException của
    // enum)
    private static EduGoalRowKind rowKind(String code) {
        try {
            return EduGoalRowKind.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw GlobalException.withCause(ApiCode.INVALID_VALUE, ex, "loại dòng mục tiêu");
        }
    }
}
