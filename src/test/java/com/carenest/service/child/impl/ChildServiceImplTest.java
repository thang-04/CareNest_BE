package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.entity.child.Child;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.child.ChildMapper;
import com.carenest.repository.child.ChildRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class ChildServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final CurrentUser TEACHER =
            new CurrentUser(7L, "kc-7", "teacher.a", "Demo", null, Set.of(CareNestRole.TEACHER));

    private final ChildRepository childRepository = mock(ChildRepository.class);
    private final EnrollmentService enrollmentService = mock(EnrollmentService.class);
    private final ChildAccessPolicy childAccessPolicy = mock(ChildAccessPolicy.class);
    private final StaffAccessPolicy staffAccessPolicy = mock(StaffAccessPolicy.class);
    private final ClassroomService classroomService = mock(ClassroomService.class);
    private final GuardianChildLinkService guardianChildLinkService = mock(GuardianChildLinkService.class);
    private final ChildServiceImpl childService = new ChildServiceImpl(
            childRepository,
            enrollmentService,
            childAccessPolicy,
            new ChildMapper(),
            staffAccessPolicy,
            classroomService,
            guardianChildLinkService,
            FIXED_CLOCK);

    @Test
    void shouldReturnProfileWithCurrentClassroomWhenInScope() {
        given(childRepository.findById(101L)).willReturn(Optional.of(child(101L)));
        given(enrollmentService.findActiveClassroomIds(101L, TODAY)).willReturn(List.of(11L));

        ChildProfileResponse response = childService.getChildProfile(TEACHER, 101L);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.classroomId()).isEqualTo(11L);
        verify(childAccessPolicy).checkCanReadChild(TEACHER, 101L);
    }

    @Test
    void shouldNotLoadChildWhenOutOfScope() {
        willThrow(GlobalException.of(ApiCode.CHILD_ACCESS_DENIED))
                .given(childAccessPolicy)
                .checkCanReadChild(TEACHER, 201L);

        assertThatThrownBy(() -> childService.getChildProfile(TEACHER, 201L))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.FORBIDDEN.getHttpStatus());
        verify(childRepository, never()).findById(anyLong());
    }

    @Test
    void shouldReturn404OnlyAfterScopeCheckPassed() {
        given(childRepository.findById(101L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> childService.getChildProfile(TEACHER, 101L))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.NOT_FOUND.getHttpStatus());
    }

    private static final CurrentUser VICE =
            new CurrentUser(3L, "kc-3", "vp", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL));

    @Test
    @DisplayName("AUTH-08: PHT chỉ liệt kê trẻ đang học ở các lớp thuộc campus mình")
    @SuppressWarnings("unchecked")
    void vicePrincipalSearchIsLimitedToManagedCampusClassrooms() {
        given(staffAccessPolicy.managedCampusIds(VICE)).willReturn(Set.of(10L));
        given(classroomService.findClassroomIdsOfCampuses(Set.of(10L))).willReturn(List.of(11L, 12L));
        given(childRepository.searchEnrolled(eq(List.of(11L, 12L)), eq(TODAY), eq("%an%"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(child(101L))));
        given(enrollmentService.findActiveClassroomIdByChildIds(List.of(101L), TODAY))
                .willReturn(Map.of(101L, 11L));
        given(classroomService.findCampusIds(any(Collection.class))).willReturn(Map.of(11L, 10L));
        given(guardianChildLinkService.countGuardiansByChildIds(List.of(101L))).willReturn(Map.of(101L, 2L));

        var page = childService.searchChildren(VICE, null, null, " An ", 0, 20);

        assertThat(page.items()).singleElement().satisfies(summary -> {
            assertThat(summary.classroomId()).isEqualTo(11L);
            assertThat(summary.campusId()).isEqualTo(10L);
            assertThat(summary.guardianCount()).isEqualTo(2L);
        });
    }

    @Test
    void vicePrincipalCannotSearchOtherCampusOrClassroom() {
        given(staffAccessPolicy.managedCampusIds(VICE)).willReturn(Set.of(10L));
        given(classroomService.findCampusId(21L)).willReturn(Optional.of(20L));

        assertThatThrownBy(() -> childService.searchChildren(VICE, 20L, null, null, 0, 20))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CAMPUS_ACCESS_DENIED);
        assertThatThrownBy(() -> childService.searchChildren(VICE, null, 21L, null, 0, 20))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CLASS_ACCESS_DENIED);
    }

    private static Child child(long id) {
        Child child = BeanUtils.instantiateClass(Child.class);
        ReflectionTestUtils.setField(child, "id", id);
        ReflectionTestUtils.setField(child, "fullName", "Trẻ Demo");
        ReflectionTestUtils.setField(child, "dateOfBirth", LocalDate.of(2022, 3, 15));
        return child;
    }
}
