package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.organization.NewStaffAssignment;
import com.carenest.entity.organization.Campus;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.entity.organization.TeacherWorkCampus;
import com.carenest.mapper.organization.StaffAssignmentMapper;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.repository.organization.TeacherWorkCampusRepository;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StaffAssignmentServiceImplTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    private final StaffAssignmentRepository repository = mock(StaffAssignmentRepository.class);
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final CampusRepository campuses = mock(CampusRepository.class);
    private final TeacherWorkCampusRepository workCampuses = mock(TeacherWorkCampusRepository.class);
    private final StaffAssignmentServiceImpl service = new StaffAssignmentServiceImpl(
            repository,
            new StaffAssignmentMapper(),
            classrooms,
            campuses,
            mock(SchoolYearRepository.class),
            mock(TeacherClassRules.class),
            workCampuses,
            Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneOffset.UTC));

    @Test
    void scopeShapeFollowsRole() {
        given(campuses.existsById(10L)).willReturn(true);
        given(classrooms.findCampusIdById(11L)).willReturn(Optional.of(10L));

        assertThat(service.resolveCampusId(StaffRole.KITCHEN_STAFF, 10L, null)).isEqualTo(10L);
        assertThat(service.resolveCampusId(StaffRole.TEACHER, null, 11L)).isEqualTo(10L);
        assertThat(service.resolveCampusId(StaffRole.PRINCIPAL, null, null)).isNull();
        assertBadRequest(StaffRole.TEACHER, 10L, null);
        assertBadRequest(StaffRole.VICE_PRINCIPAL, null, 11L);
        assertBadRequest(StaffRole.PRINCIPAL, 10L, null);
    }

    @Test
    void unknownCampusOrClassroomIsNotFound() {
        given(classrooms.findCampusIdById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveCampusId(StaffRole.VICE_PRINCIPAL, 99L, null))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CAMPUS_NOT_FOUND);
        assertThatThrownBy(() -> service.resolveCampusId(StaffRole.TEACHER, null, 99L))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CLASS_NOT_FOUND);
    }

    @Test
    void overlappingOpenAssignmentIsConflict() {
        given(campuses.existsById(10L)).willReturn(true);
        given(repository.existsOverlappingOpenEnded(40L, StaffRole.KITCHEN_STAFF, 10L, null, START, null))
                .willReturn(true);

        assertThatThrownBy(() -> service.createAssignment(
                        new NewStaffAssignment(40L, StaffRole.KITCHEN_STAFF, 10L, null, START, null, 1L, false)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ASSIGNMENT_OVERLAP);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void campusWithAnotherVicePrincipalIsConflict() {
        // AUTH-26: tạo tài khoản/đổi vai trò kèm phân công PHT cũng áp mỗi điểm trường tối đa một PHT
        given(campuses.existsById(10L)).willReturn(true);
        given(repository.existsOtherVicePrincipalAtCampusOpenEnded(10L, 41L, START, null))
                .willReturn(true);

        assertThatThrownBy(() -> service.createAssignment(
                        new NewStaffAssignment(41L, StaffRole.VICE_PRINCIPAL, 10L, null, START, null, 1L, false)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void endDateBeforeStartIsRejectedAndEndedAssignmentCannotBeExtended() {
        StaffAssignment assignment =
                StaffAssignment.create(40L, StaffRole.TEACHER, null, 11L, START, START.plusDays(5));
        given(repository.findById(5L)).willReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.endAssignment(5L, START.minusDays(1)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ASSIGNMENT_END_BEFORE_START);
        assertThatThrownBy(() -> service.endAssignment(5L, START.plusDays(9)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ASSIGNMENT_ALREADY_ENDED);
    }

    @Test
    void teamLeaderIsNotAssignedThroughGenericStaffAssignment() {
        assertThatThrownBy(() -> service.resolveCampusId(StaffRole.AGE_GROUP_TEAM_LEADER, null, null))
                .extracting("apiCode")
                .isEqualTo(ApiCode.TEAM_LEADER_ASSIGNMENT_NOT_HERE);
    }

    // Mỗi sai hình dạng phạm vi có mã riêng; helper chỉ cần biết là 400
    private void assertBadRequest(StaffRole role, Long campusId, Long classroomId) {
        assertThatThrownBy(() -> service.resolveCampusId(role, campusId, classroomId))
                .extracting("apiCode.httpStatus")
                .isEqualTo(ApiCode.BAD_REQUEST.getHttpStatus());
    }

    @Test
    void teacherWorkCampusMustBeActiveAndMovesWhenPresent() {
        Campus active = Campus.create("CS1", "Cơ sở 1", "CS1", "Địa chỉ", null);
        Campus inactive = Campus.create("CS2", "Cơ sở 2", "CS2", "Địa chỉ", null);
        inactive.deactivate(Instant.parse("2026-09-01T00:00:00Z"));
        given(campuses.findById(10L)).willReturn(Optional.of(active));
        given(campuses.findById(11L)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.saveTeacherWorkCampus(7L, 11L, 1L))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CAMPUS_INACTIVE);
        assertThatThrownBy(() -> service.saveTeacherWorkCampus(7L, 12L, 1L))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CAMPUS_NOT_FOUND);
        verify(workCampuses, never()).save(any());

        service.saveTeacherWorkCampus(7L, 10L, 1L);
        verify(workCampuses).save(any(TeacherWorkCampus.class));

        TeacherWorkCampus existing = TeacherWorkCampus.of(8L, 20L, 1L);
        given(workCampuses.findById(8L)).willReturn(Optional.of(existing));
        service.saveTeacherWorkCampus(8L, 10L, 2L);
        assertThat(existing.getCampusId()).isEqualTo(10L);
        assertThat(existing.getUpdatedBy()).isEqualTo(2L);
    }
}
