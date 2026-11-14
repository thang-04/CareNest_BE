package com.carenest.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountPhotoAccessPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final long CAMPUS_A = 1L;
    private static final long CAMPUS_B = 2L;
    // Nhân sự giả: HT 10, Admin 11, PHT A 20, PHT B 21, GV A 30 (lớp 300), GV B 31 (lớp 310), bếp A 40, chưa phân công 50
    private static final long HT = 10L;
    private static final long ADMIN = 11L;
    private static final long PHT_A = 20L;
    private static final long PHT_B = 21L;
    private static final long GV_A = 30L;
    private static final long GV_B = 31L;
    private static final long BEP_A = 40L;
    private static final long UNASSIGNED = 50L;
    private static final Set<Long> EVERYONE = Set.of(HT, ADMIN, PHT_A, PHT_B, GV_A, GV_B, BEP_A, UNASSIGNED);

    @Mock
    private StaffAccessPolicy staffAccessPolicy;

    @Mock
    private StaffAssignmentService assignments;

    @Mock
    private ClassroomService classrooms;

    @Mock
    private GuardianChildLinkService guardianLinks;

    @Mock
    private EnrollmentService enrollments;

    private AccountPhotoAccessPolicy policy;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC);
        policy = new AccountPhotoAccessPolicy(
                staffAccessPolicy, assignments, classrooms, guardianLinks, enrollments, clock);
        given(assignments.findActiveStaffUserIds(Set.of(CAMPUS_A), TODAY)).willReturn(Set.of(PHT_A, GV_A, BEP_A));
        given(assignments.findActiveStaffUserIds(Set.of(CAMPUS_B), TODAY)).willReturn(Set.of(PHT_B, GV_B));
        given(assignments.findPrincipalIds(TODAY)).willReturn(Set.of(HT));
        given(assignments.findActiveCampusIds(PHT_A, StaffRole.VICE_PRINCIPAL, TODAY)).willReturn(Set.of(CAMPUS_A));
        given(assignments.findActiveCampusIds(BEP_A, StaffRole.KITCHEN_STAFF, TODAY)).willReturn(Set.of(CAMPUS_A));
        given(assignments.findCurrentTeacherClassroomIds(GV_A, TODAY)).willReturn(Set.of(300L));
        given(classrooms.findCampusIds(Set.of(300L))).willReturn(Map.of(300L, CAMPUS_A));
    }

    private CurrentUser user(long id, CareNestRole... roles) {
        CurrentUser user = TestPermissions.userWithDefaults(id, roles);
        given(staffAccessPolicy.seesAllCampuses(user)).willReturn(false);
        return user;
    }

    @Test
    @DisplayName("AUTH-27: HT và Admin xem ảnh mọi nhân sự")
    void principalAndAdminSeeEveryone() {
        CurrentUser ht = TestPermissions.userWithDefaults(HT, CareNestRole.PRINCIPAL);
        given(staffAccessPolicy.seesAllCampuses(ht)).willReturn(true);
        CurrentUser admin = TestPermissions.userWithDefaults(ADMIN, CareNestRole.ADMIN);
        given(staffAccessPolicy.seesAllCampuses(admin)).willReturn(true);

        assertThat(policy.visibleUserIds(ht, EVERYONE)).isEqualTo(EVERYONE);
        assertThat(policy.visibleUserIds(admin, EVERYONE)).isEqualTo(EVERYONE);
    }

    @Test
    @DisplayName("AUTH-27: PHT xem nhân sự cùng điểm trường và HT, không xem điểm trường khác")
    void vicePrincipalSeesOwnCampusAndPrincipal() {
        CurrentUser pht = user(PHT_A, CareNestRole.VICE_PRINCIPAL);

        assertThat(policy.visibleUserIds(pht, EVERYONE)).containsExactlyInAnyOrder(PHT_A, GV_A, BEP_A, HT);
    }

    @Test
    @DisplayName("AUTH-27: GV tính điểm trường qua lớp đang phân công")
    void teacherCampusComesFromClassroom() {
        CurrentUser gv = user(GV_A, CareNestRole.TEACHER);

        assertThat(policy.visibleUserIds(gv, Set.of(GV_A, PHT_A, PHT_B, GV_B, HT)))
                .containsExactlyInAnyOrder(GV_A, PHT_A, HT);
    }

    @Test
    @DisplayName("AUTH-27: bếp xem nhân sự cùng điểm trường")
    void kitchenSeesOwnCampus() {
        CurrentUser bep = user(BEP_A, CareNestRole.KITCHEN_STAFF);

        assertThat(policy.visibleUserIds(bep, Set.of(GV_A, GV_B))).containsExactly(GV_A);
    }

    @Test
    @DisplayName("AUTH-27: nhân sự chưa phân công chỉ xem ảnh của mình, người khác không xem được ảnh họ")
    void unassignedStaffOnlySelf() {
        CurrentUser unassigned = user(UNASSIGNED, CareNestRole.TEACHER);
        CurrentUser pht = user(PHT_A, CareNestRole.VICE_PRINCIPAL);

        assertThat(policy.visibleUserIds(unassigned, EVERYONE)).containsExactly(UNASSIGNED);
        assertThat(policy.visibleUserIds(pht, Set.of(UNASSIGNED))).isEmpty();
    }

    @Test
    @DisplayName("AUTH-27: phụ huynh chỉ xem GV đang dạy lớp con")
    void parentSeesTeachersOfChildClass() {
        CurrentUser parent = user(70L, CareNestRole.PARENT);
        given(guardianLinks.findChildIdsOfGuardian(70L)).willReturn(List.of(900L));
        given(enrollments.findActiveClassroomIdByChildIds(List.of(900L), TODAY)).willReturn(Map.of(900L, 300L));
        given(assignments.findCurrentTeacherIds(Set.of(300L), TODAY)).willReturn(Map.of(300L, List.of(GV_A)));

        assertThat(policy.visibleUserIds(parent, Set.of(GV_A, GV_B, HT, PHT_A))).containsExactly(GV_A);
    }

    @Test
    @DisplayName("AUTH-27: phụ huynh chưa có con đang học không xem ảnh nhân sự nào")
    void parentWithoutEnrolledChildSeesNobody() {
        CurrentUser parent = user(71L, CareNestRole.PARENT);
        given(guardianLinks.findChildIdsOfGuardian(71L)).willReturn(List.of());

        assertThat(policy.visibleUserIds(parent, Set.of(GV_A, HT))).isEmpty();
        verifyNoInteractions(enrollments);
    }

    @Test
    @DisplayName("AUTH-27: GV kiêm phụ huynh xem cả nhân sự cùng điểm trường và GV lớp con")
    void teacherWhoIsAlsoParentGetsBothScopes() {
        CurrentUser gvParent = user(GV_A, CareNestRole.TEACHER, CareNestRole.PARENT);
        given(guardianLinks.findChildIdsOfGuardian(GV_A)).willReturn(List.of(901L));
        given(enrollments.findActiveClassroomIdByChildIds(List.of(901L), TODAY)).willReturn(Map.of(901L, 310L));
        given(assignments.findCurrentTeacherIds(any(), any())).willReturn(Map.of(310L, List.of(GV_B)));

        assertThat(policy.visibleUserIds(gvParent, Set.of(GV_B, PHT_A, PHT_B)))
                .containsExactlyInAnyOrder(GV_B, PHT_A);
    }
}
