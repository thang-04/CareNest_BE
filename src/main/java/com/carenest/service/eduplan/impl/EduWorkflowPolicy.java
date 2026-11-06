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
            creators = List.copyOf(creators);
            reviewers = List.copyOf(reviewers);
            if (creators.isEmpty()
                    || reviewers.isEmpty()
                    || Set.copyOf(creators).size() != creators.size()
                    || Set.copyOf(reviewers).size() != reviewers.size()
                    || reviewers.contains(Role.TEACHER)
                    || creators.stream().anyMatch(reviewers::contains)) {
                throw new IllegalArgumentException("Invalid education creator/reviewer roles");
            }
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
