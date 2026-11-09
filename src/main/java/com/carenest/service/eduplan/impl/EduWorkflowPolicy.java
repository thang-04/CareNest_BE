package com.carenest.service.eduplan.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EduWorkflowPolicy {
    public enum Role {
        TEACHER,
        TEAM_LEADER,
        VICE_PRINCIPAL,
        PRINCIPAL
    }

    public record RolePolicy(List<Role> creators, List<Role> reviewers) {
        public RolePolicy {
            List<Role> cList = List.copyOf(creators);
            List<Role> rList = List.copyOf(reviewers);
            if (cList.size() != 1
                    || rList.isEmpty()
                    || Set.copyOf(cList).size() != cList.size()
                    || Set.copyOf(rList).size() != rList.size()
                    || rList.contains(Role.TEACHER)
                    || cList.stream().anyMatch(c -> rList.stream().anyMatch(r -> r.ordinal() <= c.ordinal()))) {
                throw new IllegalArgumentException("Invalid education creator/reviewer roles: reviewer must have strictly higher hierarchy than creator");
            }
            creators = cList;
            reviewers = rList;
        }
    }

    public record Approval(Long userId, String name, Role roleKey, Instant at, String note, int round) {}

    public record Evaluation(Role waitingRole, List<Role> skippedRoles, boolean complete, Role completionRole) {
        public Evaluation {
            skippedRoles = List.copyOf(skippedRoles);
        }
    }

    public Evaluation evaluate(
            RolePolicy policy,
            int round,
            List<Approval> approvals,
            Set<Long> excludedAuthorIds,
            Map<Role, Set<Long>> candidates) {
        Set<Role> approved = approvals.stream()
                .filter(a -> a.round() == round && a.userId() != null && !excludedAuthorIds.contains(a.userId()))
                .map(Approval::roleKey)
                .collect(Collectors.toSet());
        List<Role> skipped = new ArrayList<>();
        for (int i = 0; i < policy.reviewers().size(); i++) {
            Role role = policy.reviewers().get(i);
            if (approved.contains(role)) {
                continue;
            }
            boolean hasCandidate =
                    candidates.getOrDefault(role, Set.of()).stream().anyMatch(id -> !excludedAuthorIds.contains(id));
            if (hasCandidate || i == policy.reviewers().size() - 1) {
                return new Evaluation(role, skipped, false, null);
            }
            skipped.add(role);
        }
        return new Evaluation(null, skipped, true, policy.reviewers().getLast());
    }
}
