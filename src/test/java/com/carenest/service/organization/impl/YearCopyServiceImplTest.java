package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.organization.ClassTeachersRequest;
import com.carenest.dto.organization.CutoffRequest;
import com.carenest.dto.organization.SchoolYearSemesterRequest;
import com.carenest.dto.organization.TeacherCandidateResponse;
import com.carenest.dto.organization.YearCopyRequest;
import com.carenest.dto.organization.YearCopyResponse;
import com.carenest.entity.organization.AttendanceCutoff;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.AttendanceCutoffRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AttendanceCutoffService;
import com.carenest.service.organization.ClassManagementService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.TeacherAssignmentService;
import com.carenest.service.organization.TeacherDirectory;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.YearCopyPart;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Chép năm học: giữ dữ liệu năm đích đã có, bỏ GV đã nghỉ kèm lý do, chỉ chép vào năm chưa bắt đầu. */
class YearCopyServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
    private static final CurrentUser PRINCIPAL =
            new CurrentUser(2L, "kc-2", "principal.dev", "Demo", null, Set.of(CareNestRole.PRINCIPAL));

    private final SchoolYearRepository schoolYearRepository = mock(SchoolYearRepository.class);
    private final ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
    private final StaffAssignmentRepository staffAssignmentRepository = mock(StaffAssignmentRepository.class);
    private final AttendanceCutoffRepository cutoffRepository = mock(AttendanceCutoffRepository.class);
    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final TeacherDirectory teacherDirectory = mock(TeacherDirectory.class);
    private final SchoolConfigAccess access = mock(SchoolConfigAccess.class);
    private final TeacherAssignmentService teacherAssignmentService = mock(TeacherAssignmentService.class);
    private final AttendanceCutoffService cutoffService = mock(AttendanceCutoffService.class);
    private final SchoolYearService schoolYearService = mock(SchoolYearService.class);
    private final com.carenest.service.organization.VicePrincipalAssignmentService viceService =
            mock(com.carenest.service.organization.VicePrincipalAssignmentService.class);
    private final YearCopyServiceImpl service = new YearCopyServiceImpl(
            schoolYearRepository,
            classroomRepository,
            staffAssignmentRepository,
            cutoffRepository,
            mock(AgeGroupRepository.class),
            roomRepository,
            teacherDirectory,
            access,
            mock(ClassManagementService.class),
            teacherAssignmentService,
            viceService,
            cutoffService,
            schoolYearService);

    private SchoolYear source;
    private SchoolYear target;

    @BeforeEach
    void setUp() {
        given(access.today()).willReturn(TODAY);
        source = year(1L, "2026-2027", SchoolYearStatus.ACTIVE, "2026-09-05", "2027-05-31");
        target = year(2L, "2027-2028", SchoolYearStatus.PLANNED, "2027-09-05", "2028-05-31");
        given(schoolYearRepository.findById(1L)).willReturn(Optional.of(source));
        given(schoolYearRepository.findById(2L)).willReturn(Optional.of(target));
    }

    @Test
    void copyIntoActiveYearIsRejected() {
        assertThatThrownBy(() -> service.copyYear(PRINCIPAL, 1L, new YearCopyRequest(2L, Set.of(YearCopyPart.CUTOFF))))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.YEAR_COPY_TARGET_NOT_PLANNED);
    }

    @Test
    void emptyPartsAreRejected() {
        assertThatThrownBy(() -> service.copyYear(PRINCIPAL, 2L, new YearCopyRequest(1L, Set.of())))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.YEAR_COPY_PARTS_REQUIRED);
    }

    @Test
    void teachersFollowSameClassNameAndLockedTeacherIsSkipped() {
        Classroom oldClass = classroom(10L, 1L, 5L, "Mầm 1");
        Classroom newClass = classroom(20L, 2L, 5L, " mầm 1 ");
        given(classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(1L))
                .willReturn(List.of(oldClass));
        given(classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(2L))
                .willReturn(List.of(newClass));
        given(staffAssignmentRepository.findCurrentTeachers(eq(List.of(20L)), any()))
                .willReturn(List.of());
        given(staffAssignmentRepository.findCurrentTeachers(eq(List.of(10L)), any()))
                .willReturn(List.of(
                        StaffAssignment.teacher(7L, 10L, true, source.getStartDate(), source.getEndDate(), 2L),
                        StaffAssignment.teacher(8L, 10L, false, source.getStartDate(), source.getEndDate(), 2L)));
        given(staffAssignmentRepository.findInactiveUserIds(anyCollection())).willReturn(List.of(8L));
        given(roomRepository.findUserNames(anyCollection())).willReturn(List.of());
        given(teacherDirectory.findTeachers(TODAY))
                .willReturn(List.of(new TeacherCandidateResponse(7L, "Cô Lan", 5L, Set.of(5L))));

        YearCopyResponse result =
                service.copyYear(PRINCIPAL, 2L, new YearCopyRequest(1L, Set.of(YearCopyPart.TEACHERS)));

        assertThat(result.teacherClasses()).isEqualTo(1);
        assertThat(result.skipped()).singleElement().satisfies(s -> {
            assertThat(s.part()).isEqualTo(YearCopyPart.TEACHERS);
            assertThat(s.reason()).contains("#8");
        });
        verify(teacherAssignmentService).assignClassTeachers(PRINCIPAL, 20L, new ClassTeachersRequest(List.of(7L), 7L));
    }

    @Test
    void classWithTeachersInTargetIsKept() {
        Classroom oldClass = classroom(10L, 1L, 5L, "Mầm 1");
        Classroom newClass = classroom(20L, 2L, 5L, "Mầm 1");
        given(classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(1L))
                .willReturn(List.of(oldClass));
        given(classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(2L))
                .willReturn(List.of(newClass));
        given(staffAssignmentRepository.findCurrentTeachers(eq(List.of(20L)), any()))
                .willReturn(List.of(StaffAssignment.teacher(9L, 20L, true, target.getStartDate(), null, 2L)));
        given(staffAssignmentRepository.findCurrentTeachers(eq(List.of(10L)), any()))
                .willReturn(List.of(StaffAssignment.teacher(7L, 10L, true, source.getStartDate(), null, 2L)));

        YearCopyResponse result =
                service.copyYear(PRINCIPAL, 2L, new YearCopyRequest(1L, Set.of(YearCopyPart.TEACHERS)));

        assertThat(result.teacherClasses()).isZero();
        verify(teacherAssignmentService, never()).assignClassTeachers(any(), any(), any());
    }

    @Test
    void cutoffAndSemesterMoveToTargetYear() {
        source.changeSemesters(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2027, 1, 15),
                LocalDate.of(2027, 1, 18),
                LocalDate.of(2027, 5, 25));
        given(cutoffRepository.findAllBySchoolYearIdOrderByIdDesc(2L)).willReturn(List.of());
        given(cutoffRepository.findAllBySchoolYearIdOrderByIdDesc(1L))
                .willReturn(List.of(AttendanceCutoff.create(1L, LocalTime.of(8, 30), source.getStartDate(), "", 2L)));

        YearCopyResponse result = service.copyYear(
                PRINCIPAL, 2L, new YearCopyRequest(1L, Set.of(YearCopyPart.CUTOFF, YearCopyPart.SEMESTER)));

        assertThat(result.cutoffTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(result.semesterCopied()).isTrue();
        verify(cutoffService)
                .saveCutoff(
                        PRINCIPAL,
                        2L,
                        new CutoffRequest(LocalTime.of(8, 30), target.getStartDate(), "Sao chép từ năm học 2026-2027"));
        verify(schoolYearService)
                .updateSemester(
                        PRINCIPAL,
                        2L,
                        new SchoolYearSemesterRequest(
                                LocalDate.of(2027, 9, 5),
                                LocalDate.of(2028, 1, 15),
                                LocalDate.of(2028, 1, 18),
                                LocalDate.of(2028, 5, 25)));
    }

    @Test
    void missingSourceCutoffIsReportedNotThrown() {
        given(cutoffRepository.findAllBySchoolYearIdOrderByIdDesc(any())).willReturn(List.of());

        YearCopyResponse result = service.copyYear(PRINCIPAL, 2L, new YearCopyRequest(1L, Set.of(YearCopyPart.CUTOFF)));

        assertThat(result.cutoffTime()).isNull();
        assertThat(result.skipped())
                .singleElement()
                .extracting(YearCopyResponse.Skipped::part)
                .isEqualTo(YearCopyPart.CUTOFF);
    }

    @Test
    void vicePrincipalsMoveToTargetYearKeepingExistingAndSkippingLocked() {
        given(viceService.getAssignments(PRINCIPAL, 1L))
                .willReturn(viceRows(
                        viceRow(30L, "PHT A", "ACTIVE", 7L),
                        viceRow(31L, "PHT B", "ACTIVE", 8L),
                        viceRow(32L, "PHT khóa", "INACTIVE", 9L),
                        viceRow(33L, "PHT chưa phân công", "ACTIVE", null)));
        // Năm đích đã có phân công của PHT B ⇒ giữ, không ghi đè
        given(viceService.getAssignments(PRINCIPAL, 2L)).willReturn(viceRows(viceRow(31L, "PHT B", "ACTIVE", 9L)));

        YearCopyResponse result =
                service.copyYear(PRINCIPAL, 2L, new YearCopyRequest(1L, Set.of(YearCopyPart.VICE_PRINCIPALS)));

        assertThat(result.vicePrincipals()).isEqualTo(1);
        verify(viceService)
                .assign(PRINCIPAL, 2L, 30L, new com.carenest.dto.organization.VicePrincipalAssignmentRequest(7L));
        verify(viceService, never()).assign(eq(PRINCIPAL), eq(2L), eq(31L), any());
        verify(viceService, never()).assign(eq(PRINCIPAL), eq(2L), eq(32L), any());
        assertThat(result.skipped())
                .singleElement()
                .extracting(YearCopyResponse.Skipped::part, YearCopyResponse.Skipped::name)
                .containsExactly(YearCopyPart.VICE_PRINCIPALS, "PHT khóa");
    }

    private static com.carenest.dto.organization.VicePrincipalAssignmentResponse viceRows(
            com.carenest.dto.organization.VicePrincipalAssignmentResponse.Row... rows) {
        return new com.carenest.dto.organization.VicePrincipalAssignmentResponse(null, List.of(rows));
    }

    private static com.carenest.dto.organization.VicePrincipalAssignmentResponse.Row viceRow(
            Long id, String name, String status, Long campusId) {
        return new com.carenest.dto.organization.VicePrincipalAssignmentResponse.Row(
                new com.carenest.dto.organization.VicePrincipalAssignmentResponse.VicePrincipal(id, name, null, status),
                campusId == null
                        ? null
                        : new com.carenest.dto.organization.VicePrincipalAssignmentResponse.Assignment(
                                campusId, LocalDate.of(2026, 9, 5), LocalDate.of(2027, 5, 31), 2L));
    }

    private static SchoolYear year(Long id, String name, SchoolYearStatus status, String start, String end) {
        SchoolYear year = SchoolYear.plan(name, LocalDate.parse(start), LocalDate.parse(end));
        ReflectionTestUtils.setField(year, "id", id);
        ReflectionTestUtils.setField(year, "status", status);
        return year;
    }

    private static Classroom classroom(Long id, Long yearId, Long campusId, String name) {
        Classroom c = Classroom.create(yearId, campusId);
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "name", name);
        return c;
    }
}
