package com.carenest.service.eduplan.impl;

import com.carenest.config.EduPlanProperties;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.CurriculumGoalRef;
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
import com.carenest.entity.eduplan.EduPlanEvent;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.eduplan.EduYearPlanMapper;
import com.carenest.repository.eduplan.EduBreakRepository;
import com.carenest.repository.eduplan.EduContentThemeTickRepository;
import com.carenest.repository.eduplan.EduDomainRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduGoalRowRepository;
import com.carenest.repository.eduplan.EduPlanEventRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.repository.eduplan.EduYearPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.eduplan.EduCurriculumService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.EduPermission;
import com.carenest.utils.enums.EduGoalRowKind;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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

    // Nhãn lịch sử và chức danh theo UI (GoalFormPage, GoalDetailPage, seed kế hoạch năm)
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

    private final EduYearPlanRepository yearPlanRepository;
    private final EduGoalRowRepository goalRowRepository;
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
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<YearPlanSummaryResponse> getYearPlans(
            CurrentUser currentUser, Long schoolYearId, Long ageGroupId, String keyword, Pageable pageable) {
        String logPrefix = LOG_PREFIX + "|getYearPlans|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            requirePermission(currentUser, EduPermission.GOALS_VIEW);
            EduPlanAccessPolicy.ViewScope scope = accessPolicy.viewScope(currentUser);
            String pattern = keyword == null || keyword.isBlank()
                    ? null
                    : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            Page<EduYearPlan> page = yearPlanRepository.search(
                    schoolYearId,
                    ageGroupId,
                    scope.schoolWideStatuses(),
                    scope.fullAgeGroupIds(),
                    scope.approvedAgeGroupIds(),
                    pattern,
                    pageable);

            List<Long> planIds =
                    page.getContent().stream().map(EduYearPlan::getId).toList();
            Map<Long, Long> goalCounts =
                    planIds.isEmpty() ? Map.of() : toCountMap(goalRowRepository.countGoalsByPlan(planIds));
            Map<Long, Long> themeCounts =
                    planIds.isEmpty() ? Map.of() : toCountMap(themeRepository.countByPlan(planIds));
            Map<Long, String> names = accountService.findFullNames(page.getContent().stream()
                    .flatMap(p -> Stream.of(p.getOwnerId(), p.getSentBy(), p.getIssuedBy(), p.getEditingBy()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            Map<Long, String> schoolYearNames = schoolStructureService.findSchoolYears().stream()
                    .collect(Collectors.toMap(SchoolYearResponse::id, SchoolYearResponse::name));
            Map<Long, String> ageGroupNames = schoolStructureService.findAgeGroups().stream()
                    .collect(Collectors.toMap(AgeGroupResponse::id, AgeGroupResponse::name));
            Set<Long> currentIds = page.getContent().stream()
                    .map(p -> List.of(p.getSchoolYearId(), p.getAgeGroupId()))
                    .distinct()
                    .map(key -> currentVersion(yearPlanRepository.findAllBySchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                            key.get(0), key.get(1))))
                    .flatMap(Optional::stream)
                    .map(EduYearPlan::getId)
                    .collect(Collectors.toSet());

            PageResponse<YearPlanSummaryResponse> response = PageResponse.from(
                    page,
                    plan -> mapper.toSummaryResponse(
                            plan,
                            schoolYearNames.get(plan.getSchoolYearId()),
                            ageGroupNames.get(plan.getAgeGroupId()),
                            goalCounts.getOrDefault(plan.getId(), 0L),
                            themeCounts.getOrDefault(plan.getId(), 0L),
                            names,
                            plan.getEditingAt() == null
                                    ? null
                                    : plan.getEditingAt().plus(properties.editLockTtl()),
                            currentIds.contains(plan.getId())));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public YearPlanDetailResponse getYearPlan(CurrentUser currentUser, Long planId) {
        String logPrefix = LOG_PREFIX + "|getYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduYearPlan plan = loadViewablePlan(currentUser, planId);
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
            if (request.schoolYearId() == null || request.ageGroupId() == null) {
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "năm học, nhóm tuổi");
            }
            SchoolYearResponse schoolYear = schoolStructureService
                    .findSchoolYear(request.schoolYearId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
            AgeGroupResponse ageGroup = schoolStructureService
                    .findAgeGroup(request.ageGroupId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));
            // EDU-02: Phó HT mọi nhóm tuổi, Tổ trưởng chỉ nhóm tuổi mình
            if (!accessPolicy.canCreate(currentUser, ageGroup.id())) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_CREATE_FORBIDDEN);
            }
            requireEditContext(edit);
            Optional<EduYearPlan> retried =
                    yearPlanRepository.findByOwnerIdAndCreatedRequestId(currentUser.userId(), edit.requestId());
            if (retried.isPresent()) {
                EduYearPlan previous = retried.get();
                if (!previous.getSchoolYearId().equals(request.schoolYearId())
                        || !previous.getAgeGroupId().equals(request.ageGroupId())) {
                    throw GlobalException.of(ApiCode.YEAR_PLAN_REQUEST_REUSED);
                }
                log.info("{}|END", logPrefix);
                return toDetail(currentUser, previous);
            }

            // EDU-01
            if (yearPlanRepository.existsBySchoolYearIdAndAgeGroupId(schoolYear.id(), ageGroup.id())) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_EXISTS);
            }

            Instant now = clock.instant();
            EduYearPlan plan = EduYearPlan.createDraft(
                    planCode(schoolYear, ageGroup),
                    schoolYear.id(),
                    ageGroup.id(),
                    request.title().trim(),
                    trimToEmpty(request.description()),
                    currentUser.userId(),
                    actorTitle(currentUser),
                    now);
            plan.acquireSession(currentUser.userId(), edit.sessionId(), now);
            plan.initializeSave(edit.requestId());
            try {
                plan = yearPlanRepository.saveAndFlush(plan);
            } catch (DataIntegrityViolationException ex) {
                // Hai người cùng tạo cho một nhóm tuổi: bản thứ hai vấp unique (school_year_id, age_group_id)
                throw GlobalException.withCause(ApiCode.YEAR_PLAN_EXISTS, ex);
            }
            saveContent(plan, request);

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
            EduYearPlan plan = loadPlanForUpdate(currentUser, planId);
            Instant now = clock.instant();
            requireEditable(currentUser, plan, now);
            requireSession(currentUser, plan, edit, now);
            if (edit.requestId().equals(plan.getLastSaveRequestId())) {
                log.info("{}|END", logPrefix);
                return toDetail(currentUser, plan);
            }
            requireRevision(plan, edit);
            plan.updateInfo(request.title().trim(), trimToEmpty(request.description()), now);
            saveContent(plan, request);
            plan.recordSave(edit.requestId());
            plan.touchEditing(now);
            if (plan.getStatus() == EduPlanStatus.PENDING_VP) {
                recordVicePrincipalEdit(currentUser, plan, now);
            }

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
    public YearPlanDetailResponse submitYearPlan(
            CurrentUser currentUser, Long planId, String note, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|submitYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduYearPlan plan = loadPlanForUpdate(currentUser, planId);
            Instant now = clock.instant();
            // EDU-04: Tổ trưởng của nhóm tuổi gửi; PHT tự soạn thì ban hành luôn
            if (!accessPolicy.isTeamLeaderOf(currentUser, plan.getAgeGroupId())) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_SUBMIT_FORBIDDEN);
            }
            if (!isDraftLike(plan)) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_INVALID_STATUS, "gửi duyệt");
            }
            requireSession(currentUser, plan, edit, now);
            requireRevision(plan, edit);
            requireReadyForReview(plan);

            plan.submit(currentUser.userId(), now);
            plan.recordSave(null);
            yearPlanRepository.save(plan);
            recordEvent(
                    plan,
                    ACTION_SUBMIT,
                    "Gửi Phó hiệu trưởng duyệt",
                    currentUser,
                    TITLE_TEAM_LEADER,
                    TONE_NONE,
                    note,
                    now);

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
            EduYearPlan plan = loadPlanForUpdate(currentUser, planId);
            Instant now = clock.instant();
            if (!accessPolicy.isVicePrincipal(currentUser)) {
                throw GlobalException.of(ApiCode.VICE_PRINCIPAL_ONLY, "ban hành");
            }
            // EDU-05: ban hành bản chờ duyệt, hoặc bản nháp do chính PHT soạn
            boolean pending = plan.getStatus() == EduPlanStatus.PENDING_VP;
            if (!pending && !(isDraftLike(plan) && isOwner(currentUser, plan))) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_INVALID_STATUS, "ban hành");
            }
            requireSession(currentUser, plan, edit, now);
            requireRevision(plan, edit);
            requireReadyForReview(plan);

            plan.issue(currentUser.userId(), now);
            plan.recordSave(null);
            yearPlanRepository.save(plan);
            recordEvent(plan, ACTION_ISSUE, "Ban hành", currentUser, TITLE_VICE_PRINCIPAL, TONE_OK, note, now);

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
            EduYearPlan plan = loadPlanForUpdate(currentUser, planId);
            if (!accessPolicy.isVicePrincipal(currentUser)) {
                throw GlobalException.of(ApiCode.VICE_PRINCIPAL_ONLY, "trả lại");
            }
            if (plan.getStatus() != EduPlanStatus.PENDING_VP) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_INVALID_STATUS, "trả lại");
            }
            // EDU-05: trả lại bắt buộc lý do (request đã @NotBlank; giữ kiểm ở đây cho caller khác)
            if (note == null || note.isBlank()) {
                throw GlobalException.of(ApiCode.RETURN_REASON_REQUIRED);
            }

            Instant now = clock.instant();
            requireSession(currentUser, plan, edit, now);
            requireRevision(plan, edit);
            plan.returnToOwner(now);
            plan.recordSave(null);
            yearPlanRepository.save(plan);
            recordEvent(plan, ACTION_RETURN, "Trả lại để sửa", currentUser, TITLE_VICE_PRINCIPAL, TONE_ERR, note, now);

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
            EduYearPlan plan = loadPlanForUpdate(currentUser, planId);
            // EDU-07: chỉ Phó HT xóa, và chỉ khi Nháp (kể cả đang do chính mình sửa)
            if (!canDeleteAsVicePrincipal(currentUser)) {
                throw GlobalException.of(ApiCode.VICE_PRINCIPAL_ONLY, "xóa");
            }
            if (plan.baseStatus() != EduPlanStatus.DRAFT) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_DELETE_DRAFT_ONLY);
            }
            requireSession(currentUser, plan, edit, clock.instant());
            requireRevision(plan, edit);
            // Bảng con xóa theo ON DELETE CASCADE
            yearPlanRepository.delete(plan);
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
            EduYearPlan plan = loadPlanForUpdate(currentUser, planId);
            Instant now = clock.instant();
            requireEditable(currentUser, plan, now);
            requireEditContext(edit);
            // EDU-10 (Tiền 27/10): cùng tài khoản mở lại (vd. sau mất điện) thì phiên mới nhận quyền ngay, phiên cũ mất
            // quyền;
            // người khác vẫn chờ khóa hết hạn (requireEditable ⇒ requireNotLockedByOther)
            plan.acquireSession(currentUser.userId(), edit.sessionId(), now);
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
            EduYearPlan plan = loadPlanForUpdate(currentUser, planId);
            // Không giữ khóa thì bỏ qua: trang soạn gọi khi thoát, kể cả lúc khóa đã hết hạn
            requireEditContext(edit);
            if (plan.isEditingBy(currentUser.userId()) && edit.sessionId().equals(plan.getEditSessionId())) {
                plan.finishEditing();
            }
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
            Instant now = clock.instant();
            List<EduYearPlan> expired =
                    yearPlanRepository.findAllByEditingAtLessThanEqual(now.minus(properties.editLockTtl()));
            expired.forEach(EduYearPlan::finishEditing);
            log.info("{}|count={}|END", logPrefix, expired.size());
            return expired.size();
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
            EduYearPlan plan = loadPlanForUpdate(currentUser, planId);
            Instant now = clock.instant();
            requireEditable(currentUser, plan, now);
            requireSession(currentUser, plan, edit, now);
            plan.touchEditing(now);
            log.info("{}|END", logPrefix);
            return toDetail(currentUser, plan);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public YearPlanDetailResponse createVersion(CurrentUser currentUser, Long planId, YearPlanEditRequest edit) {
        String logPrefix = LOG_PREFIX + "|createVersion|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            EduYearPlan source = loadPlanForUpdate(currentUser, planId);
            // EDU-25: người được sửa kế hoạch (PHT, TT nhóm tuổi) tạo phiên bản
            if (!accessPolicy.canCreate(currentUser, source.getAgeGroupId())) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_CREATE_FORBIDDEN);
            }
            requireEditContext(edit);
            Optional<EduYearPlan> retried =
                    yearPlanRepository.findByOwnerIdAndCreatedRequestId(currentUser.userId(), edit.requestId());
            if (retried.isPresent()) {
                if (!planId.equals(retried.get().getSourcePlanId())) {
                    throw GlobalException.of(ApiCode.YEAR_PLAN_REQUEST_REUSED);
                }
                log.info("{}|END", logPrefix);
                return toDetail(currentUser, retried.get());
            }
            List<EduYearPlan> chain = yearPlanRepository.findAllBySchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                    source.getSchoolYearId(), source.getAgeGroupId());
            Optional<EduYearPlan> open = chain.stream()
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
            requireRevision(source, edit);

            Instant now = clock.instant();
            int versionNo = source.getVersionNo() + 1;
            EduYearPlan plan = EduYearPlan.createVersion(
                    source,
                    chain.getFirst().getCode() + "-V" + versionNo,
                    currentUser.userId(),
                    actorTitle(currentUser),
                    now);
            plan.acquireSession(currentUser.userId(), edit.sessionId(), now);
            plan.initializeSave(edit.requestId());
            try {
                plan = yearPlanRepository.saveAndFlush(plan);
            } catch (DataIntegrityViolationException ex) {
                // Hai người cùng tạo: bản sau vấp unique phiên bản mở / số phiên bản
                throw GlobalException.withCause(ApiCode.YEAR_PLAN_OPEN_VERSION_EXISTS, ex, "khác");
            }
            copyContent(source.getId(), plan.getId());
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
    @Transactional(readOnly = true)
    public YearPlanChangesResponse getChanges(CurrentUser currentUser, Long planId, Long fromPlanId) {
        String logPrefix = LOG_PREFIX + "|getChanges|userId=" + currentUser.userId() + "|planId=" + planId
                + "|fromPlanId=" + fromPlanId;
        log.info("{}|START", logPrefix);
        try {
            EduYearPlan plan = loadViewablePlan(currentUser, planId);
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
    @Transactional(readOnly = true)
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

    private EduYearPlan requirePlan(Long planId) {
        return yearPlanRepository.findById(planId).orElseThrow(() -> GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
    }

    private void requireEditContext(YearPlanEditRequest edit) {
        if (edit == null
                || edit.sessionId() == null
                || edit.revision() == null
                || edit.requestId() == null
                || edit.revision() < 0) {
            throw GlobalException.of(ApiCode.EDIT_SESSION_REQUIRED);
        }
    }

    private void requireSession(CurrentUser user, EduYearPlan plan, YearPlanEditRequest edit, Instant now) {
        requireEditContext(edit);
        if (!plan.isEditingBy(user.userId())
                || !edit.sessionId().equals(plan.getEditSessionId())
                || plan.isEditLockExpired(now, properties.editLockTtl())) {
            throw GlobalException.of(ApiCode.EDIT_SESSION_LOST);
        }
    }

    private void requireRevision(EduYearPlan plan, YearPlanEditRequest edit) {
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
    private EduYearPlan loadViewablePlan(CurrentUser user, Long planId) {
        requirePermission(user, EduPermission.GOALS_VIEW);
        EduYearPlan plan =
                yearPlanRepository.findById(planId).orElseThrow(() -> GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
        if (!accessPolicy.viewScope(user).allows(plan.getAgeGroupId(), plan.getStatus())) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_ACCESS_DENIED);
        }
        return plan;
    }

    private EduYearPlan loadPlanForUpdate(CurrentUser user, Long planId) {
        requirePermission(user, EduPermission.GOALS_VIEW);
        EduYearPlan plan = yearPlanRepository
                .findForUpdateById(planId)
                .orElseThrow(() -> GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
        if (!accessPolicy.viewScope(user).allows(plan.getAgeGroupId(), plan.getStatus())) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_ACCESS_DENIED);
        }
        return plan;
    }

    private void requireEditable(CurrentUser user, EduYearPlan plan, Instant now) {
        if (plan.getStatus() == EduPlanStatus.PENDING_VP && !accessPolicy.isVicePrincipal(user)) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_PENDING_VP_ONLY);
        }
        requireNotLockedByOther(user, plan, now);
        // EDU-03
        if (!canEdit(user, plan, now)) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN);
        }
    }

    private void requireNotLockedByOther(CurrentUser user, EduYearPlan plan, Instant now) {
        if (isLockedByOther(user, plan, now)) {
            String editor = accountService
                    .findFullNames(List.of(plan.getEditingBy()))
                    .getOrDefault(plan.getEditingBy(), "người khác");
            throw GlobalException.of(ApiCode.YEAR_PLAN_LOCKED_BY_OTHER, editor);
        }
    }

    // EDU-10: khóa của người khác còn hạn thì người này chỉ xem
    private boolean isLockedByOther(CurrentUser user, EduYearPlan plan, Instant now) {
        return plan.getEditingBy() != null
                && !plan.isEditingBy(user.userId())
                && !plan.isEditLockExpired(now, properties.editLockTtl());
    }

    private boolean canDeleteAsVicePrincipal(CurrentUser user) {
        return accessPolicy.isVicePrincipal(user) && accessPolicy.hasPermission(user, EduPermission.GOALS_EDIT);
    }

    private boolean isOwner(CurrentUser user, EduYearPlan plan) {
        return plan.getOwnerId().equals(user.userId());
    }

    // Nháp / bị trả lại, kể cả khi đang có người sửa
    private static boolean isDraftLike(EduYearPlan plan) {
        EduPlanStatus base = plan.baseStatus();
        return base == EduPlanStatus.DRAFT || base == EduPlanStatus.REJECTED;
    }

    // EDU-03: PHT và Tổ trưởng nhóm tuổi cùng soạn khi nháp / bị trả lại (một người một lúc, EDU-10);
    // PHT khi chờ duyệt; đã ban hành thì khóa
    private boolean canEdit(CurrentUser user, EduYearPlan plan, Instant now) {
        return switch (plan.getStatus()) {
            case APPROVED -> false;
            case PENDING_VP -> accessPolicy.isVicePrincipal(user);
            case DRAFT, REJECTED, EDITING ->
                !isLockedByOther(user, plan, now)
                        && accessPolicy.hasPermission(user, EduPermission.GOALS_EDIT)
                        && (accessPolicy.isVicePrincipal(user)
                                || accessPolicy.isTeamLeaderOf(user, plan.getAgeGroupId()));
        };
    }

    // Điều kiện của bước "Chủ đề và sự kiện" + "Mục tiêu" trên UI, kiểm khi gửi / ban hành (EDU-04, EDU-05)
    private void requireReadyForReview(EduYearPlan plan) {
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
        if (goalRowRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()).stream()
                .anyMatch(row ->
                        row.getKind() == EduGoalRowKind.GOAL && row.getText().isBlank())) {
            throw GlobalException.of(ApiCode.GOAL_CONTENT_REQUIRED);
        }
        boolean hasGoal = goalRowRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()).stream()
                .anyMatch(row -> row.getKind() == EduGoalRowKind.GOAL);
        if (!hasGoal) {
            throw GlobalException.of(ApiCode.PLAN_GOAL_REQUIRED);
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

    // Lưu cả cây nội dung: lưu/cập nhật trước, xóa phần bị bỏ sau để nội dung chuyển dòng không bị cascade xóa
    // EDU-28: mục tiêu lấy từ danh mục của Bộ phải đúng nhóm tuổi của kế hoạch
    private Map<Long, CurriculumGoalRef> requireCurriculumGoals(
            EduYearPlan plan, List<YearPlanRequest.Domain> requestDomains) {
        Set<Long> ids = requestDomains.stream()
                .flatMap(d -> nullToEmpty(d.rows()).stream())
                .filter(r -> "goal".equals(r.kind()))
                .map(YearPlanRequest.Row::curriculumGoalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CurriculumGoalRef> refs = curriculumService.findGoalRefs(ids);
        for (Long id : ids) {
            CurriculumGoalRef ref = refs.get(id);
            if (ref == null || !ref.ageGroupId().equals(plan.getAgeGroupId())) {
                throw GlobalException.of(ApiCode.CURRICULUM_GOAL_INVALID);
            }
        }
        return refs;
    }

    private void saveContent(EduYearPlan plan, YearPlanRequest request) {
        Long planId = plan.getId();
        Map<String, Long> themeIdByKey = saveThemes(planId, nullToEmpty(request.themes()));
        saveBreaks(planId, nullToEmpty(request.breaks()));

        Map<Long, EduDomain> domains =
                domainRepository.findAll().stream().collect(Collectors.toMap(EduDomain::getId, Function.identity()));
        Map<Long, EduGoalRow> existingRows = goalRowRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                .collect(Collectors.toMap(EduGoalRow::getId, Function.identity()));
        Map<Long, EduGoalContent> existingContents = existingRows.isEmpty()
                ? Map.of()
                : goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(existingRows.keySet()).stream()
                        .collect(Collectors.toMap(EduGoalContent::getId, Function.identity()));

        List<YearPlanRequest.Domain> requestDomains = new ArrayList<>(nullToEmpty(request.domains()));
        Map<Long, CurriculumGoalRef> curriculum = requireCurriculumGoals(plan, requestDomains);
        for (YearPlanRequest.Domain domain : requestDomains) {
            if (!domains.containsKey(domain.domainId())) {
                throw GlobalException.of(ApiCode.EDU_DOMAIN_NOT_FOUND, domain.domainId());
            }
        }
        // EDU-09: MT đánh số liên tục cả năm theo thứ tự lĩnh vực đã cấu hình, rồi thứ tự dòng
        requestDomains.sort(Comparator.comparing(d -> domains.get(d.domainId()).getSortOrder()));

        Set<Long> keptRowIds = new HashSet<>();
        Set<Long> keptContentIds = new HashSet<>();
        List<EduContentThemeTick> ticks = new ArrayList<>();
        short goalNo = 0;
        for (YearPlanRequest.Domain domain : requestDomains) {
            short sortOrder = 0;
            for (YearPlanRequest.Row requestRow : nullToEmpty(domain.rows())) {
                String text = trimToEmpty(requestRow.text());
                if (text.isEmpty()) {
                    continue;
                }
                EduGoalRowKind kind = rowKind(requestRow.kind());
                EduGoalRow row = Optional.ofNullable(requestRow.id())
                        .map(existingRows::get)
                        .orElseGet(() -> EduGoalRow.create(planId));
                row.place(domain.domainId(), sortOrder++, kind, text, kind == EduGoalRowKind.GOAL ? ++goalNo : null);
                row.linkCurriculum(
                        requestRow.curriculumGoalId() == null || !curriculum.containsKey(requestRow.curriculumGoalId())
                                ? null
                                : requestRow.curriculumGoalId());
                row = goalRowRepository.save(row);
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
                    for (Long themeId : nullToEmpty(requestContent.themeKeys()).stream()
                            .map(themeIdByKey::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new))) {
                        ticks.add(EduContentThemeTick.of(content.getId(), themeId));
                    }
                }
            }
        }
        goalContentRepository.flush();

        // Chỉ xóa tick bị bỏ, thêm tick mới: xóa JPQL rồi merge lại sẽ bỏ sót tick còn trong persistence context
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
        goalRowRepository.deleteAllInBatch(existingRows.values().stream()
                .filter(row -> !keptRowIds.contains(row.getId()))
                .toList());
        tickRepository.saveAll(ticks.stream()
                .filter(tick -> !existingKeys.contains(tick.key()))
                .toList());
    }

    private Map<String, Long> saveThemes(Long planId, List<YearPlanRequest.Theme> requestThemes) {
        Map<Long, EduTheme> existing = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                .collect(Collectors.toMap(EduTheme::getId, Function.identity()));
        Map<String, Long> idByKey = new LinkedHashMap<>();
        Set<Long> kept = new HashSet<>();
        short sortOrder = 0;
        for (YearPlanRequest.Theme requestTheme : requestThemes) {
            if (idByKey.containsKey(requestTheme.key())) {
                throw GlobalException.of(ApiCode.THEME_KEY_DUPLICATE, requestTheme.key());
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
            item = breakRepository.save(item);
            kept.add(item.getId());
        }
        breakRepository.deleteAllInBatch(existing.values().stream()
                .filter(b -> !kept.contains(b.getId()))
                .toList());
    }

    // UI ghi 1 dòng "Phó hiệu trưởng chỉnh sửa" mỗi lượt sửa; lưu nhiều lần nên không ghi lặp khi dòng cuối đã là nó
    private void recordVicePrincipalEdit(CurrentUser user, EduYearPlan plan, Instant now) {
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
            EduYearPlan plan,
            String action,
            String label,
            CurrentUser actor,
            String actorRoleLabel,
            String tone,
            String note,
            Instant now) {
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        eventRepository.save(EduPlanEvent.of(
                plan.getId(), action, label, actor.userId(), actor.fullName(), actorRoleLabel, tone, cleanNote, now));
    }

    // Hiện hành = phiên bản đã ban hành có số lớn nhất của chuỗi (spec 004 FR-013)
    private static Optional<EduYearPlan> currentVersion(List<EduYearPlan> chain) {
        return chain.stream()
                .filter(p -> p.getStatus() == EduPlanStatus.APPROVED)
                .max(Comparator.comparingInt(EduYearPlan::getVersionNo));
    }

    // Chép cả cây sang phiên bản mới, giữ lineage; dấu tích ánh xạ theo id mới của nội dung và chủ đề
    private void copyContent(Long sourceId, Long targetId) {
        Map<Long, Long> themeIds = new HashMap<>();
        for (EduTheme theme : themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId)) {
            themeIds.put(
                    theme.getId(),
                    themeRepository.save(EduTheme.copyFor(targetId, theme)).getId());
        }
        breakRepository.saveAll(breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId).stream()
                .map(b -> EduBreak.copyFor(targetId, b))
                .toList());
        Map<Long, Long> rowIds = new HashMap<>();
        for (EduGoalRow row : goalRowRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId)) {
            rowIds.put(
                    row.getId(),
                    goalRowRepository.save(EduGoalRow.copyFor(targetId, row)).getId());
        }
        Map<Long, Long> contentIds = new HashMap<>();
        if (!rowIds.isEmpty()) {
            for (EduGoalContent content :
                    goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(rowIds.keySet())) {
                EduGoalContent copy = EduGoalContent.copyFor(rowIds.get(content.getGoalRowId()), content);
                contentIds.put(content.getId(), goalContentRepository.save(copy).getId());
            }
        }
        goalContentRepository.flush();
        if (!themeIds.isEmpty()) {
            tickRepository.saveAll(tickRepository.findAllByThemeIdIn(themeIds.keySet()).stream()
                    .filter(t -> contentIds.containsKey(t.getContentId()))
                    .map(t -> EduContentThemeTick.of(contentIds.get(t.getContentId()), themeIds.get(t.getThemeId())))
                    .toList());
        }
    }

    private YearPlanChangesResponse compare(EduYearPlan from, EduYearPlan plan) {
        if (!from.getSchoolYearId().equals(plan.getSchoolYearId())
                || !from.getAgeGroupId().equals(plan.getAgeGroupId())
                || from.getId().equals(plan.getId())) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_VERSION_COMPARE_INVALID);
        }
        return YearPlanVersionDiff.compare(snapshot(from), snapshot(plan));
    }

    private YearPlanVersionDiff.Snapshot snapshot(EduYearPlan plan) {
        List<EduTheme> themes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
        Map<Long, Long> themeLineage = themes.stream().collect(Collectors.toMap(EduTheme::getId, EduTheme::lineage));
        List<EduGoalRow> goals = goalRowRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()).stream()
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

    private YearPlanDetailResponse toDetail(CurrentUser user, EduYearPlan plan) {
        Long planId = plan.getId();
        List<EduGoalRow> rows = goalRowRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId);
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
        Map<Long, CurriculumGoalRef> curriculum = curriculumService.findGoalRefs(rows.stream()
                .map(EduGoalRow::getCurriculumGoalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        // UI luôn hiện đủ các lĩnh vực đang dùng; lĩnh vực đã tắt vẫn hiện nếu kế hoạch còn dòng của nó
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
                                        r.getCurriculumGoalId() == null
                                                ? null
                                                : curriculum.get(r.getCurriculumGoalId())))
                                .toList()))
                .toList();

        List<EduYearPlan> chain = yearPlanRepository.findAllBySchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                plan.getSchoolYearId(), plan.getAgeGroupId());
        Long currentId = currentVersion(chain).map(EduYearPlan::getId).orElse(null);
        EduPlanAccessPolicy.ViewScope scope = accessPolicy.viewScope(user);
        List<EduYearPlan> visibleChain = chain.stream()
                .filter(p -> scope.allows(p.getAgeGroupId(), p.getStatus()))
                .toList();
        List<EduPlanEvent> events = eventRepository.findAllByYearPlanIdOrderByOccurredAtAscIdAsc(planId);
        String lastReturnNote = plan.baseStatus() != EduPlanStatus.REJECTED
                ? null
                : events.stream()
                        .filter(e -> ACTION_RETURN.equals(e.getAction()))
                        .reduce((first, second) -> second)
                        .map(EduPlanEvent::getNote)
                        .orElse(null);
        Map<Long, String> names = accountService.findFullNames(Stream.concat(
                        Stream.of(plan.getOwnerId(), plan.getSentBy(), plan.getIssuedBy(), plan.getEditingBy()),
                        visibleChain.stream().flatMap(p -> Stream.of(p.getOwnerId(), p.getIssuedBy())))
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
                plan.getStatus(),
                mapper.toOwner(plan, names),
                mapper.toActor(plan.getSentBy(), plan.getSentAt(), names),
                mapper.toActor(plan.getIssuedBy(), plan.getIssuedAt(), names),
                mapper.toActor(plan.getEditingBy(), plan.getEditingSince(), names),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                domains,
                themes.stream().map(mapper::toTheme).toList(),
                breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId).stream()
                        .map(mapper::toBreak)
                        .toList(),
                events.stream().map(mapper::toHistory).toList(),
                lastReturnNote,
                permissionsOf(user, plan, chain, currentId),
                plan.getContentRevision(),
                plan.getEditingAt() == null ? null : plan.getEditingAt().plus(properties.editLockTtl()),
                plan.getVersionNo(),
                plan.getSourcePlanId(),
                plan.getId().equals(currentId),
                visibleChain.stream()
                        .map(p -> new YearPlanDetailResponse.VersionSummary(
                                p.getId(),
                                p.getVersionNo(),
                                p.getCode(),
                                p.getStatus(),
                                names.get(p.getOwnerId()),
                                p.getCreatedAt(),
                                p.getIssuedBy() == null ? null : names.get(p.getIssuedBy()),
                                p.getIssuedAt(),
                                p.getId().equals(currentId)))
                        .toList());
    }

    private YearPlanDetailResponse.Permissions permissionsOf(
            CurrentUser user, EduYearPlan plan, List<EduYearPlan> chain, Long currentId) {
        Instant now = clock.instant();
        boolean vicePrincipal = accessPolicy.isVicePrincipal(user);
        boolean free = !isLockedByOther(user, plan, now);
        boolean draftLike = isDraftLike(plan);
        boolean pending = plan.getStatus() == EduPlanStatus.PENDING_VP;
        return new YearPlanDetailResponse.Permissions(
                canEdit(user, plan, now),
                free && draftLike && accessPolicy.isTeamLeaderOf(user, plan.getAgeGroupId()),
                free && vicePrincipal && (pending || (draftLike && isOwner(user, plan))),
                free && vicePrincipal && pending,
                free && plan.baseStatus() == EduPlanStatus.DRAFT && canDeleteAsVicePrincipal(user),
                plan.getId().equals(currentId)
                        && chain.stream().allMatch(p -> p.getStatus() == EduPlanStatus.APPROVED)
                        && accessPolicy.canCreate(user, plan.getAgeGroupId()));
    }

    private String actorTitle(CurrentUser user) {
        return accessPolicy.isVicePrincipal(user) ? TITLE_VICE_PRINCIPAL : TITLE_TEAM_LEADER;
    }

    // Mã như UI: KHNH-<2 số cuối năm đầu><2 số cuối năm sau>-<chữ đầu tên nhóm tuổi, tối đa 3>, vd. KHNH-2627-MGN
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

    // Loại dòng do client gửi sai: 400 thay vì 500 (IllegalArgumentException của enum)
    private static EduGoalRowKind rowKind(String code) {
        try {
            return EduGoalRowKind.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw GlobalException.withCause(ApiCode.INVALID_VALUE, ex, "loại dòng mục tiêu");
        }
    }
}
