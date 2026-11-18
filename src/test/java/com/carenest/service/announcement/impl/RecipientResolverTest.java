package com.carenest.service.announcement.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.announcement.SchoolAnnouncement.Target;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AnnouncementScopeType;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.RecipientRole;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Người nhận theo phạm vi × vai trò (FR-011, FR-012, FR-013, clarify Q4). */
class RecipientResolverTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);

    private ClassroomService classrooms;
    private EnrollmentService enrollments;
    private GuardianChildLinkService guardians;
    private StaffAssignmentService staff;
    private StaffAccessPolicy access;
    private RecipientResolver resolver;

    @BeforeEach
    void setUp() {
        classrooms = mock(ClassroomService.class);
        enrollments = mock(EnrollmentService.class);
        guardians = mock(GuardianChildLinkService.class);
        staff = mock(StaffAssignmentService.class);
        access = mock(StaffAccessPolicy.class);
        resolver = new RecipientResolver(classrooms, enrollments, guardians, staff, access);
        // Lớp A (id 10) và B (id 11) ở điểm trường 1; lớp C (id 20) ở điểm trường 2
        given(classrooms.findActiveYearClassrooms())
                .willReturn(List.of(cls(10L, 1L, 3L), cls(11L, 1L, 4L), cls(20L, 2L, 3L)));
    }

    private static ClassroomInfoResponse cls(Long id, Long campusId, Long ageGroupId) {
        return new ClassroomInfoResponse(
                id, "Lớp " + id, campusId, 1L, null, null, null, ageGroupId, null, 25, ClassroomStatus.ACTIVE);
    }

    private static Target target(
            AnnouncementScopeType type, Long campus, Long ageGroup, Long classroom, RecipientRole... roles) {
        return new Target(type, campus, ageGroup, classroom, EnumSet.copyOf(List.of(roles)));
    }

    @Test
    void parentWithTwoChildrenInScopeIsCountedOnce() {
        given(enrollments.findChildrenInClassroom(10L, DAY))
                .willReturn(
                        List.of(new ClassChildResponse(100L, "An", null), new ClassChildResponse(101L, "Bình", null)));
        given(guardians.findLinksOfChildren(anyCollection()))
                .willReturn(List.of(
                        new GuardianLinkResponse(500L, 100L, null, null),
                        new GuardianLinkResponse(500L, 101L, null, null),
                        new GuardianLinkResponse(null, 101L, null, null)));

        Map<RecipientRole, Set<Long>> result = resolver.resolve(
                target(AnnouncementScopeType.CLASSROOM, null, null, 10L, RecipientRole.PARENT), 1L, DAY);

        assertThat(result.get(RecipientRole.PARENT)).containsExactly(500L);
    }

    @Test
    void bghOfClassScopeIsPrincipalPlusCampusVicePrincipalWithoutSender() {
        given(staff.findPrincipalIds(DAY)).willReturn(Set.of(1L));
        given(staff.findCampusVicePrincipalIds(1L, DAY)).willReturn(Set.of(2L, 3L));

        Map<RecipientRole, Set<Long>> result =
                resolver.resolve(target(AnnouncementScopeType.CLASSROOM, null, null, 10L, RecipientRole.BGH), 2L, DAY);

        assertThat(result.get(RecipientRole.BGH)).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void senderIsExcludedFromEverySelectedRoleBeforeCounting() {
        given(staff.findCurrentTeacherIds(anyCollection(), eq(DAY))).willReturn(Map.of(10L, List.of(1L, 40L)));
        given(staff.findPrincipalIds(DAY)).willReturn(Set.of(1L, 3L));
        given(staff.findCampusVicePrincipalIds(1L, DAY)).willReturn(Set.of(2L));
        Map<RecipientRole, Set<Long>> result = resolver.resolve(
                target(AnnouncementScopeType.CAMPUS, 1L, null, null, RecipientRole.BGH, RecipientRole.TEACHER),
                1L,
                DAY);
        assertThat(RecipientResolver.all(result))
                .containsExactlyInAnyOrder(2L, 3L, 40L)
                .doesNotContain(1L);
    }

    @Test
    void kitchenOfCampusIsCountedOnceForManyClasses() {
        given(staff.findCampusKitchenIds(1L, DAY)).willReturn(Set.of(70L));

        Map<RecipientRole, Set<Long>> result =
                resolver.resolve(target(AnnouncementScopeType.CAMPUS, 1L, null, null, RecipientRole.KITCHEN), 1L, DAY);

        assertThat(result.get(RecipientRole.KITCHEN)).containsExactly(70L);
    }

    @Test
    void ageGroupScopeWithoutActiveClassHasNoTeachers() {
        Map<RecipientRole, Set<Long>> result =
                resolver.resolve(target(AnnouncementScopeType.AGE_GROUP, 2L, 4L, null, RecipientRole.TEACHER), 1L, DAY);

        assertThat(result.get(RecipientRole.TEACHER)).isEmpty();
    }

    @Test
    void teachersOfAgeGroupInCampus() {
        given(staff.findCurrentTeacherIds(eq(List.of(10L, 20L)), any()))
                .willReturn(Map.of(10L, List.of(40L), 20L, List.of(41L)));

        Map<RecipientRole, Set<Long>> result = resolver.resolve(
                target(AnnouncementScopeType.AGE_GROUP, null, 3L, null, RecipientRole.TEACHER), 1L, DAY);

        assertThat(result.get(RecipientRole.TEACHER)).containsExactlyInAnyOrder(40L, 41L);
    }

    @Test
    void vicePrincipalCannotSendToOtherCampusOrWholeSchool() {
        CurrentUser pht = new CurrentUser(2L, "kc", "pht", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of());
        given(access.managedCampusIds(pht)).willReturn(Set.of(1L));
        given(classrooms.findCampusId(20L)).willReturn(java.util.Optional.of(2L));
        given(classrooms.findCampusId(10L)).willReturn(java.util.Optional.of(1L));

        assertDenied(() -> resolver.checkCanSend(
                pht, target(AnnouncementScopeType.CLASSROOM, null, null, 20L, RecipientRole.PARENT)));
        assertDenied(() -> resolver.checkCanSend(
                pht, target(AnnouncementScopeType.SCHOOL, null, null, null, RecipientRole.PARENT)));
        assertDenied(() -> resolver.checkCanSend(
                pht, target(AnnouncementScopeType.AGE_GROUP, null, 3L, null, RecipientRole.PARENT)));
        resolver.checkCanSend(pht, target(AnnouncementScopeType.CLASSROOM, null, null, 10L, RecipientRole.PARENT));
    }

    @Test
    void principalSendsAnywhere() {
        CurrentUser ht = new CurrentUser(1L, "kc", "ht", "HT", null, Set.of(CareNestRole.PRINCIPAL), Set.of());
        given(access.managesWholeSchool(ht)).willReturn(true);

        resolver.checkCanSend(ht, target(AnnouncementScopeType.SCHOOL, null, null, null, RecipientRole.PARENT));
    }

    @Test
    void missingScopeIdIsRejected() {
        assertThatThrownBy(() -> RecipientResolver.validate(
                        new Target(AnnouncementScopeType.CLASSROOM, null, null, null, Set.of())))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA));
    }

    private static void assertDenied(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.ANNOUNCEMENT_SCOPE_DENIED));
    }
}
