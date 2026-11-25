package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.repository.child.ChildPlacementRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlacementRulesTest {

    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final StaffAccessPolicy staffAccessPolicy = mock(StaffAccessPolicy.class);
    private final PlacementRules rules = new PlacementRules(
            enrollmentRepository,
            mock(ChildPlacementRepository.class),
            staffAccessPolicy,
            mock(StaffAssignmentService.class),
            mock(NotificationService.class));

    @Test
    @DisplayName("G94-180/CHD-13: lớp không xét nhóm tuổi của trẻ (chỉ gợi ý); vẫn chặn khi đủ sĩ số từ ngày vào lớp")
    void classIgnoresAgeGroupButKeepsCapacity() {
        CurrentUser principal = TestPermissions.userWithDefaults(1L, CareNestRole.PRINCIPAL);
        given(staffAccessPolicy.managesWholeSchool(principal)).willReturn(true);
        LocalDate from = LocalDate.of(2026, 11, 23);
        given(enrollmentRepository.countOccupyingFrom(10L, from, 7L)).willReturn(24L);

        assertThat(rules.classProblem(principal, classroom(), from, 7L)).isNull();

        given(enrollmentRepository.countOccupyingFrom(10L, from, 7L)).willReturn(25L);
        assertThat(rules.classProblem(principal, classroom(), from, 7L))
                .isEqualTo("Lớp A1 đã đủ 25 trẻ. Chọn lớp khác.");
    }

    private static ClassroomInfoResponse classroom() {
        return new ClassroomInfoResponse(
                10L,
                "Lớp A1",
                2L,
                5L,
                SchoolYearStatus.ACTIVE,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 5, 31),
                3L,
                List.of(),
                25,
                ClassroomStatus.ACTIVE);
    }
}
