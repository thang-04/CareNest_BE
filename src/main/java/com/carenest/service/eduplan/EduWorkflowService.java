package com.carenest.service.eduplan;

import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service điều phối quy trình tạo/duyệt kế hoạch giáo dục (EDU-54).
 */
public interface EduWorkflowService {

    EduWorkflowPolicy.RolePolicy loadPolicy(String docType);

    Map<String, EduWorkflowPolicy.RolePolicy> loadAllPolicies();

    long getPolicyRevision();

    EduWorkflowPolicy.Evaluation evaluateYearPlan(EduPlan plan, LocalDate date);

    EduWorkflowPolicy.Evaluation evaluateClassDoc(EduClassDoc doc, Long ageGroupId, LocalDate date);

    Map<Long, EduWorkflowPolicy.Evaluation> evaluateClassDocs(java.util.Collection<EduClassDoc> docs, LocalDate date);

    void recordAuthor(Long yearPlanId, Long classDocId, Long userId, String origin, Instant now);

    Set<Long> getAuthorUserIdsForYearPlan(Long yearPlanId);

    Set<Long> getUnionAuthorUserIdsForDossier(EduClassDoc doc);

    Map<Long, Set<Long>> getUnionAuthorUserIdsForDossiers(java.util.Collection<EduClassDoc> docs);

    void reconcilePending(Long userId, Instant now);

    void activateLegacyPending(Instant now);

    Map<EduWorkflowPolicy.Role, Set<Long>> resolveActiveCandidates(
            EduWorkflowPolicy.RolePolicy policy, Long ageGroupId, Long classroomId, LocalDate date);

    void validateClassDocVersion(EduClassDoc doc, Long expectedVersion, Long expectedRootVersion);

    com.carenest.dto.eduplan.EduWorkflowPolicyResponse getPolicy(com.carenest.security.CurrentUser currentUser);

    com.carenest.dto.eduplan.EduWorkflowPolicyResponse savePolicy(
            com.carenest.security.CurrentUser currentUser, com.carenest.dto.eduplan.EduWorkflowPolicyRequest request);

    boolean hasReviewerPrecedence(
            com.carenest.security.CurrentUser user, String docType, Long ageGroupId, Long classroomId);

    boolean isAuthorEligible(com.carenest.security.CurrentUser user, String docType, Long ageGroupId, Long classroomId);

    String buildPrintSnapshot(
            String docType,
            long revision,
            List<EduWorkflowPolicy.Approval> approvals,
            EduWorkflowPolicy.RolePolicy policy,
            Instant now);

    String buildPrintSnapshot(
            String docType,
            long revision,
            List<EduWorkflowPolicy.Approval> approvals,
            EduWorkflowPolicy.RolePolicy policy,
            Instant now,
            Set<Long> authorIds);

    String buildPrintSnapshot(String docType, long revision, EduWorkflowPolicy.Role completionRole, Instant now);

    List<EduWorkflowPolicy.Approval> parseApprovals(String approvalsJson);

    String serializeApprovals(List<EduWorkflowPolicy.Approval> approvals);

    com.carenest.dto.eduplan.EduReviewStateResponse toReviewStateResponse(
            Long policyRevision,
            int round,
            EduWorkflowPolicy.RolePolicy policy,
            EduWorkflowPolicy.Evaluation evaluation,
            String approvalsJson,
            String authorCoverage,
            Instant authorTrackingSince,
            String completionMode,
            Instant completedAt,
            String completionPolicyJson,
            String printSnapshotJson);
}
