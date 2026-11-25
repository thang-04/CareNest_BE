package com.carenest.service.eduplan.impl;

import com.carenest.security.authorization.EduPlanAccessPolicy.DocType;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Role;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.RolePolicy;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EDU-110: suy chuỗi soạn/duyệt từ ô tick 8 mã EDU của ma trận vai trò (BR1.1–BR1.5, BR3.1). Thuần, không đọc DB.
 */
public final class EduWorkflowPolicyDeriver {

    public enum Rule {
        CREATOR_NOT_EXACTLY_ONE,
        NO_REVIEWER,
        TEACHER_REVIEWER,
        CREATOR_IS_REVIEWER,
        REVIEWER_NOT_ABOVE_CREATOR
    }

    public record Issue(String docType, Rule rule, List<CareNestRole> roles) {}

    /** issues rỗng ⇔ policies đủ 4 loại. Loại vi phạm không có trong policies. */
    public record Result(Map<String, RolePolicy> policies, List<Issue> issues) {
        public boolean valid() {
            return issues.isEmpty();
        }
    }

    private static final List<CareNestRole> ROLE_ORDER = List.of(
            CareNestRole.TEACHER, CareNestRole.TEAM_LEADER, CareNestRole.VICE_PRINCIPAL, CareNestRole.PRINCIPAL);

    private EduWorkflowPolicyDeriver() {}

    public static Result derive(Map<CareNestRole, Set<Permission>> matrix) {
        Map<String, RolePolicy> policies = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();
        for (DocType type : DocType.values()) {
            List<CareNestRole> creators = rolesWith(matrix, type.edit());
            List<CareNestRole> reviewers = rolesWith(matrix, type.approve());
            List<Issue> typeIssues = new ArrayList<>();
            if (creators.size() != 1) {
                typeIssues.add(new Issue(type.key(), Rule.CREATOR_NOT_EXACTLY_ONE, creators));
            }
            if (reviewers.contains(CareNestRole.TEACHER)) {
                typeIssues.add(new Issue(type.key(), Rule.TEACHER_REVIEWER, List.of(CareNestRole.TEACHER)));
            }
            List<CareNestRole> both =
                    creators.stream().filter(reviewers::contains).toList();
            if (!both.isEmpty()) {
                typeIssues.add(new Issue(type.key(), Rule.CREATOR_IS_REVIEWER, both));
            }
            if (creators.size() == 1) {
                CareNestRole creator = creators.getFirst();
                List<CareNestRole> lower = reviewers.stream()
                        .filter(r -> r != creator && level(r) <= level(creator))
                        .toList();
                if (!lower.isEmpty()) {
                    typeIssues.add(new Issue(type.key(), Rule.REVIEWER_NOT_ABOVE_CREATOR, lower));
                }
                if (reviewers.isEmpty() && creator != CareNestRole.PRINCIPAL) {
                    typeIssues.add(new Issue(type.key(), Rule.NO_REVIEWER, List.of()));
                }
            }
            if (typeIssues.isEmpty()) {
                policies.put(
                        type.key(),
                        new RolePolicy(
                                List.of(toRole(creators.getFirst())),
                                reviewers.stream()
                                        .map(EduWorkflowPolicyDeriver::toRole)
                                        .toList()));
            }
            issues.addAll(typeIssues);
        }
        return new Result(policies, List.copyOf(issues));
    }

    /** Vai trò có mã, xếp cấp dưới trước (BR3.1); chỉ 4 vai trò kế hoạch giáo dục. */
    private static List<CareNestRole> rolesWith(Map<CareNestRole, Set<Permission>> matrix, Permission permission) {
        return ROLE_ORDER.stream()
                .filter(role -> matrix.getOrDefault(role, Set.of()).contains(permission))
                .sorted(Comparator.comparingInt(EduWorkflowPolicyDeriver::level))
                .toList();
    }

    private static int level(CareNestRole role) {
        return ROLE_ORDER.indexOf(role);
    }

    public static Role toRole(CareNestRole role) {
        return Role.valueOf(role.name());
    }
}
