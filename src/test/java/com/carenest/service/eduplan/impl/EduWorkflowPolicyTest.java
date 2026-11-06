package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Approval;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Role;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.RolePolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EduWorkflowPolicyTest {
    private final EduWorkflowPolicy evaluator = new EduWorkflowPolicy();
    private final RolePolicy policy =
            new RolePolicy(List.of(Role.TEACHER), List.of(Role.TEAM_LEADER, Role.VICE_PRINCIPAL));

    @Test
    @DisplayName("EDU-54: nhóm soạn và duyệt không rỗng, không trùng hoặc giao nhau")
    void rejectsInvalidRoleSets() {
        assertThatThrownBy(() -> new RolePolicy(List.of(), List.of(Role.PRINCIPAL)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RolePolicy(List.of(Role.TEACHER), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RolePolicy(List.of(Role.TEACHER, Role.TEACHER), List.of(Role.PRINCIPAL)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RolePolicy(List.of(Role.TEACHER), List.of(Role.PRINCIPAL, Role.PRINCIPAL)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RolePolicy(List.of(Role.PRINCIPAL), List.of(Role.PRINCIPAL)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RolePolicy(List.of(Role.TEAM_LEADER), List.of(Role.TEACHER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EDU-54: còn candidate hợp lệ thì giữ bước dù có candidate đã soạn")
    void excludesAuthorsWithoutSkippingOtherEligibleCandidates() {
        var result = evaluator.evaluate(
                policy,
                2,
                List.of(),
                Set.of(10L),
                Map.of(Role.TEAM_LEADER, Set.of(10L, 11L), Role.VICE_PRINCIPAL, Set.of(12L)));
        assertThat(result.waitingRole()).isEqualTo(Role.TEAM_LEADER);
        assertThat(result.skippedRoles()).isEmpty();
        assertThat(result.complete()).isFalse();
    }

    @Test
    @DisplayName("EDU-54: thiếu bước giữa bỏ qua, thiếu bước cuối giữ chờ")
    void skipsOnlyIntermediateMissingSteps() {
        var result = evaluator.evaluate(policy, 2, List.of(), Set.of(), Map.of());
        assertThat(result.skippedRoles()).containsExactly(Role.TEAM_LEADER);
        assertThat(result.waitingRole()).isEqualTo(Role.VICE_PRINCIPAL);
        assertThat(result.complete()).isFalse();
    }

    @Test
    @DisplayName("EDU-54: bước cuối đã duyệt và bước giữa thiếu người thì hoàn tất, không tạo approval giả")
    void completesWhenLastApprovedAndEarlierStepCanBeSkipped() {
        var approvals = List.of(approval(Role.VICE_PRINCIPAL, 12L, 2));
        var result = evaluator.evaluate(policy, 2, approvals, Set.of(), Map.of());
        assertThat(result.complete()).isTrue();
        assertThat(result.waitingRole()).isNull();
        assertThat(result.completionRole()).isEqualTo(Role.VICE_PRINCIPAL);
        assertThat(approvals).hasSize(1);
    }

    @Test
    @DisplayName("EDU-54: thêm bước còn người thì chờ dù bước cuối đã duyệt")
    void waitsForNewUnapprovedStep() {
        var result = evaluator.evaluate(
                policy,
                2,
                List.of(approval(Role.VICE_PRINCIPAL, 12L, 2)),
                Set.of(),
                Map.of(Role.TEAM_LEADER, Set.of(11L)));
        assertThat(result.complete()).isFalse();
        assertThat(result.waitingRole()).isEqualTo(Role.TEAM_LEADER);
    }

    @Test
    @DisplayName("EDU-54: chỉ approvals cùng vòng, không do người soạn thực hiện mới được tính")
    void ignoresOldRoundsAndAuthorApprovals() {
        var result = evaluator.evaluate(
                policy,
                2,
                List.of(approval(Role.TEAM_LEADER, 11L, 1), approval(Role.VICE_PRINCIPAL, 10L, 2)),
                Set.of(10L),
                Map.of(Role.TEAM_LEADER, Set.of(11L)));
        assertThat(result.waitingRole()).isEqualTo(Role.TEAM_LEADER);
        assertThat(result.complete()).isFalse();
    }

    @Test
    @DisplayName("EDU-54: đổi chuỗi chỉ giữ approvals cùng vòng của vai trò còn trong chuỗi")
    void removedRolesDoNotRequireReapproval() {
        var shortened = new RolePolicy(List.of(Role.TEACHER), List.of(Role.VICE_PRINCIPAL));
        assertThat(evaluator
                        .evaluate(
                                shortened,
                                2,
                                List.of(approval(Role.TEAM_LEADER, 11L, 2), approval(Role.VICE_PRINCIPAL, 12L, 2)),
                                Set.of(),
                                Map.of())
                        .complete())
                .isTrue();
    }

    private static Approval approval(Role role, long id, int round) {
        return new Approval(id, "Người thử", role, Instant.parse("2026-11-06T00:00:00Z"), "", round);
    }
}
