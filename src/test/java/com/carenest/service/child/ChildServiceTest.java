package com.carenest.service.child;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.carenest.security.CareNestRole;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ChildServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final CurrentUser TEACHER =
            new CurrentUser(7L, "kc-7", "teacher.a", "Demo", null, Set.of(CareNestRole.TEACHER));

    private final ChildRepository childRepository = mock(ChildRepository.class);
    private final EnrollmentService enrollmentService = mock(EnrollmentService.class);
    private final ChildAccessPolicy childAccessPolicy = mock(ChildAccessPolicy.class);
    private final ChildService childService = new ChildService(childRepository, enrollmentService, childAccessPolicy,
            new ChildMapper(), FIXED_CLOCK);

    @Test
    void shouldReturnProfileWithCurrentClassroomWhenInScope() {
        given(childRepository.findById(101L)).willReturn(Optional.of(child(101L)));
        given(enrollmentService.findActiveClassroomIds(101L, TODAY)).willReturn(List.of(11L));

        ChildProfileResponse response = childService.getChildProfile(TEACHER, 101L);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.classroomId()).isEqualTo(11L);
        verify(childAccessPolicy).checkCanViewChild(TEACHER, 101L);
    }

    @Test
    void shouldNotLoadChildWhenOutOfScope() {
        willThrow(new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this child"))
                .given(childAccessPolicy).checkCanViewChild(TEACHER, 201L);

        assertThatThrownBy(() -> childService.getChildProfile(TEACHER, 201L))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode").isEqualTo(ApiCode.FORBIDDEN);
        verify(childRepository, never()).findById(anyLong());
    }

    @Test
    void shouldReturn404OnlyAfterScopeCheckPassed() {
        given(childRepository.findById(101L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> childService.getChildProfile(TEACHER, 101L))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode").isEqualTo(ApiCode.NOT_FOUND);
    }

    private static Child child(long id) {
        Child child = BeanUtils.instantiateClass(Child.class);
        ReflectionTestUtils.setField(child, "id", id);
        ReflectionTestUtils.setField(child, "fullName", "Trẻ Demo");
        ReflectionTestUtils.setField(child, "dateOfBirth", LocalDate.of(2022, 3, 15));
        return child;
    }
}
