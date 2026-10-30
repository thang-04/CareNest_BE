package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.config.DevActivationProperties;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.SchoolYearRequest;
import com.carenest.dto.organization.SchoolYearSemesterRequest;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AttendanceCutoffRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SchoolYearServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final CurrentUser PRINCIPAL =
            new CurrentUser(2L, "kc-2", "principal.dev", "Demo", null, Set.of(CareNestRole.PRINCIPAL));
    private static final CurrentUser VICE_PRINCIPAL =
            new CurrentUser(3L, "kc-3", "viceprincipal.a", "Demo", null, Set.of(CareNestRole.VICE_PRINCIPAL));

    private final SchoolYearRepository schoolYearRepository = mock(SchoolYearRepository.class);
    private final ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
    private final StaffAssignmentService staffAssignmentService = mock(StaffAssignmentService.class);
    private final SchoolYearServiceImpl service = new SchoolYearServiceImpl(
            schoolYearRepository,
            classroomRepository,
            mock(AttendanceCutoffRepository.class),
            staffAssignmentService,
            new SchoolStructureMapper(),
            FIXED_CLOCK,
            new DevActivationProperties(false, false, false));

    @BeforeEach
    void principalHasActiveAssignment() {
        given(staffAssignmentService.hasActivePrincipalAssignment(2L, TODAY)).willReturn(true);
        given(schoolYearRepository.save(any(SchoolYear.class))).willAnswer(invocation -> {
            SchoolYear saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
        given(schoolYearRepository.findOverlapping(any(), any(), any())).willReturn(List.of());
    }

    @Test
    void createsPlannedYear() {
        SchoolYearDetailResponse created = service.createSchoolYear(PRINCIPAL, request(" 2027-2028 ", "2027-09-05"));

        assertThat(created.id()).isEqualTo(99L);
        assertThat(created.name()).isEqualTo("2027-2028");
        assertThat(created.status()).isEqualTo(SchoolYearStatus.PLANNED);
        assertThat(created.classCount()).isZero();
    }

    @Test
    void vicePrincipalCannotCreate() {
        expectError(
                () -> service.createSchoolYear(VICE_PRINCIPAL, request("2027-2028", "2027-09-05")), ApiCode.FORBIDDEN);
        verify(schoolYearRepository, never()).save(any());
    }

    @Test
    void principalWithoutActiveAssignmentCannotCreate() {
        given(staffAssignmentService.hasActivePrincipalAssignment(2L, TODAY)).willReturn(false);

        expectError(() -> service.createSchoolYear(PRINCIPAL, request("2027-2028", "2027-09-05")), ApiCode.FORBIDDEN);
    }

    @Test
    void nameMustBeConsecutiveYears() {
        expectError(() -> service.createSchoolYear(PRINCIPAL, request("2027-2029", "2027-09-05")), ApiCode.BAD_REQUEST);
    }

    @Test
    void endDateMustBeAfterStartDate() {
        SchoolYearRequest request =
                new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2027, 9, 5));

        expectError(() -> service.createSchoolYear(PRINCIPAL, request), ApiCode.BAD_REQUEST);
    }

    @Test
    void duplicatedNameIsConflict() {
        given(schoolYearRepository.existsByName("2027-2028")).willReturn(true);

        expectError(() -> service.createSchoolYear(PRINCIPAL, request("2027-2028", "2027-09-05")), ApiCode.CONFLICT);
    }

    @Test
    void overlappingDatesIsConflict() {
        given(schoolYearRepository.findOverlapping(any(), any(), any()))
                .willReturn(List.of(year(1L, "2026-2027", SchoolYearStatus.ACTIVE, "2026-09-01")));

        expectError(() -> service.createSchoolYear(PRINCIPAL, request("2027-2028", "2027-05-01")), ApiCode.CONFLICT);
    }

    @Test
    void closedYearIsReadOnly() {
        given(schoolYearRepository.findById(1L))
                .willReturn(Optional.of(year(1L, "2025-2026", SchoolYearStatus.CLOSED, "2025-09-01")));

        expectError(
                () -> service.updateSchoolYear(PRINCIPAL, 1L, request("2025-2026", "2025-09-01")), ApiCode.CONFLICT);
    }

    @Test
    void activeYearCannotBeRenamed() {
        given(schoolYearRepository.findById(1L))
                .willReturn(Optional.of(year(1L, "2026-2027", SchoolYearStatus.ACTIVE, "2026-09-01")));

        expectError(
                () -> service.updateSchoolYear(PRINCIPAL, 1L, request("2027-2028", "2027-09-01")), ApiCode.CONFLICT);
    }

    @Test
    void plannedYearWithClassesCannotBeRenamed() {
        given(schoolYearRepository.findById(5L))
                .willReturn(Optional.of(year(5L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-01")));
        given(classroomRepository.countBySchoolYearId(5L)).willReturn(2L);

        expectError(
                () -> service.updateSchoolYear(PRINCIPAL, 5L, request("2028-2029", "2028-09-01")), ApiCode.CONFLICT);
    }

    @Test
    void plannedYearCanChangeDates() {
        SchoolYear planned = year(5L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-01");
        given(schoolYearRepository.findById(5L)).willReturn(Optional.of(planned));

        SchoolYearDetailResponse updated = service.updateSchoolYear(PRINCIPAL, 5L, request("2027-2028", "2027-09-06"));

        assertThat(updated.startDate()).isEqualTo(LocalDate.of(2027, 9, 6));
        verify(schoolYearRepository).findOverlapping(LocalDate.of(2027, 9, 6), LocalDate.of(2028, 5, 31), 5L);
    }

    @Test
    void principalSetsBothSemesters() {
        given(schoolYearRepository.findById(5L))
                .willReturn(Optional.of(year(5L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-01")));

        SchoolYearDetailResponse updated = service.updateSemester(
                PRINCIPAL, 5L, semesters("2027-09-01", "2028-01-14", "2028-01-17", "2028-05-31"));

        assertThat(updated.semester1Start()).isEqualTo(LocalDate.of(2027, 9, 1));
        assertThat(updated.semester2End()).isEqualTo(LocalDate.of(2028, 5, 31));
    }

    @Test
    void semesterDatesAreValidated() {
        given(schoolYearRepository.findById(5L))
                .willReturn(Optional.of(year(5L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-01")));

        expectError(
                () -> service.updateSemester(
                        PRINCIPAL, 5L, new SchoolYearSemesterRequest(LocalDate.of(2027, 9, 1), null, null, null)),
                ApiCode.SEMESTER_DATE_REQUIRED);
        expectError(
                () -> service.updateSemester(
                        PRINCIPAL, 5L, semesters("2027-08-31", "2028-01-14", "2028-01-17", "2028-05-31")),
                ApiCode.SEMESTER_OUT_OF_YEAR);
        expectError(
                () -> service.updateSemester(
                        PRINCIPAL, 5L, semesters("2027-09-01", "2028-01-14", "2028-01-17", "2028-06-01")),
                ApiCode.SEMESTER_OUT_OF_YEAR);
        expectError(
                () -> service.updateSemester(
                        PRINCIPAL, 5L, semesters("2027-12-01", "2027-11-30", "2028-01-17", "2028-05-31")),
                ApiCode.SEMESTER_START_AFTER_END);
        expectError(
                () -> service.updateSemester(
                        PRINCIPAL, 5L, semesters("2027-09-01", "2028-01-14", "2028-05-31", "2028-05-30")),
                ApiCode.SEMESTER_START_AFTER_END);
        expectError(
                () -> service.updateSemester(
                        PRINCIPAL, 5L, semesters("2027-09-01", "2028-01-14", "2028-01-14", "2028-05-31")),
                ApiCode.SEMESTER_OVERLAP);
    }

    @Test
    void yearDatesCannotCutSetSemesters() {
        SchoolYear planned = year(5L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-01");
        planned.changeSemesters(
                LocalDate.of(2027, 9, 1),
                LocalDate.of(2028, 1, 14),
                LocalDate.of(2028, 1, 17),
                LocalDate.of(2028, 5, 31));
        given(schoolYearRepository.findById(5L)).willReturn(Optional.of(planned));

        expectError(
                () -> service.updateSchoolYear(PRINCIPAL, 5L, request("2027-2028", "2027-09-06")),
                ApiCode.SEMESTER_OUT_OF_YEAR);
    }

    @Test
    void deleteOnlyPlannedYear() {
        given(schoolYearRepository.findById(1L))
                .willReturn(Optional.of(year(1L, "2026-2027", SchoolYearStatus.ACTIVE, "2026-09-01")));

        expectError(() -> service.deleteSchoolYear(PRINCIPAL, 1L), ApiCode.CONFLICT);
        verify(schoolYearRepository, never()).delete(any());
    }

    @Test
    void deletePlannedYearRemovesItsClassesAndAssignments() {
        SchoolYear planned = year(5L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-01");
        given(schoolYearRepository.findById(5L)).willReturn(Optional.of(planned));
        given(classroomRepository.countBySchoolYearId(5L)).willReturn(1L);

        service.deleteSchoolYear(PRINCIPAL, 5L);

        verify(schoolYearRepository).deletePlannedYearAssignments(5L);
        verify(schoolYearRepository).deletePlannedYearClasses(5L);
        verify(schoolYearRepository).delete(planned);
    }

    @Test
    void activationClosesCurrentActiveYear() {
        SchoolYear current = year(1L, "2025-2026", SchoolYearStatus.ACTIVE, "2025-09-01");
        SchoolYear next = year(5L, "2026-2027", SchoolYearStatus.PLANNED, "2026-10-25");
        given(schoolYearRepository.findById(5L)).willReturn(Optional.of(next));
        given(schoolYearRepository.findFirstByStatus(SchoolYearStatus.ACTIVE)).willReturn(Optional.of(current));

        SchoolYearDetailResponse activated = service.activateSchoolYear(PRINCIPAL, 5L, " 2026-2027 ");

        assertThat(activated.status()).isEqualTo(SchoolYearStatus.ACTIVE);
        assertThat(activated.activatedBy()).isEqualTo(2L);
        assertThat(current.getStatus()).isEqualTo(SchoolYearStatus.CLOSED);
        assertThat(current.getClosedBy()).isEqualTo(2L);
    }

    @Test
    void activationRequiresExactName() {
        given(schoolYearRepository.findById(5L))
                .willReturn(Optional.of(year(5L, "2026-2027", SchoolYearStatus.PLANNED, "2026-10-25")));

        expectError(() -> service.activateSchoolYear(PRINCIPAL, 5L, "2026"), ApiCode.BAD_REQUEST);
    }

    @Test
    void activationTooEarlyIsConflict() {
        given(schoolYearRepository.findById(5L))
                .willReturn(Optional.of(year(5L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-01")));

        expectError(() -> service.activateSchoolYear(PRINCIPAL, 5L, "2027-2028"), ApiCode.CONFLICT);
        verify(schoolYearRepository, never()).findFirstByStatus(any());
    }

    @Test
    void onlyPlannedYearCanBeActivated() {
        given(schoolYearRepository.findById(1L))
                .willReturn(Optional.of(year(1L, "2025-2026", SchoolYearStatus.CLOSED, "2025-09-01")));

        expectError(() -> service.activateSchoolYear(PRINCIPAL, 1L, "2025-2026"), ApiCode.CONFLICT);
    }

    @Test
    void closeOnlyActiveYear() {
        given(schoolYearRepository.findById(5L))
                .willReturn(Optional.of(year(5L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-01")));

        expectError(() -> service.closeSchoolYear(PRINCIPAL, 5L), ApiCode.CONFLICT);
    }

    @Test
    void closeBeforeEndDateIsConflict() {
        // Năm 2026-2027 kết thúc 31/05/2027, hôm nay 22/10/2026
        given(schoolYearRepository.findById(1L))
                .willReturn(Optional.of(year(1L, "2026-2027", SchoolYearStatus.ACTIVE, "2026-09-01")));

        expectError(() -> service.closeSchoolYear(PRINCIPAL, 1L), ApiCode.CONFLICT);
    }

    @Test
    void activationOutOfOrderIsConflict() {
        SchoolYear first = year(4L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-05");
        given(schoolYearRepository.findById(6L))
                .willReturn(Optional.of(year(6L, "2029-2030", SchoolYearStatus.PLANNED, "2029-09-05")));
        given(schoolYearRepository.findFirstByStatusOrderByStartDateAsc(SchoolYearStatus.PLANNED))
                .willReturn(Optional.of(first));

        expectError(() -> service.activateSchoolYear(PRINCIPAL, 6L, "2029-2030"), ApiCode.CONFLICT);
        verify(schoolYearRepository, never()).findFirstByStatus(any());
    }

    @Test
    void closesActiveYearAfterEndDate() {
        given(schoolYearRepository.findById(1L))
                .willReturn(Optional.of(year(1L, "2025-2026", SchoolYearStatus.ACTIVE, "2025-09-01")));

        SchoolYearDetailResponse closed = service.closeSchoolYear(PRINCIPAL, 1L);

        assertThat(closed.status()).isEqualTo(SchoolYearStatus.CLOSED);
        assertThat(closed.closedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    @Test
    void closedYearIsReadOnlyForEveryModule() {
        SchoolYear closed = year(1L, "2025-2026", SchoolYearStatus.CLOSED, "2025-09-01");
        given(schoolYearRepository.findById(1L)).willReturn(Optional.of(closed));
        given(schoolYearRepository.findContaining(LocalDate.parse("2026-01-05")))
                .willReturn(Optional.of(closed));

        expectError(() -> service.checkYearOpen(1L), ApiCode.CONFLICT);
        expectError(() -> service.checkDateYearOpen(LocalDate.parse("2026-01-05")), ApiCode.CONFLICT);
    }

    @Test
    void openYearAndDateOutsideYearsPassCheck() {
        given(schoolYearRepository.findById(5L))
                .willReturn(Optional.of(year(5L, "2026-2027", SchoolYearStatus.ACTIVE, "2026-09-01")));
        given(schoolYearRepository.findContaining(any())).willReturn(Optional.empty());

        service.checkYearOpen(5L);
        service.checkDateYearOpen(LocalDate.parse("2026-07-01"));
    }

    @Test
    void unknownYearIsNotFound() {
        given(schoolYearRepository.findById(anyLong())).willReturn(Optional.empty());

        expectError(() -> service.getSchoolYear(VICE_PRINCIPAL, 404L), ApiCode.NOT_FOUND);
    }

    private static SchoolYearSemesterRequest semesters(String s1Start, String s1End, String s2Start, String s2End) {
        return new SchoolYearSemesterRequest(
                LocalDate.parse(s1Start), LocalDate.parse(s1End), LocalDate.parse(s2Start), LocalDate.parse(s2End));
    }

    private static SchoolYearRequest request(String name, String startDate) {
        LocalDate start = LocalDate.parse(startDate);
        return new SchoolYearRequest(name, start, LocalDate.of(start.getYear() + 1, 5, 31));
    }

    private static SchoolYear year(Long id, String name, SchoolYearStatus status, String startDate) {
        LocalDate start = LocalDate.parse(startDate);
        SchoolYear year = SchoolYear.plan(name, start, LocalDate.of(start.getYear() + 1, 5, 31));
        ReflectionTestUtils.setField(year, "id", id);
        ReflectionTestUtils.setField(year, "status", status);
        return year;
    }

    private static void expectError(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(apiCode.getHttpStatus());
    }
}
