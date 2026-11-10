package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.EduWorkflowPolicyRequest;
import com.carenest.dto.eduplan.EduWorkflowPolicyResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduDocumentAuthor;
import com.carenest.entity.eduplan.EduSettings;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduDocumentAuthorRepository;
import com.carenest.repository.eduplan.EduSettingsRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Approval;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Evaluation;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Role;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.RolePolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.EduClassDocStatus;
import com.carenest.utils.enums.EduPlanStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
public class EduWorkflowServiceImpl implements EduWorkflowService {

    private static final String LOG_PREFIX = "[EduWorkflowServiceImpl]";
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter LEGACY_APPROVAL_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final EduSettingsRepository settingsRepository;
    private final EduPlanRepository yearPlanRepository;
    private final EduTeamUpdateGenerator teamUpdates;
    private final EduClassDocRepository classDocRepository;
    private final EduDocumentAuthorRepository authorRepository;
    private final StaffAssignmentService staffAssignmentService;
    private final AccountService accountService;
    private final EduPlanAccessPolicy accessPolicy;
    private final YearPlanPublicationValidator publicationValidator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final com.carenest.repository.eduplan.EduPlanEventRepository eventRepository;
    private final com.carenest.service.organization.ClassroomService classroomService;

    private final EduWorkflowPolicy evaluator = new EduWorkflowPolicy();

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public RolePolicy loadPolicy(String docType) {
        String logPrefix = LOG_PREFIX + "|loadPolicy|docType=" + docType;
        log.info("{}|START", logPrefix);
        try {
            RolePolicy policy = parsePolicyNode(loadSettingsPolicyNode(), (EduClassDoc.isRoot(docType) || "week".equals(docType) || "day".equals(docType) ? EduClassDoc.policyType(docType) : docType));
            var result = policy;
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, RolePolicy> loadAllPolicies() {
        String logPrefix = LOG_PREFIX + "|loadAllPolicies";
        log.info("{}|START", logPrefix);
        try {
            JsonNode root = loadSettingsPolicyNode();
            Map<String, RolePolicy> map = Map.of(
                    "year", parsePolicyNode(root, "year"),
                    "schoolYear", parsePolicyNode(root, "schoolYear"),
                    "branch", parsePolicyNode(root, "branch"),
                    "theme", parsePolicyNode(root, "theme"));
            var result = map;
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public long getPolicyRevision() {
        String logPrefix = LOG_PREFIX + "|getPolicyRevision";
        log.info("{}|START", logPrefix);
        try {
            var result = settingsRepository
                    .findById((short) 1)
                    .map(EduSettings::getWorkflowRevision)
                    .orElse(1L);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Evaluation evaluateYearPlan(EduPlan plan, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|evaluateYearPlan";
        log.info("{}|START", logPrefix);
        try {
            RolePolicy policy = loadPolicy(plan.workflowKey());
            int round = plan.getReviewRound();
            List<Approval> approvals = parseApprovals(plan.getApprovals());
            Set<Long> excludedAuthors = getAuthorUserIdsForYearPlan(plan.getId());
            Map<Role, Set<Long>> candidates = resolveActiveCandidates(policy, plan.getAgeGroupId(), null, date);
            var result = evaluator.evaluate(policy, round, approvals, excludedAuthors, candidates);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Evaluation evaluateClassDoc(EduClassDoc doc, Long ageGroupId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|evaluateClassDoc";
        log.info("{}|START", logPrefix);
        try {
            String type = EduClassDoc.policyType(doc.getKind());
            RolePolicy policy = loadPolicy(type);
            int round = doc.getReviewRound();
            List<Approval> approvals = parseApprovals(doc.getApprovals());
            Set<Long> excludedAuthors = getUnionAuthorUserIdsForDossier(doc);
            Map<Role, Set<Long>> candidates = resolveActiveCandidates(policy, ageGroupId, doc.getClassroomId(), date);
            var result = evaluator.evaluate(policy, round, approvals, excludedAuthors, candidates);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<Long, Evaluation> evaluateClassDocs(java.util.Collection<EduClassDoc> docs, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|evaluateClassDocs";
        log.info("{}|START", logPrefix);
        try {
            var assignments = staffAssignmentService.findActiveEducationReviewerAssignments(date);
            var activeIds = accountService.findActiveUserIds(assignments.keySet());
            var policies = loadAllPolicies();
            var classAuthors = batchClassDocAuthors(List.copyOf(docs));
            var classInfos = batchClassroomInfos(List.copyOf(docs));
            Map<Long, Evaluation> results = new java.util.HashMap<>();
            for (EduClassDoc doc : docs) {
                if (doc.getStatus() != EduClassDocStatus.REVIEW) continue;
                var policy = policies.get(EduClassDoc.policyType(doc.getKind()));
                var classroom = classInfos.get(doc.getClassroomId());
                if (classroom == null) {
                    classroom = classroomService
                            .findClassroomInfo(doc.getClassroomId())
                            .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_NOT_FOUND));
                }
                results.put(
                        doc.getId(),
                        evaluator.evaluate(
                                policy,
                                doc.getReviewRound(),
                                parseApprovals(doc.getApprovals()),
                                classAuthors.get(doc.getId()),
                                candidatesForClassroom(policy, null, classroom, assignments, activeIds)));
            }
            log.info("{}|END", logPrefix);
            return Map.copyOf(results);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void recordAuthor(Long yearPlanId, Long classDocId, Long userId, String origin, Instant now) {
        String logPrefix = LOG_PREFIX + "|recordAuthor|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            if (yearPlanId != null) {
                Optional<EduDocumentAuthor> existing = authorRepository.findByYearPlanIdAndUserId(yearPlanId, userId);
                if (existing.isPresent()) {
                    existing.get().touch(now);
                    authorRepository.save(existing.get());
                } else {
                    var author=EduDocumentAuthor.forYearPlan(yearPlanId,userId,origin,now);
                    author.initializeOwner(yearPlanRepository.findById(yearPlanId).orElseThrow());
                    authorRepository.save(author);
                }
            } else if (classDocId != null) {
                Optional<EduDocumentAuthor> existing = authorRepository.findByClassDocIdAndUserId(classDocId, userId);
                if (existing.isPresent()) {
                    existing.get().touch(now);
                    authorRepository.save(existing.get());
                } else {
                    authorRepository.save(EduDocumentAuthor.forClassDoc(classDocId, userId, origin, now));
                }
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Set<Long> getAuthorUserIdsForYearPlan(Long yearPlanId) {
        String logPrefix = LOG_PREFIX + "|getAuthorUserIdsForYearPlan";
        log.info("{}|START", logPrefix);
        try {
            Set<Long> ids = new HashSet<>(authorRepository.findUserIdsByYearPlanId(yearPlanId));
            yearPlanRepository.findById(yearPlanId).ifPresent(p -> ids.add(p.getOwnerId()));
            var result = Set.copyOf(ids);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Set<Long> getUnionAuthorUserIdsForDossier(EduClassDoc doc) {
        String logPrefix = LOG_PREFIX + "|getUnionAuthorUserIdsForDossier";
        log.info("{}|START", logPrefix);
        try {
            Set<Long> docIds = new HashSet<>();
            docIds.add(doc.getId());
            Set<Long> userIds = new HashSet<>();
            userIds.add(doc.getCreatedBy());

            if (doc.getThemeId() != null) {
                Long themeId = doc.getThemeId();
                if (themeId != null) {
                    List<EduClassDoc> siblingDocs = classDocRepository.findAllByClassroomIdAndYearPlanId(
                            doc.getClassroomId(), doc.getYearPlanId());
                    for (EduClassDoc d : siblingDocs) {
                        if (Objects.equals(d.getThemeId(), themeId)) {
                            docIds.add(d.getId());
                            userIds.add(d.getCreatedBy());
                        }
                    }
                }
            }

            userIds.addAll(authorRepository.findUserIdsByClassDocIdIn(docIds));
            var result = Set.copyOf(userIds);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<Long, Set<Long>> getUnionAuthorUserIdsForDossiers(java.util.Collection<EduClassDoc> docs) {
        String logPrefix = LOG_PREFIX + "|getUnionAuthorUserIdsForDossiers|count=" + docs.size();
        log.info("{}|START", logPrefix);
        try {
            var result = batchClassDocAuthors(List.copyOf(docs));
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<Role, Set<Long>> resolveActiveCandidates(
            RolePolicy policy, Long ageGroupId, Long classroomId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|resolveActiveCandidates";
        log.info("{}|START", logPrefix);
        try {
            var assignments = staffAssignmentService.findActiveEducationReviewerAssignments(date);
            var result = candidatesFromAssignments(
                    policy,
                    ageGroupId,
                    classroomId,
                    assignments,
                    accountService.findActiveUserIds(assignments.keySet()));
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Map<Role, Set<Long>> candidatesFromAssignments(
            RolePolicy policy,
            Long ageGroupId,
            Long classroomId,
            Map<Long, List<StaffAssignmentSummaryResponse>> assignmentsByUserId,
            Set<Long> activeUserIds) {
        var classroom = classroomId == null
                ? null
                : classroomService
                        .findClassroomInfo(classroomId)
                        .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_NOT_FOUND));
        return candidatesForClassroom(policy, ageGroupId, classroom, assignmentsByUserId, activeUserIds);
    }

    private Map<Role, Set<Long>> candidatesForClassroom(
            RolePolicy policy,
            Long ageGroupId,
            ClassroomInfoResponse classroom,
            Map<Long, List<StaffAssignmentSummaryResponse>> assignmentsByUserId,
            Set<Long> activeUserIds) {
        Long scopedAgeGroup = classroom == null ? ageGroupId : classroom.ageGroupId();
        Map<Role, Set<Long>> result = new EnumMap<>(Role.class);
        for (Role role : policy.reviewers()) {
            Set<Long> matchingIds = new HashSet<>();
            for (Long userId : activeUserIds) {
                List<StaffAssignmentSummaryResponse> list = assignmentsByUserId.getOrDefault(userId, List.of());
                boolean matches = list.stream().anyMatch(a -> {
                    if (role == Role.PRINCIPAL) {
                        return a.staffRole() == StaffRole.PRINCIPAL;
                    }
                    if (role == Role.VICE_PRINCIPAL) {
                        return a.staffRole() == StaffRole.VICE_PRINCIPAL
                                && (classroom == null || Objects.equals(a.campusId(), classroom.campusId()));
                    }
                    if (role == Role.TEAM_LEADER) {
                        return a.staffRole() == StaffRole.AGE_GROUP_TEAM_LEADER
                                && scopedAgeGroup != null
                                && Objects.equals(a.ageGroupId(), scopedAgeGroup);
                    }
                    return false;
                });
                if (matches) {
                    matchingIds.add(userId);
                }
            }
            result.put(role, Set.copyOf(matchingIds));
        }
        return result;
    }

    @Override
    @Transactional
    public void reconcilePending(Long userId, Instant now) {
        String logPrefix = LOG_PREFIX + "|reconcilePending|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            // 1. Exclusive lock on EduSettings
            EduSettings settings =
                    settingsRepository.findForUpdate().orElseThrow(() -> GlobalException.of(ApiCode.INTERNAL_ERROR));
            long revision = settings.getWorkflowRevision();
            LocalDate today = LocalDate.ofInstant(now, clock.getZone());

            var assignmentSnapshot = staffAssignmentService.findActiveEducationReviewerAssignments(today);
            var activeSnapshot = accountService.findActiveUserIds(assignmentSnapshot.keySet());

            // 2. Year plans lock in ascending ID order
            List<EduPlan> pendingYears =
                    yearPlanRepository.findAllByStatusInForUpdateOrderByIdAsc(List.of(EduPlanStatus.REVIEW));
            for (EduPlan plan : pendingYears) {
                RolePolicy yearPolicy = loadPolicy(plan.workflowKey());
                Evaluation eval = evaluator.evaluate(
                        yearPolicy,
                        plan.getReviewRound(),
                        parseApprovals(plan.getApprovals()),
                        getAuthorUserIdsForYearPlan(plan.getId()),
                        candidatesFromAssignments(
                                yearPolicy, plan.getAgeGroupId(), null, assignmentSnapshot, activeSnapshot));
                if (!eval.complete() && Objects.equals(plan.getAppliedPolicyRevision(), revision)) continue;
                if (eval.complete()) {
                    publicationValidator.validateForPublication(plan);
                    List<Approval> approvals = parseApprovals(plan.getApprovals());
                    RolePolicy policy = loadPolicy(plan.workflowKey());
                    String printSnap = buildPrintSnapshot(
                            plan.workflowKey(),
                            revision,
                            approvals,
                            policy,
                            now,
                            getAuthorUserIdsForYearPlan(plan.getId()));
                    var vpApproval = approvals.stream()
                            .filter(a -> a.round() == plan.getReviewRound()
                                    && a.roleKey() == Role.VICE_PRINCIPAL
                                    && !getAuthorUserIdsForYearPlan(plan.getId())
                                            .contains(a.userId()))
                            .findFirst()
                            .orElse(null);
                    plan.projectLegacyIssuer(
                            vpApproval == null ? null : vpApproval.userId(),
                            vpApproval == null ? null : vpApproval.at());
                    plan.updateWorkflowState(
                            EduPlanStatus.APPROVED,
                            plan.getReviewRound(),
                            plan.getApprovals(),
                            revision,
                            settings.getWorkflowPolicy(),
                            printSnap,
                            approvals.stream()
                                    .filter(a -> a.round() == plan.getReviewRound()
                                            && a.roleKey() == eval.completionRole()
                                            && !getAuthorUserIdsForYearPlan(plan.getId())
                                                    .contains(a.userId()))
                                    .map(Approval::userId)
                                    .filter(Objects::nonNull)
                                    .findFirst()
                                    .orElseThrow(),
                            "POLICY_CHANGE",
                            now,
                            now);
                } else {
                    plan.updateWorkflowState(
                            EduPlanStatus.REVIEW,
                            plan.getReviewRound(),
                            plan.getApprovals(),
                            revision,
                            plan.getCompletionPolicy(),
                            plan.getPrintSnapshot(),
                            plan.getPublishedBy(),
                            plan.getCompletionMode(),
                            plan.getCompletedAt(),
                            now);
                }
                plan.recordSave(null);
                String action = eval.complete() ? "COMPLETED_BY_POLICY" : "POLICY_RECONCILED";
                var event = com.carenest.entity.eduplan.EduPlanEvent.of(
                        plan.getId(),
                        action,
                        action,
                        userId,
                        "",
                        null,
                        "",
                        policyAudit(plan.getReviewRound(), revision).toString(),
                        now);
                event.initializeOwner(plan);
                eventRepository.save(event);
                yearPlanRepository.save(plan);
                if(eval.complete()) teamUpdates.updateAfterPublication(plan,now);
            }

            // 3. Class docs lock in ascending ID order
            List<EduClassDoc> pendingClassDocs =
                    classDocRepository.findAllByStatusInForUpdateOrderByIdAsc(List.of(EduClassDocStatus.REVIEW));
            var classAuthors = batchClassDocAuthors(pendingClassDocs);
            var classInfos = batchClassroomInfos(pendingClassDocs);
            var policies = loadAllPolicies();
            for (EduClassDoc doc : pendingClassDocs) {
                RolePolicy currentPolicy = policies.get(EduClassDoc.policyType(doc.getKind()));
                Set<Long> excludedAuthors = classAuthors.get(doc.getId());
                ClassroomInfoResponse classroom = classInfos.get(doc.getClassroomId());
                if (classroom == null) {
                    classroom = classroomService
                            .findClassroomInfo(doc.getClassroomId())
                            .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_DOC_NOT_FOUND));
                }
                Evaluation eval = evaluator.evaluate(
                        currentPolicy,
                        doc.getReviewRound(),
                        parseApprovals(doc.getApprovals()),
                        excludedAuthors,
                        candidatesForClassroom(currentPolicy, null, classroom, assignmentSnapshot, activeSnapshot));
                if (!eval.complete() && Objects.equals(doc.getAppliedPolicyRevision(), revision)) continue;
                if (eval.complete()) {
                    List<Approval> approvals = parseApprovals(doc.getApprovals());
                    String printSnap =
                            buildPrintSnapshot(doc.getKind(), revision, approvals, currentPolicy, now, excludedAuthors);
                    doc.updateWorkflowState(
                            EduClassDocStatus.APPROVED,
                            doc.getReviewRound(),
                            doc.getApprovals(),
                            appendPolicyHistory(doc, eval.complete(), userId, revision, now),
                            revision,
                            settings.getWorkflowPolicy(),
                            printSnap,
                            "POLICY_CHANGE",
                            now,
                            userId != null ? userId : doc.getUpdatedBy());
                } else {
                    doc.updateWorkflowState(
                            EduClassDocStatus.REVIEW,
                            doc.getReviewRound(),
                            doc.getApprovals(),
                            appendPolicyHistory(doc, eval.complete(), userId, revision, now),
                            revision,
                            doc.getCompletionPolicy(),
                            doc.getPrintSnapshot(),
                            doc.getCompletionMode(),
                            doc.getCompletedAt(),
                            userId != null ? userId : doc.getUpdatedBy());
                }
                doc.touchDossier(now);
                classDocRepository.save(doc);
            }

            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Map<Long, Set<Long>> batchClassDocAuthors(List<EduClassDoc> docs) {
        if (docs.isEmpty()) return Map.of();
        Set<Long> classrooms =
                docs.stream().map(EduClassDoc::getClassroomId).collect(java.util.stream.Collectors.toSet());
        List<EduClassDoc> siblings = classDocRepository.findAllByClassroomIdInOrderByIdAsc(classrooms);
        Map<Long, EduClassDoc> byId = new java.util.HashMap<>();
        siblings.forEach(d -> byId.put(d.getId(), d));
        docs.forEach(d -> byId.put(d.getId(), d));
        Map<Long, Set<Long>> authors = new java.util.HashMap<>();
        byId.values()
                .forEach(d -> authors.computeIfAbsent(d.getId(), ignored -> new HashSet<>())
                        .add(d.getCreatedBy()));
        for (EduDocumentAuthor author : authorRepository.findAllByClassDocIdIn(byId.keySet())) {
            authors.computeIfAbsent(author.getClassDocId(), ignored -> new HashSet<>())
                    .add(author.getUserId());
        }
        Map<Long, Set<Long>> result = new java.util.HashMap<>();
        for (EduClassDoc doc : docs) {
            Set<Long> union = new HashSet<>(authors.get(doc.getId()));
            if (doc.getThemeId() != null) {
                for (EduClassDoc sibling : byId.values()) {
                    if (Objects.equals(doc.getClassroomId(), sibling.getClassroomId())
                            && Objects.equals(doc.getYearPlanId(), sibling.getYearPlanId())
                            && Objects.equals(doc.getThemeId(), sibling.getThemeId())) {
                        union.addAll(authors.get(sibling.getId()));
                    }
                }
            }
            result.put(doc.getId(), Set.copyOf(union));
        }
        return result;
    }

    private Map<Long, ClassroomInfoResponse> batchClassroomInfos(List<EduClassDoc> docs) {
        if (docs.isEmpty()) return Map.of();
        Set<Long> planIds = docs.stream()
                .map(EduClassDoc::getYearPlanId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> years = new HashSet<>();
        yearPlanRepository.findAllById(planIds).forEach(plan -> years.add(plan.getSchoolYearId()));
        Map<Long, ClassroomInfoResponse> result = new java.util.HashMap<>();
        for (Long year : years) {
            for (ClassroomInfoResponse classroom : classroomService.findYearClassrooms(year)) {
                result.put(classroom.id(), classroom);
            }
        }
        return result;
    }

    private ObjectNode policyAudit(int round, long revision) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("round", round);
        event.put("policyRevision", revision);
        return event;
    }

    private String appendPolicyHistory(EduClassDoc doc, boolean complete, Long actor, long revision, Instant now) {
        ArrayNode history = (ArrayNode) objectMapper.readTree(doc.getHistory());
        ObjectNode event = policyAudit(doc.getReviewRound(), revision);
        event.put("action", complete ? "COMPLETED_BY_POLICY" : "POLICY_RECONCILED");
        if (actor != null) event.put("actorId", actor);
        else event.putNull("actorId");
        event.put("at", now.toString());
        history.add(event);
        return history.toString();
    }

    @Override
    @Transactional
    public void activateLegacyPending(Instant now) {
        String logPrefix = LOG_PREFIX + "|activateLegacyPending";
        log.info("{}|START", logPrefix);
        try {
            EduSettings settings = settingsRepository.findForUpdate().orElse(null);
            if (settings == null) {
                log.info("{}|NO_SETTINGS|Skipping activation", logPrefix);
                return;
            }

            // 1. Reconcile any pending docs atomically
            reconcilePending(null, now);

            // 2. Backfill approved year plans without print snapshot (idempotent)
            List<EduPlan> approvedYears =
                    yearPlanRepository.findAllByStatusInForUpdateOrderByIdAsc(List.of(EduPlanStatus.APPROVED));
            for (EduPlan plan : approvedYears) {
                boolean modified = false;
                if (plan.getPrintSnapshot() == null || plan.getPrintSnapshot().isBlank()) {
                    String snapshot = legacySnapshot(
                            "year",
                            plan.getUpdatedAt() != null ? plan.getUpdatedAt() : now,
                            legacyYearApprovals(plan),
                            getAuthorUserIdsForYearPlan(plan.getId()),
                            null);
                    plan.backfillLegacySnapshot(snapshot, "LEGACY_BASELINE", now);
                    modified = true;
                } else if (plan.getAuthorCoverage() == null) {
                    plan.backfillLegacySnapshot(plan.getPrintSnapshot(), "LEGACY_BASELINE", now);
                    modified = true;
                }
                if (modified) {
                    yearPlanRepository.save(plan);
                }
                teamUpdates.updateAfterPublication(plan, now);
            }

            // 3. Backfill approved class docs without print snapshot (idempotent)
            List<EduClassDoc> approvedClassDocs =
                    classDocRepository.findAllByStatusInForUpdateOrderByIdAsc(List.of(EduClassDocStatus.APPROVED));
            for (EduClassDoc doc : approvedClassDocs) {
                boolean modified = false;
                if (doc.getPrintSnapshot() == null || doc.getPrintSnapshot().isBlank()) {
                    String snapshot = legacySnapshot(
                            doc.getKind(),
                            doc.getUpdatedAt() != null ? doc.getUpdatedAt() : now,
                            parseApprovals(doc.getApprovals()),
                            getUnionAuthorUserIdsForDossier(doc),
                            doc.getSignChain());
                    doc.backfillLegacySnapshot(snapshot, "LEGACY_BASELINE", null);
                    modified = true;
                } else if (doc.getAuthorCoverage() == null) {
                    doc.backfillLegacySnapshot(doc.getPrintSnapshot(), "LEGACY_BASELINE", null);
                    modified = true;
                }
                if (modified) {
                    classDocRepository.save(doc);
                }
            }

            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<Approval> legacyYearApprovals(EduPlan plan) {
        var approvals = new ArrayList<>(parseApprovals(plan.getApprovals()));
        if (plan.getIssuedBy() != null && approvals.stream().noneMatch(a -> a.roleKey() == Role.VICE_PRINCIPAL)) {
            String name =
                    accountService.findFullNames(Set.of(plan.getIssuedBy())).getOrDefault(plan.getIssuedBy(), "");
            approvals.add(new Approval(
                    plan.getIssuedBy(), name, Role.VICE_PRINCIPAL, plan.getIssuedAt(), "", plan.getReviewRound()));
        }
        return approvals;
    }

    private String legacySnapshot(String type, Instant at, List<Approval> approvals, Set<Long> authors, String chain) {
        String key = "year".equals(type) ? "year" : "branch".equals(type) || "semester".equals(type) ? "branch" : "week";
        JsonNode legacy = settingsRepository
                .findById(EduSettings.SINGLETON_ID)
                .map(EduSettings::getLegacySigners)
                .map(objectMapper::readTree)
                .orElse(null);
        List<Role> reviewers =
                parseRoles(chain == null ? legacy == null ? null : legacy.get(key) : objectMapper.readTree(chain));
        RolePolicy policy = reviewers.isEmpty() ? null : new RolePolicy(List.of(Role.TEACHER), reviewers);
        ObjectNode snapshot =
                (ObjectNode) objectMapper.readTree(buildPrintSnapshot(type, 1L, approvals, policy, at, authors));
        snapshot.put("origin", "LEGACY_BASELINE");
        return snapshot.toString();
    }

    @Override
    public void validateClassDocVersion(EduClassDoc doc, Long expectedVersion, Long expectedRootVersion) {
        String logPrefix = LOG_PREFIX + "|validateClassDocVersion";
        log.info("{}|START", logPrefix);
        try {
            if (expectedVersion != null) {
                if (expectedVersion == -1L) {
                    if (doc != null && doc.getId() != null) {
                        throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
                    }
                } else if (doc == null || !Objects.equals(doc.getVersion(), expectedVersion)) {
                    throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
                }
            }
            if (expectedRootVersion != null
                    && doc != null
                    && "week".equals(doc.getKind())
                    && doc.getThemeId() != null) {
                List<EduClassDoc> siblings =
                        classDocRepository.findAllByClassroomIdAndYearPlanId(doc.getClassroomId(), doc.getYearPlanId());
                EduClassDoc rootTheme = siblings.stream()
                        .filter(d -> "theme".equals(d.getKind()) && Objects.equals(d.getThemeId(), doc.getThemeId()))
                        .findFirst()
                        .orElse(null);
                if (rootTheme != null && !Objects.equals(rootTheme.getVersion(), expectedRootVersion)) {
                    throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
                }
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private JsonNode loadSettingsPolicyNode() {
        return settingsRepository
                .findById((short) 1)
                .map(EduSettings::getWorkflowPolicy)
                .map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception ex) {
                        throw new IllegalStateException("Cannot read stored workflow policy", ex);
                    }
                })
                .orElseThrow(() -> new IllegalStateException("Missing stored workflow policy"));
    }

    private RolePolicy parsePolicyNode(JsonNode root, String docType) {
        if ("schoolYear".equals(docType) && root != null && !root.has(docType))
            return new RolePolicy(List.of(Role.VICE_PRINCIPAL), List.of(Role.PRINCIPAL));
        if (root == null || !root.has(docType)) {
            throw new IllegalStateException("Missing document workflow policy");
        }
        JsonNode typeNode = root.get(docType);
        List<Role> creators = parseRoles(typeNode.get("creators"));
        List<Role> reviewers = parseRoles(typeNode.get("reviewers"));
        return new RolePolicy(creators, reviewers);
    }

    private List<Role> parseRoles(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<Role> roles = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            try {
                roles.add(Role.valueOf(item.asText()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Invalid stored workflow role", ex);
            }
        }
        return roles;
    }

    @Override
    public List<Approval> parseApprovals(String approvalsJson) {
        String logPrefix = LOG_PREFIX + "|parseApprovals";
        log.info("{}|START", logPrefix);
        try {
            if (approvalsJson == null || approvalsJson.isBlank()) {
                var result = List.<Approval>of();
                log.info("{}|END", logPrefix);
                return result;
            }
            try {
                JsonNode tree = objectMapper.readTree(approvalsJson);
                if (!tree.isArray()) {
                    throw new IllegalStateException("Invalid stored approval history");
                }
                List<Approval> result = new ArrayList<>();
                for (JsonNode item : tree) {
                    Long userId = item.has("userId") && !item.get("userId").isNull()
                            ? item.get("userId").asLong()
                            : null;
                    String name = item.has("name") && !item.get("name").isNull()
                            ? item.get("name").asText()
                            : "";
                    String roleKeyStr =
                            item.has("roleKey") && !item.get("roleKey").isNull()
                                    ? item.get("roleKey").asText()
                                    : "";
                    Role roleKey = Role.valueOf(roleKeyStr);
                    Instant at = item.has("at") && !item.get("at").isNull()
                            ? parseApprovalTime(item.get("at").asText())
                            : null;
                    String note = item.has("note") && !item.get("note").isNull()
                            ? item.get("note").asText()
                            : "";
                    int round = item.has("round") && !item.get("round").isNull()
                            ? item.get("round").asInt()
                            : 0;
                    result.add(new Approval(userId, name, roleKey, at, note, round));
                }
                log.info("{}|END", logPrefix);
                return result;
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot read stored approval history", ex);
            }
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Instant parseApprovalTime(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value, LEGACY_APPROVAL_TIME)
                    .atZone(SCHOOL_ZONE)
                    .toInstant();
        }
    }

    @Override
    public String buildPrintSnapshot(
            String docType, long revision, List<Approval> approvals, RolePolicy policy, Instant now) {
        String logPrefix = LOG_PREFIX + "|buildPrintSnapshot";
        log.info("{}|START", logPrefix);
        try {
            var result = buildPrintSnapshot(docType, revision, approvals, policy, now, Set.of());
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public String buildPrintSnapshot(
            String docType,
            long revision,
            List<Approval> approvals,
            RolePolicy policy,
            Instant now,
            Set<Long> authorIds) {
        String logPrefix = LOG_PREFIX + "|buildPrintSnapshot";
        log.info("{}|START", logPrefix);
        try {
            ObjectNode snapshot = objectMapper.createObjectNode();
            snapshot.put("schemaVersion", 1);
            snapshot.put("capturedAt", now.toString());
            snapshot.put("origin", "PUBLICATION");
            snapshot.put("documentType", docType);
            snapshot.put("policyRevision", revision);
            ArrayNode slots = snapshot.putArray("slots");
            ObjectNode creator = slots.addObject();
            creator.put("roleKey", "CREATOR");
            creator.put("label", "Người soạn");
            ArrayNode authorNames = creator.putArray("names");
            Map<Long, String> knownAuthors = accountService.findFullNames(authorIds);
            authorIds.stream()
                    .sorted()
                    .map(knownAuthors::get)
                    .filter(Objects::nonNull)
                    .filter(n -> !n.isBlank())
                    .distinct()
                    .forEach(authorNames::add);

            Map<Role, List<String>> namesByRole = new EnumMap<>(Role.class);
            if (approvals != null) {
                for (Approval app : approvals) {
                    if (app.name() != null && !app.name().isBlank()) {
                        namesByRole
                                .computeIfAbsent(app.roleKey(), r -> new ArrayList<>())
                                .add(app.name());
                    }
                }
            }

            List<Role> reviewers = policy != null ? policy.reviewers() : List.of();
            for (Role role : reviewers) {
                ObjectNode slot = slots.addObject();
                slot.put("roleKey", role.name());
                slot.put(
                        "label",
                        role == Role.PRINCIPAL
                                ? "Hiệu trưởng"
                                : (role == Role.VICE_PRINCIPAL ? "Phó Hiệu trưởng" : "Tổ trưởng"));
                ArrayNode namesArray = slot.putArray("names");
                List<String> names = namesByRole.getOrDefault(role, List.of());
                for (String name : names) {
                    namesArray.add(name);
                }
            }
            var result = snapshot.toString();
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public String buildPrintSnapshot(String docType, long revision, Role completionRole, Instant now) {
        String logPrefix = LOG_PREFIX + "|buildPrintSnapshot";
        log.info("{}|START", logPrefix);
        try {
            RolePolicy policy = loadPolicy(docType);
            var result = buildPrintSnapshot(docType, revision, List.of(), policy, now);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public boolean hasReviewerPrecedence(CurrentUser user, String docType, Long ageGroupId, Long classroomId) {
        String logPrefix = LOG_PREFIX + "|hasReviewerPrecedence";
        log.info("{}|START", logPrefix);
        try {
            if (user == null) {
                var result = false;
                log.info("{}|END", logPrefix);
                return result;
            }
            RolePolicy policy = loadPolicy(docType);
            var result = accessPolicy.hasReviewerPrecedence(user, policy);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public boolean isAuthorEligible(CurrentUser user, String docType, Long ageGroupId, Long classroomId) {
        String logPrefix = LOG_PREFIX + "|isAuthorEligible";
        log.info("{}|START", logPrefix);
        try {
            if (user == null) {
                var result = false;
                log.info("{}|END", logPrefix);
                return result;
            }
            RolePolicy policy = loadPolicy(docType);
            if (("year".equals(docType) || "schoolYear".equals(docType))) {
                var result = accessPolicy.canAuthorYearPlan(user, policy, ageGroupId);
                log.info("{}|END", logPrefix);
                return result;
            }
            var result = accessPolicy.canAuthorClassDoc(user, policy, ageGroupId, classroomId);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public String serializeApprovals(List<Approval> approvals) {
        String logPrefix = LOG_PREFIX + "|serializeApprovals";
        log.info("{}|START", logPrefix);
        try {
            if (approvals == null || approvals.isEmpty()) {
                var result = "[]";
                log.info("{}|END", logPrefix);
                return result;
            }
            try {
                var result = objectMapper.writeValueAsString(approvals);
                log.info("{}|END", logPrefix);
                return result;
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot serialize approval history", ex);
            }
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public com.carenest.dto.eduplan.EduReviewStateResponse toReviewStateResponse(
            Long policyRevision,
            int round,
            RolePolicy policy,
            Evaluation evaluation,
            String approvalsJson,
            String authorCoverage,
            Instant authorTrackingSince,
            String completionMode,
            Instant completedAt,
            String completionPolicyJson,
            String printSnapshotJson) {
        String logPrefix = LOG_PREFIX + "|toReviewStateResponse";
        log.info("{}|START", logPrefix);
        try {
            List<String> roles =
                    policy != null ? policy.reviewers().stream().map(Enum::name).toList() : List.of();
            String waitingRole = evaluation != null && evaluation.waitingRole() != null
                    ? evaluation.waitingRole().name()
                    : null;
            List<com.carenest.dto.eduplan.EduReviewStateResponse.ApprovalItem> approvalItems =
                    parseApprovals(approvalsJson).stream()
                            .map(a -> new com.carenest.dto.eduplan.EduReviewStateResponse.ApprovalItem(
                                    a.userId(), a.name(), a.roleKey().name(), a.at(), a.note(), a.round()))
                            .toList();

            Object completionPolicyObj = parseJsonNode(completionPolicyJson);
            Object printSnapshotObj = parseJsonNode(printSnapshotJson);

            var result = new com.carenest.dto.eduplan.EduReviewStateResponse(
                    policyRevision,
                    round,
                    roles,
                    waitingRole,
                    approvalItems,
                    authorCoverage,
                    authorTrackingSince,
                    completionMode,
                    completedAt,
                    completionPolicyObj,
                    printSnapshotObj);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Object parseJsonNode(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private static final Set<String> ALLOWED_DOC_TYPES = Set.of("year", "schoolYear", "branch", "theme");
    private static final Set<Role> ALLOWED_CREATOR_ROLES =
            Set.of(Role.TEACHER, Role.TEAM_LEADER, Role.VICE_PRINCIPAL, Role.PRINCIPAL);
    private static final Set<Role> ALLOWED_REVIEWER_ROLES =
            Set.of(Role.TEAM_LEADER, Role.VICE_PRINCIPAL, Role.PRINCIPAL);

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public EduWorkflowPolicyResponse getPolicy(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getPolicy|userId=" + (currentUser != null ? currentUser.userId() : null);
        log.info("{}|START", logPrefix);
        try {
            EduSettings settings = settingsRepository
                    .findById(EduSettings.SINGLETON_ID)
                    .orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));

            Map<String, RolePolicy> allPolicies = loadAllPolicies();
            Map<String, EduWorkflowPolicyResponse.PolicyEntry> policiesMap = new java.util.LinkedHashMap<>();
            allPolicies.forEach((key, policy) -> policiesMap.put(
                    key,
                    new EduWorkflowPolicyResponse.PolicyEntry(
                            policy.creators().stream().map(Enum::name).toList(),
                            policy.reviewers().stream().map(Enum::name).toList())));

            boolean canConfigure = currentUser != null && accessPolicy.canConfigureWorkflow(currentUser);
            Instant updatedAt = settings.getWorkflowUpdatedAt();

            EduWorkflowPolicyResponse response =
                    new EduWorkflowPolicyResponse(settings.getWorkflowRevision(), policiesMap, canConfigure, updatedAt);
            var result = response;
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public EduWorkflowPolicyResponse savePolicy(CurrentUser currentUser, EduWorkflowPolicyRequest request) {
        String logPrefix = LOG_PREFIX + "|savePolicy|userId=" + (currentUser != null ? currentUser.userId() : null);
        log.info("{}|START", logPrefix);
        try {
            // 1. Authorization: Only Principal with active assignment
            if (currentUser == null || !accessPolicy.canConfigureWorkflow(currentUser)) {
                throw GlobalException.of(ApiCode.EDU_CONFIG_FORBIDDEN);
            }

            // 2. Validation of request body
            validatePolicyRequest(request);

            // 3. Acquire exclusive lock on EduSettings
            EduSettings settings =
                    settingsRepository.findForUpdate().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));

            // 4. Check expected revision
            if (request.revision() == null || request.revision() != settings.getWorkflowRevision()) {
                throw GlobalException.of(ApiCode.CONCURRENT_UPDATE);
            }

            // 5. Parse and serialize new policies
            Map<String, RolePolicy> newPolicies = parseRequestPolicies(request);
            String newPolicyJson = serializePolicies(newPolicies);

            // 6. Check no-op: if normalized policy equals current
            Map<String, RolePolicy> currentPolicies = loadAllPolicies();
            if (Objects.equals(currentPolicies, newPolicies)) {
                var result = getPolicy(currentUser);
                log.info("{}|NOOP|END", logPrefix);
                return result;
            }

            // 7. Update EduSettings: bump revision, update timestamp/user
            Instant now = clock.instant();
            settings.updateWorkflowPolicy(newPolicyJson, currentUser.userId(), now);

            // Also keep legacy signers projection in sync
            RolePolicy yearPol = newPolicies.get("year");
            RolePolicy branchPol = newPolicies.get("branch");
            RolePolicy themePol = newPolicies.get("theme");
            Map<String, List<String>> signersProjection = Map.of(
                    "year", yearPol.reviewers().stream().map(Enum::name).toList(),
                    "branch", branchPol.reviewers().stream().map(Enum::name).toList(),
                    "week", themePol.reviewers().stream().map(Enum::name).toList());
            settings.update(
                    objectMapper.writeValueAsString(signersProjection),
                    settings.getWeekRows(),
                    currentUser.userId(),
                    now);

            // 8. Reconcile pending docs atomically in the same transaction
            reconcilePending(currentUser.userId(), now);

            EduWorkflowPolicyResponse response = getPolicy(currentUser);
            var result = response;
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void validatePolicyRequest(EduWorkflowPolicyRequest request) {
        if (request == null || request.policies() == null) {
            throw GlobalException.of(ApiCode.SIGNERS_INVALID);
        }
        if (!Objects.equals(request.policies().keySet(), ALLOWED_DOC_TYPES)
                && !Objects.equals(request.policies().keySet(), Set.of("year", "branch", "theme"))) {
            throw GlobalException.of(ApiCode.SIGNERS_INVALID);
        }
        for (String type : request.policies().keySet()) {
            EduWorkflowPolicyRequest.PolicyEntry entry = request.policies().get(type);
            if (entry == null || entry.creators() == null || entry.reviewers() == null) {
                throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            }
            if (entry.creators().size() != 1 || entry.reviewers().isEmpty()) {
                throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            }
            if (new HashSet<>(entry.creators()).size() != entry.creators().size()) {
                throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            }
            if (new HashSet<>(entry.reviewers()).size() != entry.reviewers().size()) {
                throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            }
            List<Role> creators = new ArrayList<>();
            for (String c : entry.creators()) {
                try {
                    Role r = Role.valueOf(c);
                    if (!ALLOWED_CREATOR_ROLES.contains(r)) {
                        throw GlobalException.of(ApiCode.SIGNERS_INVALID);
                    }
                    creators.add(r);
                } catch (IllegalArgumentException ex) {
                    throw GlobalException.of(ApiCode.SIGNERS_INVALID);
                }
            }
            List<Role> reviewers = new ArrayList<>();
            for (String rev : entry.reviewers()) {
                try {
                    Role r = Role.valueOf(rev);
                    if (!ALLOWED_REVIEWER_ROLES.contains(r)) {
                        throw GlobalException.of(ApiCode.SIGNERS_INVALID);
                    }
                    reviewers.add(r);
                } catch (IllegalArgumentException ex) {
                    throw GlobalException.of(ApiCode.SIGNERS_INVALID);
                }
            }
            // Disjoint check
            Set<Role> intersection = new HashSet<>(creators);
            intersection.retainAll(reviewers);
            if (!intersection.isEmpty()
                    || creators.stream().anyMatch(c -> reviewers.stream().anyMatch(r -> r.ordinal() <= c.ordinal()))) {
                throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            }
        }
    }

    private Map<String, RolePolicy> parseRequestPolicies(EduWorkflowPolicyRequest request) {
        Map<String, RolePolicy> result = new java.util.LinkedHashMap<>();
        for (String type : request.policies().keySet()) {
            EduWorkflowPolicyRequest.PolicyEntry entry = request.policies().get(type);
            List<Role> creators = entry.creators().stream().map(Role::valueOf).toList();
            List<Role> reviewers = entry.reviewers().stream().map(Role::valueOf).toList();
            result.put(type, new RolePolicy(creators, reviewers));
        }
        result.putIfAbsent("schoolYear", loadPolicy("schoolYear"));
        return result;
    }

    private String serializePolicies(Map<String, RolePolicy> policies) {
        ObjectNode root = objectMapper.createObjectNode();
        policies.forEach((type, pol) -> {
            ObjectNode node = root.putObject(type);
            ArrayNode creators = node.putArray("creators");
            pol.creators().forEach(c -> creators.add(c.name()));
            ArrayNode reviewers = node.putArray("reviewers");
            pol.reviewers().forEach(r -> reviewers.add(r.name()));
        });
        return root.toString();
    }
}
