package com.carenest.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaffAccessPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final CurrentUser ADMIN = user(1L, CareNestRole.ADMIN);
    private static final CurrentUser PRINCIPAL = user(2L, CareNestRole.PRINCIPAL);
    private static final CurrentUser VICE = user(3L, CareNestRole.VICE_PRINCIPAL);
    private static final Long CAMPUS_A = 10L;
    private static final Long CAMPUS_B = 20L;

    private final StaffAssignmentService assignments = mock(StaffAssignmentService.class);
    private final EnrollmentService enrollments = mock(EnrollmentService.class);
    private final ClassroomService classrooms = mock(ClassroomService.class);
    private final GuardianChildLinkService guardianLinks = mock(GuardianChildLinkService.class);
    private final StaffAccessPolicy policy = new StaffAccessPolicy(
            assignments,
            enrollments,
            classrooms,
            guardianLinks,
            Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        given(assignments.hasActivePrincipalAssignment(2L, TODAY)).willReturn(true);
        given(assignments.findActiveCampusIds(3L, StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(CAMPUS_A));
    }

    @Test
    @DisplayName("AUTH-06: Admin tạo HT, không tạo nhân sự; HT tạo PHT/GV/bếp, không tạo HT")
    void creatableRolesFollowAuth06() {
        assertThatCode(() -> policy.checkCanCreate(ADMIN, CareNestRole.PRINCIPAL, null))
                .doesNotThrowAnyException();
        assertDenied(() -> policy.checkCanCreate(ADMIN, CareNestRole.TEACHER, CAMPUS_A));
        assertThatCode(() -> policy.checkCanCreate(PRINCIPAL, CareNestRole.VICE_PRINCIPAL, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.checkCanCreate(PRINCIPAL, CareNestRole.TEACHER, null))
                .doesNotThrowAnyException();
        assertDenied(() -> policy.checkCanCreate(PRINCIPAL, CareNestRole.PRINCIPAL, null));
    }

    @Test
    @DisplayName("AUTH-15: PHT chỉ tạo GV/bếp kèm phân công trong campus mình")
    void vicePrincipalCreatesOnlyInOwnCampus() {
        assertThatCode(() -> policy.checkCanCreate(VICE, CareNestRole.TEACHER, CAMPUS_A))
                .doesNotThrowAnyException();
        assertDenied(() -> policy.checkCanCreate(VICE, CareNestRole.TEACHER, CAMPUS_B));
        assertDenied(() -> policy.checkCanCreate(VICE, CareNestRole.TEACHER, null));
        assertDenied(() -> policy.checkCanCreate(VICE, CareNestRole.VICE_PRINCIPAL, CAMPUS_A));
    }

    @Test
    @DisplayName("AUTH-01: HT mất phân công PRINCIPAL thì không còn phạm vi toàn trường")
    void principalWithoutActiveAssignmentHasNoSchoolScope() {
        CurrentUser formerPrincipal = user(9L, CareNestRole.PRINCIPAL);

        assertDenied(() -> policy.checkCanCreate(formerPrincipal, CareNestRole.TEACHER, null));
        assertThat(policy.seesAllCampuses(formerPrincipal)).isFalse();
    }

    @Test
    @DisplayName("AUTH-17: PHT quản lý GV/bếp đang được phân công trong campus mình; không đụng HT, PHT khác")
    void vicePrincipalManagesOnlyCampusStaff() {
        given(assignments.findActiveCampusIds(40L, StaffRole.TEACHER, TODAY)).willReturn(Set.of(CAMPUS_A));
        given(assignments.findActiveCampusIds(41L, StaffRole.TEACHER, TODAY)).willReturn(Set.of(CAMPUS_B));

        assertThat(policy.canManageAccount(VICE, 40L, CareNestRole.TEACHER)).isTrue();
        assertThat(policy.canManageAccount(VICE, 41L, CareNestRole.TEACHER)).isFalse();
        assertThat(policy.canManageAccount(VICE, 50L, CareNestRole.VICE_PRINCIPAL))
                .isFalse();
        assertThat(policy.canManageAccount(PRINCIPAL, 41L, CareNestRole.TEACHER))
                .isTrue();
        assertThat(policy.canManageAccount(PRINCIPAL, 60L, CareNestRole.PRINCIPAL))
                .isFalse();
        assertThat(policy.canManageAccount(PRINCIPAL, 61L, null)).isFalse();
        assertThat(policy.canManageAccount(ADMIN, 61L, null)).isTrue();
    }

    @Test
    @DisplayName("AUTH-16: PHT phân công GV/bếp trong campus mình; HT phân công toàn trường; không ai phân công HT")
    void assignmentScope() {
        assertThatCode(() -> policy.checkCanAssign(VICE, StaffRole.KITCHEN_STAFF, CAMPUS_A))
                .doesNotThrowAnyException();
        assertDenied(() -> policy.checkCanAssign(VICE, StaffRole.TEACHER, CAMPUS_B));
        assertDenied(() -> policy.checkCanAssign(VICE, StaffRole.VICE_PRINCIPAL, CAMPUS_A));
        assertThatCode(() -> policy.checkCanAssign(PRINCIPAL, StaffRole.VICE_PRINCIPAL, CAMPUS_B))
                .doesNotThrowAnyException();
        assertDenied(() -> policy.checkCanAssign(PRINCIPAL, StaffRole.PRINCIPAL, null));
    }

    @Test
    void campusVisibility() {
        given(assignments.hasActivePrincipalAssignment(any(), any())).willReturn(false);

        assertThatCode(() -> policy.checkCanViewCampus(VICE, CAMPUS_A)).doesNotThrowAnyException();
        assertDenied(() -> policy.checkCanViewCampus(VICE, CAMPUS_B));
        assertThat(policy.seesAllCampuses(ADMIN)).isTrue();
    }

    @Test
    @DisplayName("AUTH-18: PHT cấp tài khoản phụ huynh chỉ cho trẻ đang học trong campus mình; HT mọi trẻ")
    void childGuardianScope() {
        given(enrollments.findActiveClassroomIdByChildIds(Set.of(101L), TODAY)).willReturn(Map.of(101L, 11L));
        given(enrollments.findActiveClassroomIdByChildIds(Set.of(201L), TODAY)).willReturn(Map.of(201L, 21L));
        stubClassroomCampuses();

        assertThatCode(() -> policy.checkCanManageChildGuardians(VICE, 101L)).doesNotThrowAnyException();
        assertDenied(() -> policy.checkCanManageChildGuardians(VICE, 201L));
        // Trẻ không tồn tại / chưa có lớp: PHT nhận cùng 403
        assertDenied(() -> policy.checkCanManageChildGuardians(VICE, 999L));
        assertThatCode(() -> policy.checkCanManageChildGuardians(PRINCIPAL, 201L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AUTH-19: PHT quản lý phụ huynh khi một trẻ liên kết học trong campus mình")
    void parentScopeFollowsLinkedChildren() {
        given(guardianLinks.findChildIdsOfGuardian(60L)).willReturn(List.of(101L, 201L));
        given(guardianLinks.findChildIdsOfGuardian(61L)).willReturn(List.of(201L));
        given(enrollments.findActiveClassroomIdByChildIds(List.of(101L, 201L), TODAY))
                .willReturn(Map.of(101L, 11L, 201L, 21L));
        given(enrollments.findActiveClassroomIdByChildIds(List.of(201L), TODAY)).willReturn(Map.of(201L, 21L));
        stubClassroomCampuses();

        assertThatCode(() -> policy.checkCanManageParent(VICE, 60L)).doesNotThrowAnyException();
        assertDenied(() -> policy.checkCanManageParent(VICE, 61L));
        assertThatCode(() -> policy.checkCanManageParent(PRINCIPAL, 61L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AUTH-20: quyền xem liên cơ sở không mở quản lý tài khoản, phân công, phụ huynh ở campus khác")
    void allCampusViewDoesNotExtendManagementScope() {
        CurrentUser viewer = new CurrentUser(
                3L, "kc-3", "u3", "U", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of(Permission.ALL_CAMPUS_VIEW));
        given(assignments.findActiveCampusIds(41L, StaffRole.TEACHER, TODAY)).willReturn(Set.of(CAMPUS_B));
        given(guardianLinks.findChildIdsOfGuardian(61L)).willReturn(List.of(201L));
        given(enrollments.findActiveClassroomIdByChildIds(Set.of(201L), TODAY)).willReturn(Map.of(201L, 21L));
        given(enrollments.findActiveClassroomIdByChildIds(List.of(201L), TODAY)).willReturn(Map.of(201L, 21L));
        stubClassroomCampuses();

        assertThat(policy.managedCampusIds(viewer)).containsExactly(CAMPUS_A);
        assertDenied(() -> policy.checkCanViewCampus(viewer, CAMPUS_B));
        assertDenied(() -> policy.checkCanCreate(viewer, CareNestRole.TEACHER, CAMPUS_B));
        assertDenied(() -> policy.checkCanAssign(viewer, StaffRole.TEACHER, CAMPUS_B));
        assertThat(policy.canManageAccount(viewer, 41L, CareNestRole.TEACHER)).isFalse();
        assertDenied(() -> policy.checkCanManageChildGuardians(viewer, 201L));
        assertDenied(() -> policy.checkCanManageParent(viewer, 61L));
    }

    // Lớp 11 thuộc campus A, lớp 21 thuộc campus B
    private void stubClassroomCampuses() {
        given(classrooms.findCampusIds(anyCollection())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            Map<Long, Long> campuses = new HashMap<>();
            if (ids.contains(11L)) campuses.put(11L, CAMPUS_A);
            if (ids.contains(21L)) campuses.put(21L, CAMPUS_B);
            return campuses;
        });
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.FORBIDDEN);
    }

    private static CurrentUser user(Long id, CareNestRole role) {
        return new CurrentUser(id, "kc-" + id, "u" + id, "U", null, Set.of(role));
    }
}
