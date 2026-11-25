package com.carenest.service.eduplan.impl;

import com.carenest.dto.account.EduWorkflowIssuesResponse;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduSettingsRepository;
import com.carenest.security.authorization.EduPlanAccessPolicy.DocType;
import com.carenest.service.account.RoleMatrixWorkflowSync;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** EDU-110: nối ma trận quyền (account) với policy soạn/duyệt (EDU-54) qua {@link EduWorkflowPolicyDeriver}. */
@Component
@RequiredArgsConstructor
public class EduRoleMatrixWorkflowSync implements RoleMatrixWorkflowSync {

    private final EduSettingsRepository settingsRepository;
    private final com.carenest.service.organization.StaffAssignmentService staffAssignmentService;
    private final java.time.Clock clock;
    // Lấy muộn: EduWorkflowService → AccountService → PermissionService → bean này tạo vòng khi khởi tạo
    private final org.springframework.beans.factory.ObjectProvider<EduWorkflowService> workflowServiceProvider;

    @Override
    public void lockWorkflowSettings() {
        settingsRepository.findForUpdate().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
    }

    @Override
    public EduWorkflowIssuesResponse validate(Map<CareNestRole, Set<Permission>> matrix) {
        return toResponse(EduWorkflowPolicyDeriver.derive(matrix));
    }

    @Override
    public int apply(Map<CareNestRole, Set<Permission>> matrix, Long actorUserId, Instant now) {
        var derived = EduWorkflowPolicyDeriver.derive(matrix).policies();
        if (derived.isEmpty()) return 0;
        EduWorkflowService workflowService = workflowServiceProvider.getObject();
        var current = workflowService.loadAllPolicies();
        int changed = (int) derived.entrySet().stream()
                .filter(e -> !e.getValue().equals(current.get(e.getKey())))
                .count();
        if (changed > 0) workflowService.applyPolicy(derived, actorUserId, now);
        return changed;
    }

    @Override
    public Map<CareNestRole, Set<Permission>> ticksFromCurrentPolicy() {
        var policies = workflowServiceProvider.getObject().loadAllPolicies();
        Map<CareNestRole, Set<Permission>> ticks = new java.util.EnumMap<>(CareNestRole.class);
        for (CareNestRole role : List.of(
                CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL, CareNestRole.TEAM_LEADER, CareNestRole.TEACHER)) {
            ticks.put(role, java.util.EnumSet.noneOf(Permission.class));
        }
        for (DocType type : DocType.values()) {
            var policy = policies.get(type.key());
            policy.creators()
                    .forEach(r -> ticks.get(CareNestRole.valueOf(r.name())).add(type.edit()));
            // TEACHER không bao giờ duyệt (RolePolicy đã chặn)
            policy.reviewers()
                    .forEach(r -> ticks.get(CareNestRole.valueOf(r.name())).add(type.approve()));
        }
        return ticks;
    }

    @Override
    public java.util.Optional<Long> activePrincipalUserId(Instant now) {
        return staffAssignmentService.findPrincipalIds(java.time.LocalDate.ofInstant(now, clock.getZone())).stream()
                .min(Long::compare);
    }

    static EduWorkflowIssuesResponse toResponse(EduWorkflowPolicyDeriver.Result result) {
        return new EduWorkflowIssuesResponse(result.issues().stream()
                .map(i -> new EduWorkflowIssuesResponse.Issue(
                        DocType.fromKey(i.docType()).map(Enum::name).orElse(i.docType()),
                        i.rule().name(),
                        i.roles()))
                .toList());
    }
}
