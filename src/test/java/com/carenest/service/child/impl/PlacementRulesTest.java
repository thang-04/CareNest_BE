package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.repository.child.ChildPlacementRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlacementRulesTest {

    private final AgeGroupService ageGroupService = mock(AgeGroupService.class);
    private final PlacementRules rules = new PlacementRules(
            mock(EnrollmentRepository.class),
            mock(ChildPlacementRepository.class),
            mock(StaffAccessPolicy.class),
            mock(StaffAssignmentService.class),
            mock(NotificationService.class),
            ageGroupService);

    @Test
    void ageIsCalculatedAtSchoolYearStartAndMustFitClassGroup() {
        ClassroomInfoResponse classroom = classroom();
        given(ageGroupService.getYearAgeGroups(5L))
                .willReturn(List.of(new AgeGroupDetailResponse(
                        3L, "ag-3", "Mẫu giáo 3–4 tuổi", "Mẫu giáo 3–4", (short) 4, 36, 48, List.of(), 0, 25, "")));

        assertThat(rules.ageGroupProblem(classroom, LocalDate.of(2023, 9, 1))).isNull();
        assertThat(rules.ageGroupProblem(classroom, LocalDate.of(2023, 9, 2)))
                .contains("35 tháng")
                .contains("không thuộc Mẫu giáo 3–4 tuổi");
        assertThat(rules.ageGroupProblem(classroom, LocalDate.of(2022, 9, 2))).isNull();
        assertThat(rules.ageGroupProblem(classroom, LocalDate.of(2022, 9, 1)))
                .contains("48 tháng")
                .contains("không thuộc Mẫu giáo 3–4 tuổi");
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
