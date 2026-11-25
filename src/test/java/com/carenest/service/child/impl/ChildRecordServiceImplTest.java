package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.child.ChildAddressInput;
import com.carenest.dto.child.ChildEditDraft;
import com.carenest.dto.child.ChildProfileInput;
import com.carenest.dto.child.EnrollmentChildInput;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.dto.child.WithdrawChildRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.entity.child.ChildHistory;
import com.carenest.entity.child.ChildProfile;
import com.carenest.entity.child.ClassPlacement;
import com.carenest.entity.child.Enrollment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.repository.child.ChildHistoryRepository;
import com.carenest.repository.child.ChildProfileRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.repository.child.ClassPlacementRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildLeaveDataCheck;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.child.YearTransitionService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildHistoryAction;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.PlacementAction;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.YesNo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

class ChildRecordServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZONE);
    private static final Long CHILD_ID = 1L;
    private static final Long CLASS_ID = 10L;
    private static final Long CAMPUS_ID = 2L;
    private static final CurrentUser PRINCIPAL = TestPermissions.userWithDefaults(9L, CareNestRole.PRINCIPAL);
    private static final LocalDate YEAR_END = LocalDate.of(2027, 5, 31);
    // Câu chặn dữ liệu trong ngày của withdraw (G94-180/CHD-03)
    private static final String SAME_DAY_DATA =
            "Trẻ đã có điểm danh, sức khỏe hoặc đánh giá trong hôm nay. Thực hiện nghỉ học từ ngày học tiếp theo.";

    private final ChildRepository childRepository = mock(ChildRepository.class);
    private final ChildProfileRepository profileRepository = mock(ChildProfileRepository.class);
    private final ChildGuardianRepository guardianRepository = mock(ChildGuardianRepository.class);
    private final ChildHistoryRepository historyRepository = mock(ChildHistoryRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final ChildIntakeService intakeService = mock(ChildIntakeService.class);
    private final GuardianChildLinkService linkService = mock(GuardianChildLinkService.class);
    private final ClassroomService classroomService = mock(ClassroomService.class);
    private final StaffAssignmentService assignmentService = mock(StaffAssignmentService.class);
    private final SchoolYearService schoolYearService = mock(SchoolYearService.class);
    private final StaffAccessPolicy policy = mock(StaffAccessPolicy.class);
    private final ChildAccessPolicy childAccessPolicy = mock(ChildAccessPolicy.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final com.carenest.repository.child.ChildHealthDeclarationRepository declarationRepository =
            mock(com.carenest.repository.child.ChildHealthDeclarationRepository.class);
    private final com.carenest.repository.child.ChildAllergyRepository allergyRepository =
            mock(com.carenest.repository.child.ChildAllergyRepository.class);
    private final com.carenest.repository.child.ChildHealthDeclarationHistoryRepository declarationHistoryRepository =
            mock(com.carenest.repository.child.ChildHealthDeclarationHistoryRepository.class);
    private final TbContactNotifier tbNotifier = mock(TbContactNotifier.class);
    private final com.carenest.repository.child.ChildPlacementRepository placementRepository =
            mock(com.carenest.repository.child.ChildPlacementRepository.class);
    private final PlacementRules placementRules = mock(PlacementRules.class);
    private final com.carenest.repository.attendance.AttendanceRecordRepository attendanceRecordRepository =
            mock(com.carenest.repository.attendance.AttendanceRecordRepository.class);
    private final com.carenest.repository.attendance.DailyHealthEntryRepository dailyHealthEntryRepository =
            mock(com.carenest.repository.attendance.DailyHealthEntryRepository.class);
    private final com.carenest.repository.attendance.PickupRecordRepository pickupRecordRepository =
            mock(com.carenest.repository.attendance.PickupRecordRepository.class);
    private final com.carenest.repository.observation.DailyObservationRepository dailyObservationRepository =
            mock(com.carenest.repository.observation.DailyObservationRepository.class);
    private final com.carenest.repository.observation.DailyActivityResultRepository dailyActivityResultRepository =
            mock(com.carenest.repository.observation.DailyActivityResultRepository.class);
    private final ClassPlacementRepository classPlacementRepository = mock(ClassPlacementRepository.class);
    private final ChildLeaveDataCheck leaveDataCheck = mock(ChildLeaveDataCheck.class);
    private final ChildRecordServiceImpl service = new ChildRecordServiceImpl(
            childRepository,
            profileRepository,
            guardianRepository,
            historyRepository,
            enrollmentRepository,
            intakeService,
            linkService,
            classroomService,
            assignmentService,
            policy,
            childAccessPolicy,
            notificationService,
            events,
            JsonMapper.builder().build(),
            CLOCK,
            declarationRepository,
            allergyRepository,
            declarationHistoryRepository,
            tbNotifier,
            placementRepository,
            placementRules,
            org.mockito.Mockito.mock(com.carenest.repository.child.PlannedAdmissionRepository.class),
            List.of(
                    new com.carenest.service.attendance.impl.AttendanceChildDayUsageCheck(
                            attendanceRecordRepository, dailyHealthEntryRepository, pickupRecordRepository),
                    new com.carenest.service.observation.impl.AssessmentChildDayUsageCheck(
                            dailyObservationRepository, dailyActivityResultRepository)),
            schoolYearService,
            classPlacementRepository,
            List.of(leaveDataCheck),
            mock(PlatformTransactionManager.class));

    private Child child;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        given(schoolYearService.isSchoolDay(TODAY)).willReturn(true);
        child = Child.enroll(
                "HS26001",
                "Bé An",
                LocalDate.of(2022, 3, 1),
                Gender.FEMALE,
                true,
                null,
                LocalDate.of(2026, 9, 1),
                true);
        ReflectionTestUtils.setField(child, "id", CHILD_ID);
        enrollment = Enrollment.start(CHILD_ID, CLASS_ID, LocalDate.of(2026, 9, 1));
        given(childRepository.findById(CHILD_ID)).willReturn(Optional.of(child));
        given(policy.managesWholeSchool(PRINCIPAL)).willReturn(true);
        given(enrollmentRepository.findActiveByChildIdIn(List.of(CHILD_ID), TODAY))
                .willReturn(List.of(enrollment));
        given(enrollmentRepository.findOpenByChildId(CHILD_ID, TODAY)).willReturn(List.of(enrollment));
        given(classroomService.findCampusId(CLASS_ID)).willReturn(Optional.of(CAMPUS_ID));
        given(classroomService.findClassroomInfo(CLASS_ID)).willReturn(Optional.of(classroom(CLASS_ID, "Lớp Chồi 1")));
        given(assignmentService.findCurrentTeacherIds(List.of(CLASS_ID), TODAY))
                .willReturn(Map.of(CLASS_ID, List.of(7L, 8L)));
        given(schoolYearService.getSchoolYears(any(), eq(SchoolYearStatus.ACTIVE)))
                .willReturn(List.of(schoolYear(40L, LocalDate.of(2026, 9, 1), YEAR_END, SchoolYearStatus.ACTIVE)));
    }

    @Test
    @DisplayName(
            "CHD-10: chuyển lớp ⇒ lớp cũ kết thúc hôm qua, lớp mới từ hôm nay, ghi lịch sử kèm lý do, báo GV lớp mới")
    void transferEndsOldClassAndStartsNewOne() {
        ClassroomInfoResponse target = classroom(11L, "Lớp Chồi 2");
        given(declarationRepository.existsById(CHILD_ID)).willReturn(true);
        given(classroomService.findClassroomInfo(11L)).willReturn(Optional.of(target));

        service.place(PRINCIPAL, CHILD_ID, new com.carenest.dto.child.PlaceChildRequest(11L, " Đổi theo độ tuổi "));

        assertThat(enrollment.getValidTo()).isEqualTo(TODAY.minusDays(1));
        verify(enrollmentRepository).saveAndFlush(enrollment);
        verify(placementRules).startEnrollment(CHILD_ID, target, PRINCIPAL.userId(), TODAY);
        verify(placementRules)
                .recordPlacement(
                        eq(CHILD_ID),
                        eq("Bé An"),
                        eq(CLASS_ID),
                        eq(target),
                        eq(PRINCIPAL.userId()),
                        eq("Đổi theo độ tuổi"),
                        any(),
                        eq(TODAY),
                        eq(TODAY));
        assertThat(child.getStatus()).isEqualTo(ChildStatus.ACTIVE);
    }

    @Test
    @DisplayName("Chuyển lớp trong ngày đã có điểm danh bị chặn để không tách dữ liệu khỏi lớp")
    void transferIsRejectedAfterDailyDataExists() {
        ClassroomInfoResponse target = classroom(11L, "Lớp Chồi 2");
        given(declarationRepository.existsById(CHILD_ID)).willReturn(true);
        given(classroomService.findClassroomInfo(11L)).willReturn(Optional.of(target));
        given(attendanceRecordRepository.findByChildIdInAndDate(List.of(CHILD_ID), TODAY))
                .willReturn(List.of(mock(com.carenest.entity.attendance.AttendanceRecord.class)));

        assertThatThrownBy(() -> service.place(
                        PRINCIPAL, CHILD_ID, new com.carenest.dto.child.PlaceChildRequest(11L, "Đổi lớp")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors()).anySatisfy(error -> {
                            assertThat(error.field()).isEqualTo("effectiveDate");
                            assertThat(error.message()).contains("ngày học tiếp theo");
                        }));
        verify(placementRules, never()).startEnrollment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CHD-03: không chuyển lớp vào ngày nghỉ để enrollment không bắt đầu ngoài ngày học")
    void transferIsRejectedOnNonSchoolDay() {
        given(declarationRepository.existsById(CHILD_ID)).willReturn(true);
        given(classroomService.findClassroomInfo(11L)).willReturn(Optional.of(classroom(11L, "Lớp Chồi 2")));
        given(schoolYearService.isSchoolDay(TODAY)).willReturn(false);

        assertThatThrownBy(() -> service.place(
                        PRINCIPAL, CHILD_ID, new com.carenest.dto.child.PlaceChildRequest(11L, "Đổi lớp")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors()).anySatisfy(error -> {
                            assertThat(error.field()).isEqualTo("effectiveDate");
                            assertThat(error.message())
                                    .isEqualTo("Ngày chuyển lớp 10/11/2026 không phải ngày học. Chọn ngày học khác.");
                        }));
        verify(placementRules, never()).startEnrollment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CHD-10: trẻ chờ xếp lớp ⇒ không cần lý do, thành Đang học, bỏ điểm trường tiếp nhận")
    void placingPendingChildNeedsNoReason() {
        Child pending = Child.enroll(
                "HS26002", "Bé Bình", LocalDate.of(2022, 5, 1), Gender.MALE, false, CAMPUS_ID, TODAY, true);
        ReflectionTestUtils.setField(pending, "id", 2L);
        ClassroomInfoResponse target = classroom(11L, "Lớp Chồi 2");
        given(childRepository.findById(2L)).willReturn(Optional.of(pending));
        given(enrollmentRepository.findActiveByChildIdIn(List.of(2L), TODAY)).willReturn(List.of());
        given(declarationRepository.existsById(2L)).willReturn(true);
        given(classroomService.findClassroomInfo(11L)).willReturn(Optional.of(target));

        service.place(PRINCIPAL, 2L, new com.carenest.dto.child.PlaceChildRequest(11L, null));

        assertThat(pending.getStatus()).isEqualTo(ChildStatus.ACTIVE);
        assertThat(pending.getIntakeCampusId()).isNull();
        verify(enrollmentRepository, never()).saveAndFlush(any());
        verify(placementRules)
                .recordPlacement(
                        eq(2L), eq("Bé Bình"), eq(null), eq(target), eq(9L), eq(null), any(), eq(TODAY), eq(TODAY));
    }

    @Test
    @DisplayName("CHD-10: lỗi theo ô — thiếu lớp và lý do, cùng lớp, chưa khai báo sức khỏe, lớp không nhận, lý do dài")
    void placeValidatesFields() {
        expectFields(new com.carenest.dto.child.PlaceChildRequest(null, " "), "classId", "reason");
        expectFields(new com.carenest.dto.child.PlaceChildRequest(CLASS_ID, "x"), "classId");

        given(declarationRepository.existsById(CHILD_ID)).willReturn(false);
        assertThatThrownBy(() ->
                        service.place(PRINCIPAL, CHILD_ID, new com.carenest.dto.child.PlaceChildRequest(11L, "x")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .singleElement()
                                .satisfies(e -> assertThat(e.message())
                                        .isEqualTo(
                                                "Trẻ chưa có khai báo ăn và sức khỏe. Khai báo trước khi xếp lớp.")));

        given(declarationRepository.existsById(CHILD_ID)).willReturn(true);
        given(classroomService.findClassroomInfo(11L)).willReturn(Optional.of(classroom(11L, "Lớp Chồi 2")));
        given(placementRules.classProblem(any(), any(), any(), any()))
                .willReturn("Lớp Chồi 2 đã đủ 25 trẻ. Chọn lớp khác.");
        expectFields(new com.carenest.dto.child.PlaceChildRequest(11L, "x".repeat(301)), "classId", "reason");
        verify(placementRules, never()).startEnrollment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CHD-10: trẻ đã nghỉ ⇒ 409 3011, không xếp lớp")
    void placingLeftChildIsRejected() {
        child.withdraw(LocalDate.of(2026, 11, 1), "Chuyển nhà");

        assertThatThrownBy(() ->
                        service.place(PRINCIPAL, CHILD_ID, new com.carenest.dto.child.PlaceChildRequest(11L, "x")))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ALREADY_LEFT));
        verify(placementRules, never()).recordPlacement(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private void expectFields(com.carenest.dto.child.PlaceChildRequest request, String... fields) {
        assertThatThrownBy(() -> service.place(PRINCIPAL, CHILD_ID, request))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .containsExactly(fields));
    }

    private static ClassroomInfoResponse classroom(Long id, String name) {
        return new ClassroomInfoResponse(
                id,
                name,
                CAMPUS_ID,
                5L,
                com.carenest.utils.enums.SchoolYearStatus.ACTIVE,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 5, 31),
                3L,
                List.of(),
                25,
                com.carenest.utils.enums.ClassroomStatus.ACTIVE);
    }

    @Test
    @DisplayName(
            "G94-180/CHD-03: nghỉ học ⇒ Đã nghỉ, lớp kết thúc ngày trước ngày nghỉ, lịch sử, báo GV, sự kiện khóa TK")
    void withdrawClosesClassAndNotifiesTeachers() {
        service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest("2026-11-10", " Chuyển nhà "));

        assertThat(child.getStatus()).isEqualTo(ChildStatus.LEFT);
        assertThat(child.getLeftOn()).isEqualTo(TODAY);
        assertThat(child.getLeftReason()).isEqualTo("Chuyển nhà");
        assertThat(enrollment.getValidTo()).isEqualTo(TODAY.minusDays(1));
        ArgumentCaptor<ChildHistory> history = ArgumentCaptor.forClass(ChildHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(ChildHistoryAction.WITHDRAWN);
        verify(notificationService)
                .notifyUsers(eq(Set.of(7L, 8L)), eq(NotificationType.CHILD_WITHDRAWN), any(), any(), eq(CHILD_ID));
        verify(events).publishEvent(new YearTransitionService.ChildrenLeftSchoolEvent(List.of(CHILD_ID), TODAY));
    }

    @Test
    @DisplayName("G94-180/CHD-03 (user chốt 2026-11-25): nghỉ từ thứ Bảy (sau hôm nay) ⇒ lịch nghỉ: trẻ vẫn Đang học,"
            + " lớp không đổi, lịch sử LEAVE_SCHEDULED, báo GV ngày sẽ nghỉ, không phát sự kiện khóa tài khoản")
    void withdrawFromLaterDaySchedulesLeave() {
        LocalDate saturday = LocalDate.of(2026, 11, 14);

        service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest(saturday.toString(), " Chuyển nhà "));

        assertThat(child.getStatus()).isEqualTo(ChildStatus.ACTIVE);
        assertThat(child.getLeftOn()).isNull();
        assertThat(child.getScheduledLeaveOn()).isEqualTo(saturday);
        assertThat(child.getScheduledLeaveReason()).isEqualTo("Chuyển nhà");
        assertThat(child.getScheduledLeaveBy()).isEqualTo(PRINCIPAL.userId());
        verify(childRepository).save(child);
        assertThat(enrollment.getValidTo()).isNull();
        verify(enrollmentRepository, never()).save(any());
        verify(enrollmentRepository, never()).delete(any());
        verify(placementRules, never()).cancelUpcoming(any(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<ChildHistory> history = ArgumentCaptor.forClass(ChildHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(ChildHistoryAction.LEAVE_SCHEDULED);
        assertThat(history.getValue().getNote()).isEqualTo("Chuyển nhà");
        assertThat(history.getValue().getUserId()).isEqualTo(PRINCIPAL.userId());
        verify(notificationService)
                .notifyUsers(
                        eq(Set.of(7L, 8L)),
                        eq(NotificationType.CHILD_WITHDRAWN),
                        any(),
                        eq("Bé An sẽ nghỉ học từ ngày 14/11/2026."),
                        eq(CHILD_ID));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("G94-180/CHD-03: trẻ đã có lịch nghỉ ⇒ đặt lịch lần 2 (ngày nào cũng vậy) lỗi ô leftAt, không ghi gì")
    void secondLeaveScheduleIsRejected() {
        child.scheduleLeave(LocalDate.of(2026, 11, 20), "Chuyển nhà", PRINCIPAL.userId());

        expectLeftAtError(
                "2026-11-25", "Trẻ đã có lịch nghỉ học từ 20/11/2026. Hủy lịch nghỉ trước khi chọn ngày khác");
        expectLeftAtError(
                TODAY.toString(), "Trẻ đã có lịch nghỉ học từ 20/11/2026. Hủy lịch nghỉ trước khi chọn ngày khác");
        verify(childRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName(
            "G94-180/CHD-03: đặt lịch nghỉ 3 tuần sau khi đã có năm học sắp tới ⇒ bảng xếp lớp năm sau ghi Nghỉ học"
                    + " thay quyết định cũ, lớp nguồn là lớp đang học hôm nay")
    void scheduledLeaveMarksLeaveOnNewYearBoard() {
        LocalDate later = TODAY.plusWeeks(3);
        given(schoolYearService.findNextPlannedYear()).willReturn(plannedYear(50L));
        ClassPlacement promoted = ClassPlacement.create(50L, CHILD_ID);
        promoted.decide(CLASS_ID, PlacementAction.PROMOTE, 60L, 2L);
        ClassPlacement other = ClassPlacement.create(50L, 99L);
        other.decide(CLASS_ID, PlacementAction.PROMOTE, 60L, 2L);
        given(classPlacementRepository.findAllBySchoolYearId(50L)).willReturn(List.of(other, promoted));

        service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest(later.toString(), "Chuyển nhà"));

        verify(classPlacementRepository).save(promoted);
        assertThat(promoted.getAction()).isEqualTo(PlacementAction.LEAVE);
        assertThat(promoted.getFromClassroomId()).isEqualTo(CLASS_ID);
        assertThat(promoted.getToClassroomId()).isNull();
        assertThat(promoted.getUpdatedBy()).isEqualTo(PRINCIPAL.userId());
        assertThat(other.getAction()).isEqualTo(PlacementAction.PROMOTE);
    }

    @Test
    @DisplayName("G94-180/CHD-03: nghỉ từ hôm nay ⇒ trẻ đã rời bảng xếp lớp năm sau (không còn lớp hôm nay), không ghi"
            + " quyết định")
    void withdrawTodayLeavesNewYearBoardUntouched() {
        given(schoolYearService.findNextPlannedYear()).willReturn(plannedYear(50L));

        service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest(TODAY.toString(), "Chuyển nhà"));

        verify(classPlacementRepository, never()).save(any());
    }

    private static com.carenest.dto.organization.SchoolYearDetailResponse plannedYear(Long id) {
        return schoolYear(id, LocalDate.of(2027, 9, 1), LocalDate.of(2028, 5, 31), SchoolYearStatus.PLANNED);
    }

    private static com.carenest.dto.organization.SchoolYearDetailResponse schoolYear(
            Long id, LocalDate start, LocalDate end, SchoolYearStatus status) {
        return new com.carenest.dto.organization.SchoolYearDetailResponse(
                id,
                start.getYear() + "-" + end.getYear(),
                start,
                end,
                null,
                null,
                null,
                null,
                status,
                0,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("G94-180/CHD-03, CHD-04: áp lịch nghỉ hôm nay ⇒ Đã nghỉ từ ngày hẹn với lý do đã lưu, lớp đang học tới"
            + " hôm qua, lớp chuyển tới từ đúng ngày nghỉ bị hủy như lịch chưa bắt đầu, lịch bị xóa, lịch sử WITHDRAWN"
            + " ghi người đặt lịch, phát sự kiện khóa tài khoản")
    void applyDueScheduledLeaveWithdrawsChild() {
        Long scheduler = 5L;
        child.scheduleLeave(TODAY, "Chuyển nhà", scheduler);
        enrollment.endOn(TODAY.minusDays(1));
        Enrollment transfer = Enrollment.start(CHILD_ID, 11L, TODAY);
        given(enrollmentRepository.findOpenByChildId(CHILD_ID, TODAY)).willReturn(List.of(transfer));
        given(childRepository.findIdsWithLeaveDue(TODAY)).willReturn(List.of(CHILD_ID));
        given(childRepository.findLockedById(CHILD_ID)).willReturn(Optional.of(child));
        given(classroomService.findCampusId(11L)).willReturn(Optional.of(CAMPUS_ID));

        assertThat(service.applyDueScheduledLeaves(TODAY)).containsExactly(CHILD_ID);

        assertThat(child.getStatus()).isEqualTo(ChildStatus.LEFT);
        assertThat(child.getLeftOn()).isEqualTo(TODAY);
        assertThat(child.getLeftReason()).isEqualTo("Chuyển nhà");
        assertThat(child.getScheduledLeaveOn()).isNull();
        assertThat(child.getScheduledLeaveReason()).isNull();
        assertThat(child.getScheduledLeaveBy()).isNull();
        verify(placementRules)
                .cancelUpcoming(eq(CHILD_ID), eq("Bé An"), eq(transfer), eq(null), eq(scheduler), any(), eq(TODAY));
        verify(enrollmentRepository, never()).delete(transfer);
        ArgumentCaptor<ChildHistory> history = ArgumentCaptor.forClass(ChildHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(ChildHistoryAction.WITHDRAWN);
        assertThat(history.getValue().getUserId()).isEqualTo(scheduler);
        assertThat(history.getValue().getNote()).isEqualTo("Chuyển nhà");
        verify(events).publishEvent(new YearTransitionService.ChildrenLeftSchoolEvent(List.of(CHILD_ID), TODAY));
    }

    @Test
    @DisplayName("G94-180/CHD-03: lần chạy khác đã áp lịch (job 00:05 rồi khởi động lại) ⇒ không áp lại, không ghi lịch"
            + " sử, không phát sự kiện")
    void applyDueScheduledLeavesSkipsLeaveAlreadyApplied() {
        child.scheduleLeave(TODAY, "Chuyển nhà", 5L);
        child.withdraw(TODAY, "Chuyển nhà");
        given(childRepository.findIdsWithLeaveDue(TODAY)).willReturn(List.of(CHILD_ID));
        given(childRepository.findLockedById(CHILD_ID)).willReturn(Optional.of(child));

        assertThat(service.applyDueScheduledLeaves(TODAY)).isEmpty();

        verify(historyRepository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("G94-180/CHD-03 (user chốt 2026-11-25): leave-options — hôm nay không phải ngày học vẫn chọn được hôm"
            + " nay; khoảng chọn từ hôm nay tới ngày kết thúc năm học đang hoạt động")
    void leaveOptionsAllowTodayOnNonSchoolDay() {
        given(schoolYearService.isSchoolDay(TODAY)).willReturn(false);

        assertThat(service.leaveOptions(PRINCIPAL, CHILD_ID))
                .isEqualTo(new com.carenest.dto.child.LeaveOptionsResponse(TODAY, true, null, TODAY, YEAR_END));
    }

    @Test
    @DisplayName("G94-180/CHD-03 (user chốt 2026-11-25): hôm nay đã có số đo / phiếu khám (không có điểm danh) ⇒"
            + " leave-options không chọn hôm nay, từ ngày mai; withdraw hôm nay ⇒ lỗi ô leftAt cùng câu")
    void healthDataTodayBlocksLeavingToday() {
        given(leaveDataCheck.hasData(CHILD_ID, TODAY)).willReturn(true);

        assertThat(service.leaveOptions(PRINCIPAL, CHILD_ID))
                .isEqualTo(new com.carenest.dto.child.LeaveOptionsResponse(
                        TODAY, false, SAME_DAY_DATA, TODAY.plusDays(1), YEAR_END));
        expectLeftAtError(TODAY.toString(), SAME_DAY_DATA);
        verify(childRepository, never()).save(any());
    }

    @Test
    @DisplayName(
            "G94-180/CHD-03 (user chốt 2026-11-25): nghỉ hè (năm đang hoạt động đã kết thúc hôm qua) ⇒ leave-options"
                    + " min = max = hôm nay, hôm nay vẫn chọn được; withdraw hôm nay được, ngày sau lỗi vì ngoài năm học")
    void leaveOptionsAfterYearEndOnlyOfferToday() {
        given(schoolYearService.getSchoolYears(any(), eq(SchoolYearStatus.ACTIVE)))
                .willReturn(List.of(
                        schoolYear(40L, LocalDate.of(2026, 1, 1), TODAY.minusDays(1), SchoolYearStatus.ACTIVE)));

        assertThat(service.leaveOptions(PRINCIPAL, CHILD_ID))
                .isEqualTo(new com.carenest.dto.child.LeaveOptionsResponse(TODAY, true, null, TODAY, TODAY));
        expectLeftAtError(TODAY.plusDays(1).toString(), "Ngày nghỉ học phải trong năm học (tới 09/11/2026)");
        verify(childRepository, never()).save(any());

        service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest(TODAY.toString(), "Chuyển nhà"));
        assertThat(child.getStatus()).isEqualTo(ChildStatus.LEFT);
    }

    @Test
    @DisplayName("G94-180/CHD-03: nghỉ hè mà hôm nay đã có dữ liệu ⇒ leave-options min = max = hôm nay nhưng"
            + " todayAllowed = false kèm câu chặn")
    void leaveOptionsInSummerStillBlockTodayWithData() {
        given(schoolYearService.getSchoolYears(any(), eq(SchoolYearStatus.ACTIVE)))
                .willReturn(List.of(
                        schoolYear(40L, LocalDate.of(2026, 1, 1), TODAY.minusDays(1), SchoolYearStatus.ACTIVE)));
        given(leaveDataCheck.hasData(CHILD_ID, TODAY)).willReturn(true);

        assertThat(service.leaveOptions(PRINCIPAL, CHILD_ID))
                .isEqualTo(new com.carenest.dto.child.LeaveOptionsResponse(TODAY, false, SAME_DAY_DATA, TODAY, TODAY));
    }

    @Test
    @DisplayName("G94-180/CHD-03 (user chốt 2026-11-25): chưa có năm học đang hoạt động ⇒ chỉ cho nghỉ từ hôm nay;"
            + " ngày sau lỗi ô leftAt với câu riêng")
    void noActiveYearOnlyAllowsLeavingToday() {
        given(schoolYearService.getSchoolYears(any(), eq(SchoolYearStatus.ACTIVE)))
                .willReturn(List.of());

        assertThat(service.leaveOptions(PRINCIPAL, CHILD_ID))
                .isEqualTo(new com.carenest.dto.child.LeaveOptionsResponse(TODAY, true, null, TODAY, TODAY));
        expectLeftAtError(
                TODAY.plusDays(1).toString(), "Chỉ cho nghỉ học từ hôm nay khi chưa có năm học đang hoạt động");
        verify(childRepository, never()).save(any());
    }

    @Test
    @DisplayName("G94-180/CHD-03: trẻ đã có lịch nghỉ ⇒ leave-options không chọn hôm nay, todayBlockedReason là câu"
            + " trẻ đã có lịch (đè cả dữ liệu trong ngày)")
    void leaveOptionsOfChildWithScheduleBlockToday() {
        child.scheduleLeave(LocalDate.of(2026, 11, 20), "Chuyển nhà", PRINCIPAL.userId());
        given(leaveDataCheck.hasData(CHILD_ID, TODAY)).willReturn(true);

        assertThat(service.leaveOptions(PRINCIPAL, CHILD_ID))
                .isEqualTo(new com.carenest.dto.child.LeaveOptionsResponse(
                        TODAY,
                        false,
                        "Trẻ đã có lịch nghỉ học từ 20/11/2026. Hủy lịch nghỉ trước khi chọn ngày khác",
                        TODAY.plusDays(1),
                        YEAR_END));
    }

    @Test
    @DisplayName("G94-180/CHD-03: nghỉ hôm nay vừa sai ngày vừa có số đo / phiếu khám ⇒ chỉ báo lỗi ngày (ô leftAt)")
    void dateErrorWinsOverHealthDataError() {
        ReflectionTestUtils.setField(child, "enrolledOn", TODAY.plusDays(1));
        given(leaveDataCheck.hasData(CHILD_ID, TODAY)).willReturn(true);

        expectLeftAtError(TODAY.toString(), "Ngày nghỉ học không được trước ngày tiếp nhận (11/11/2026)");
    }

    @Test
    @DisplayName("G94-180/CHD-03 (user chốt 2026-11-25): hủy lịch nghỉ ⇒ trẻ vẫn Đang học, lịch bị xóa, dòng Nghỉ học"
            + " ở bảng năm sau bị xóa (dòng trẻ khác giữ), lịch sử LEAVE_CANCELLED, báo GV")
    void cancelScheduledLeaveClearsScheduleAndNewYearBoard() {
        LocalDate later = TODAY.plusWeeks(3);
        child.scheduleLeave(later, "Chuyển nhà", 5L);
        given(schoolYearService.findNextPlannedYear()).willReturn(plannedYear(50L));
        ClassPlacement leave = ClassPlacement.create(50L, CHILD_ID);
        leave.decide(CLASS_ID, PlacementAction.LEAVE, null, 5L);
        ClassPlacement other = ClassPlacement.create(50L, 99L);
        other.decide(CLASS_ID, PlacementAction.LEAVE, null, 5L);
        given(classPlacementRepository.findAllBySchoolYearId(50L)).willReturn(List.of(other, leave));

        service.cancelScheduledLeave(PRINCIPAL, CHILD_ID);

        assertThat(child.getStatus()).isEqualTo(ChildStatus.ACTIVE);
        assertThat(child.getScheduledLeaveOn()).isNull();
        assertThat(child.getScheduledLeaveReason()).isNull();
        assertThat(child.getScheduledLeaveBy()).isNull();
        verify(childRepository).save(child);
        verify(classPlacementRepository).delete(leave);
        verify(classPlacementRepository, never()).delete(other);
        ArgumentCaptor<ChildHistory> history = ArgumentCaptor.forClass(ChildHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(ChildHistoryAction.LEAVE_CANCELLED);
        assertThat(history.getValue().getUserId()).isEqualTo(PRINCIPAL.userId());
        verify(notificationService)
                .notifyUsers(
                        eq(Set.of(7L, 8L)),
                        eq(NotificationType.CHILD_WITHDRAWN),
                        any(),
                        eq("Hủy lịch nghỉ học của Bé An (từ 01/12/2026)."),
                        eq(CHILD_ID));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName(
            "G94-180/CHD-03: hủy lịch nhưng bảng năm sau đã ghi quyết định khác Nghỉ học ⇒ không xóa quyết định đó")
    void cancelScheduledLeaveKeepsOtherNewYearDecision() {
        child.scheduleLeave(TODAY.plusWeeks(3), "Chuyển nhà", 5L);
        given(schoolYearService.findNextPlannedYear()).willReturn(plannedYear(50L));
        ClassPlacement promoted = ClassPlacement.create(50L, CHILD_ID);
        promoted.decide(CLASS_ID, PlacementAction.PROMOTE, 60L, 2L);
        given(classPlacementRepository.findAllBySchoolYearId(50L)).willReturn(List.of(promoted));

        service.cancelScheduledLeave(PRINCIPAL, CHILD_ID);

        verify(classPlacementRepository, never()).delete(any());
        assertThat(child.getScheduledLeaveOn()).isNull();
    }

    @Test
    @DisplayName("G94-180/CHD-03: hủy lịch khi không có lịch ⇒ 409 CHILD_NO_SCHEDULED_LEAVE, không ghi gì")
    void cancelScheduledLeaveWithoutScheduleConflicts() {
        assertCancelRejected(ApiCode.CHILD_NO_SCHEDULED_LEAVE);
        verify(childRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
        verify(notificationService, never()).notifyUsers(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("G94-180/CHD-03: lịch nghỉ hôm nay mà job 00:05 chưa áp ⇒ hủy lịch áp lịch như job (Đã nghỉ, lịch sử"
            + " WITHDRAWN của người đặt lịch, phát sự kiện khóa) rồi 409 \"Đã tới ngày nghỉ…\"")
    void cancelDueScheduledLeaveAppliesItFirst() {
        child.scheduleLeave(TODAY, "Chuyển nhà", 5L);
        given(childRepository.findLockedById(CHILD_ID)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> service.cancelScheduledLeave(PRINCIPAL, CHILD_ID))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_LEAVE_ALREADY_DUE);
                    assertThat(ex.getMessage()).isEqualTo("Đã tới ngày nghỉ: trẻ đã nghỉ học từ 10/11/2026");
                });

        assertDueLeaveApplied();
    }

    @Test
    @DisplayName("G94-180/CHD-03: lịch nghỉ hôm nay mà job chưa áp ⇒ cho nghỉ học áp lịch rồi 409 CHILD_ALREADY_LEFT")
    void withdrawAppliesDueScheduledLeaveFirst() {
        child.scheduleLeave(TODAY, "Chuyển nhà", 5L);
        given(childRepository.findLockedById(CHILD_ID)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest("2026-11-12", "x")))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ALREADY_LEFT));

        assertDueLeaveApplied();
    }

    @Test
    @DisplayName("G94-180/CHD-03: lịch nghỉ hôm nay mà job chưa áp ⇒ leave-options áp lịch rồi 409 CHILD_ALREADY_LEFT")
    void leaveOptionsApplyDueScheduledLeaveFirst() {
        child.scheduleLeave(TODAY, "Chuyển nhà", 5L);
        given(childRepository.findLockedById(CHILD_ID)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> service.leaveOptions(PRINCIPAL, CHILD_ID))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ALREADY_LEFT));

        assertDueLeaveApplied();
    }

    // Lịch nghỉ hôm nay đã áp như job: Đã nghỉ từ hôm nay, lớp tới hôm qua, lịch sử WITHDRAWN của người đặt lịch, sự
    // kiện
    private void assertDueLeaveApplied() {
        assertThat(child.getStatus()).isEqualTo(ChildStatus.LEFT);
        assertThat(child.getLeftOn()).isEqualTo(TODAY);
        assertThat(child.getScheduledLeaveOn()).isNull();
        assertThat(enrollment.getValidTo()).isEqualTo(TODAY.minusDays(1));
        ArgumentCaptor<ChildHistory> history = ArgumentCaptor.forClass(ChildHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(ChildHistoryAction.WITHDRAWN);
        assertThat(history.getValue().getUserId()).isEqualTo(5L);
        verify(events).publishEvent(new YearTransitionService.ChildrenLeftSchoolEvent(List.of(CHILD_ID), TODAY));
    }

    @Test
    @DisplayName("G94-180/CHD-03: hủy lịch của trẻ đã nghỉ ⇒ 409 CHILD_ALREADY_LEFT; PHT ngoài điểm trường ⇒ 403")
    void cancelScheduledLeaveFollowsWithdrawScope() {
        CurrentUser vice = TestPermissions.userWithDefaults(3L, CareNestRole.VICE_PRINCIPAL);
        given(policy.managedCampusIds(vice)).willReturn(Set.of(99L));
        assertThatThrownBy(() -> service.cancelScheduledLeave(vice, CHILD_ID))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ACCESS_DENIED));

        child.withdraw(LocalDate.of(2026, 10, 1), "Đã nghỉ");
        assertCancelRejected(ApiCode.CHILD_ALREADY_LEFT);
    }

    @Test
    @DisplayName(
            "G94-180/CHD-03, CHD-04: năm mới đã kích hoạt trước ngày nghỉ (ngày nghỉ trước ngày bắt đầu năm đang hoạt"
                    + " động), trẻ không có lớp năm đó ⇒ 409 CHILD_LEAVE_LOCKED_BY_NEW_YEAR, lịch giữ nguyên, không ghi gì")
    void cancelLeaveScheduledBeforeActivatedYearIsRejected() {
        LocalDate leaveOn = TODAY.plusWeeks(3);
        LocalDate newYearStart = leaveOn.plusDays(5);
        child.scheduleLeave(leaveOn, "Chuyển nhà", 5L);
        given(schoolYearService.getSchoolYears(any(), eq(SchoolYearStatus.ACTIVE)))
                .willReturn(
                        List.of(schoolYear(41L, newYearStart, newYearStart.plusMonths(9), SchoolYearStatus.ACTIVE)));

        assertCancelRejected(ApiCode.CHILD_LEAVE_LOCKED_BY_NEW_YEAR);

        assertThat(child.getScheduledLeaveOn()).isEqualTo(leaveOn);
        verify(childRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
        verify(notificationService, never()).notifyUsers(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("G94-180/CHD-03: lịch nghỉ đặt sau khi kích hoạt cho ngày trước ngày bắt đầu năm mới, trẻ có lớp năm"
            + " mới ⇒ vẫn hủy được")
    void cancelLeaveOfChildWithNewYearClassIsAllowed() {
        LocalDate leaveOn = TODAY.plusWeeks(3);
        LocalDate newYearStart = leaveOn.plusDays(5);
        child.scheduleLeave(leaveOn, "Chuyển nhà", 5L);
        given(schoolYearService.getSchoolYears(any(), eq(SchoolYearStatus.ACTIVE)))
                .willReturn(
                        List.of(schoolYear(41L, newYearStart, newYearStart.plusMonths(9), SchoolYearStatus.ACTIVE)));
        given(enrollmentRepository.findOpenByChildId(CHILD_ID, newYearStart))
                .willReturn(List.of(Enrollment.start(CHILD_ID, 60L, newYearStart)));

        service.cancelScheduledLeave(PRINCIPAL, CHILD_ID);

        assertThat(child.getScheduledLeaveOn()).isNull();
        verify(childRepository).save(child);
    }

    private void assertCancelRejected(ApiCode code) {
        assertThatThrownBy(() -> service.cancelScheduledLeave(PRINCIPAL, CHILD_ID))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("G94-180/CHD-03 (user chốt 2026-11-25): đã có lịch nghỉ ⇒ chuyển lớp từ ngày ≥ ngày nghỉ lỗi ô"
            + " effectiveDate; từ ngày trước ngày nghỉ thì được")
    void transferFromLeaveDateOnIsRejected() {
        LocalDate leaveOn = TODAY.plusDays(7);
        child.scheduleLeave(leaveOn, "Chuyển nhà", 5L);
        given(declarationRepository.existsById(CHILD_ID)).willReturn(true);
        ClassroomInfoResponse target = classroom(11L, "Lớp Chồi 2");
        given(classroomService.findClassroomInfo(11L)).willReturn(Optional.of(target));
        given(schoolYearService.isSchoolDay(any())).willReturn(true);

        for (LocalDate date : List.of(leaveOn, leaveOn.plusDays(3))) {
            assertThatThrownBy(() -> service.place(
                            PRINCIPAL, CHILD_ID, new com.carenest.dto.child.PlaceChildRequest(11L, "Đổi lớp", date)))
                    .isInstanceOfSatisfying(
                            GlobalException.class,
                            ex -> assertThat(ex.getFieldErrors())
                                    .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                                    .containsExactly(tuple(
                                            "effectiveDate",
                                            "Trẻ nghỉ học từ 17/11/2026. Hủy lịch nghỉ trước khi xếp lớp từ ngày"
                                                    + " này")));
        }
        verify(placementRules, never()).startEnrollment(any(), any(), any(), any());

        service.place(
                PRINCIPAL,
                CHILD_ID,
                new com.carenest.dto.child.PlaceChildRequest(11L, "Đổi lớp", leaveOn.minusDays(1)));
        verify(placementRules).startEnrollment(CHILD_ID, target, PRINCIPAL.userId(), leaveOn.minusDays(1));
    }

    @Test
    @DisplayName("G94-180/CHD-03: leave-options như withdraw — trẻ đã nghỉ ⇒ 409, PHT ngoài điểm trường ⇒ 403")
    void leaveOptionsFollowWithdrawScope() {
        CurrentUser vice = TestPermissions.userWithDefaults(3L, CareNestRole.VICE_PRINCIPAL);
        given(policy.managedCampusIds(vice)).willReturn(Set.of(99L));
        assertThatThrownBy(() -> service.leaveOptions(vice, CHILD_ID))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ACCESS_DENIED));

        child.withdraw(LocalDate.of(2026, 10, 1), "Đã nghỉ");
        assertThatThrownBy(() -> service.leaveOptions(PRINCIPAL, CHILD_ID))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ALREADY_LEFT));
    }

    @Test
    @DisplayName("G94-180/CHD-03: nghỉ ngay ngày vào lớp ⇒ bỏ lớp đó (không có ngày học nào)")
    void withdrawOnFirstClassDayDropsEnrollment() {
        Enrollment today = Enrollment.start(CHILD_ID, CLASS_ID, TODAY);
        given(enrollmentRepository.findOpenByChildId(CHILD_ID, TODAY)).willReturn(List.of(today));

        service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest("2026-11-10", "Lý do"));

        verify(enrollmentRepository).delete(today);
    }

    @Test
    @DisplayName("G94-180/CHD-03 (user chốt 2026-11-25): lỗi ô leftAt theo thứ tự — trước hôm nay (kèm thiếu lý do),"
            + " sau ngày kết thúc năm học, trước ngày tiếp nhận, trước ngày mở lại hồ sơ")
    void withdrawValidatesLeaveDateAndReason() {
        assertThatThrownBy(() -> service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest("2026-11-09", "")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                                .containsExactly(
                                        tuple("leftAt", "Ngày nghỉ học không được trước hôm nay"),
                                        tuple("reason", "Nhập lý do cho trẻ nghỉ học")));
        expectLeftAtError("2027-06-01", "Ngày nghỉ học phải trong năm học (tới 31/05/2027)");

        // Ngày tiếp nhận / mở lại hồ sơ sau hôm nay chỉ gặp ở dữ liệu lỗi: vẫn chặn
        ReflectionTestUtils.setField(child, "enrolledOn", TODAY.plusDays(1));
        expectLeftAtError("2026-11-10", "Ngày nghỉ học không được trước ngày tiếp nhận (11/11/2026)");
        ReflectionTestUtils.setField(child, "enrolledOn", LocalDate.of(2026, 9, 1));
        ReflectionTestUtils.setField(child, "readmittedOn", TODAY.plusDays(1));
        expectLeftAtError("2026-11-10", "Ngày nghỉ học không được trước ngày mở lại hồ sơ (11/11/2026)");
        verify(childRepository, never()).save(any());
    }

    private void expectLeftAtError(String leftAt, String message) {
        assertThatThrownBy(() -> service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest(leftAt, "Chuyển nhà")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                                .containsExactly(tuple("leftAt", message)));
    }

    @Test
    @DisplayName("G94-180/CHD-03: trẻ đã nghỉ ⇒ 409, chỉ mở lại hồ sơ")
    void withdrawLeftChildConflicts() {
        child.withdraw(LocalDate.of(2026, 10, 1), "Đã nghỉ");

        assertThatThrownBy(() -> service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest("2026-11-10", "x")))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ALREADY_LEFT));
    }

    @Test
    @DisplayName("PHT ngoài điểm trường của trẻ ⇒ 403")
    void withdrawOutOfScopeDenied() {
        CurrentUser vice = TestPermissions.userWithDefaults(3L, CareNestRole.VICE_PRINCIPAL);
        given(policy.managedCampusIds(vice)).willReturn(Set.of(99L));

        assertThatThrownBy(() -> service.withdraw(vice, CHILD_ID, new WithdrawChildRequest("2026-11-10", "x")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ACCESS_DENIED));
    }

    @Test
    @DisplayName("Sửa hồ sơ: bỏ phụ huynh đang liên kết tài khoản, ngày sinh sau ngày tiếp nhận ⇒ lỗi theo ô")
    void prepareEditRejectsDroppedLinkedGuardianAndLateBirthDate() {
        ChildGuardian linked = guardian(31L, 0, "001234567890", "0912345678", 50L);
        ChildGuardian other = guardian(32L, 1, "001234567891", "0912345679", null);
        given(guardianRepository.findAllByChildIdOrderByPositionAsc(CHILD_ID)).willReturn(List.of(linked, other));
        given(linkService.findLinksOfChild(CHILD_ID))
                .willReturn(List.of(new GuardianLinkResponse(50L, CHILD_ID, GuardianRelationship.MOTHER, null)));

        UpdateChildRequest request = request("2026-10-01", List.of(guardianInput(32L, "001234567891", "0912345679")));

        assertThatThrownBy(() -> service.prepareEdit(PRINCIPAL, CHILD_ID, request))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .contains("dateOfBirth", "guardians"));
    }

    @Test
    @DisplayName("Sửa hồ sơ: ghép phụ huynh theo id, giữ tài khoản đang liên kết, điểm trường theo lớp hiện tại")
    void prepareEditMatchesGuardiansById() {
        ChildGuardian linked = guardian(31L, 0, "001234567890", "0912345678", 50L);
        given(guardianRepository.findAllByChildIdOrderByPositionAsc(CHILD_ID)).willReturn(List.of(linked));
        given(linkService.findLinksOfChild(CHILD_ID))
                .willReturn(List.of(new GuardianLinkResponse(50L, CHILD_ID, GuardianRelationship.MOTHER, null)));
        given(childRepository.findDuplicateCandidates(any(), anyCollection(), eq(CHILD_ID)))
                .willReturn(List.of());

        ChildEditDraft draft = service.prepareEdit(
                PRINCIPAL, CHILD_ID, request("2022-03-01", List.of(guardianInput(31L, "001234567890", "0987654321"))));

        assertThat(draft.campusId()).isEqualTo(CAMPUS_ID);
        assertThat(draft.guardians()).singleElement().satisfies(g -> {
            assertThat(g.guardianId()).isEqualTo(31L);
            assertThat(g.accountId()).isEqualTo(50L);
            assertThat(g.currentPhone()).isEqualTo("0912345678");
            assertThat(g.draft().phone()).isEqualTo("0987654321");
        });
    }

    @Test
    @DisplayName("Sửa hồ sơ: lịch sử UPDATED ghi từng ô đổi, CCCD chỉ 3 số cuối")
    void applyEditRecordsMaskedChanges() {
        ChildGuardian linked = guardian(31L, 0, "001234567890", "0912345678", null);
        given(guardianRepository.findAllByChildIdOrderByPositionAsc(CHILD_ID)).willReturn(List.of(linked));
        given(guardianRepository.lockByChildId(CHILD_ID)).willReturn(List.of(linked));
        given(profileRepository.findById(CHILD_ID)).willReturn(Optional.empty());
        ChildEditDraft draft = service.prepareEdit(
                PRINCIPAL, CHILD_ID, request("2022-03-01", List.of(guardianInput(31L, "001234567899", "0912345678"))));

        service.applyEdit(PRINCIPAL, draft);

        assertThat(linked.getCitizenId()).isEqualTo("001234567899");
        ArgumentCaptor<ChildHistory> history = ArgumentCaptor.forClass(ChildHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(ChildHistoryAction.UPDATED);
        assertThat(history.getValue().getChanges())
                .contains("guardians.0.idNumber")
                .contains("***890")
                .contains("***899")
                .doesNotContain("001234567899");
        verify(profileRepository).save(any(ChildProfile.class));
    }

    @Test
    @DisplayName("Sửa hồ sơ trẻ đã nghỉ ⇒ 409")
    void prepareEditLeftChildConflicts() {
        child.withdraw(LocalDate.of(2026, 10, 1), "Đã nghỉ");

        assertThatThrownBy(() -> service.prepareEdit(PRINCIPAL, CHILD_ID, request("2022-03-01", List.of())))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ALREADY_LEFT));
    }

    @Test
    @DisplayName("CHD-06: sửa khai báo khi đã có khai báo mà thiếu lý do ⇒ lỗi ô không tiền tố declaration.")
    void saveDeclarationRequiresReasonAndUsesUnprefixedFields() {
        given(declarationRepository.findById(CHILD_ID))
                .willReturn(Optional.of(declaration(com.carenest.utils.enums.DeclarationState.NONE_REPORTED, null)));
        var request = new com.carenest.dto.child.HealthDeclarationRequest(
                declarationInput("REPORTED", List.of(), "NONE_REPORTED", null), " ");

        assertThatThrownBy(() -> service.saveDeclaration(PRINCIPAL, CHILD_ID, request))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .containsExactly("allergies", "reason"));
    }

    @Test
    @DisplayName("AUTH-10/AUTH-13: GV được cấp thêm CHILD_PROFILE_EDIT sửa khai báo trẻ lớp mình (qua kiểm phạm vi)")
    void teacherWithGrantPassesScopeForOwnClassChild() {
        CurrentUser teacher = new CurrentUser(
                20L,
                "kc-20",
                "gv.lop",
                "GV",
                null,
                java.util.Set.of(CareNestRole.TEACHER),
                java.util.Set.of(com.carenest.utils.enums.Permission.CHILD_PROFILE_EDIT));
        given(childAccessPolicy.canViewChild(teacher, CHILD_ID)).willReturn(true);
        given(declarationRepository.findById(CHILD_ID))
                .willReturn(Optional.of(declaration(com.carenest.utils.enums.DeclarationState.NONE_REPORTED, null)));
        var request = new com.carenest.dto.child.HealthDeclarationRequest(
                declarationInput("REPORTED", List.of(), "NONE_REPORTED", null), " ");

        // Qua được phạm vi thì tới bước kiểm nghiệp vụ (thiếu lý do), không bị CHILD_ACCESS_DENIED
        assertThatThrownBy(() -> service.checkDeclaration(teacher, CHILD_ID, request))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .containsExactly("allergies", "reason"));
        verify(policy, never()).managesWholeSchool(teacher);
    }

    @Test
    @DisplayName("AUTH-13: GV có CHILD_PROFILE_EDIT sửa trẻ lớp khác ⇒ CHILD_ACCESS_DENIED")
    void teacherWithGrantCannotEditChildOutsideAssignedClass() {
        CurrentUser teacher = new CurrentUser(
                20L,
                "kc-20",
                "gv.lop",
                "GV",
                null,
                java.util.Set.of(CareNestRole.TEACHER),
                java.util.Set.of(com.carenest.utils.enums.Permission.CHILD_PROFILE_EDIT));
        given(childAccessPolicy.canViewChild(teacher, CHILD_ID)).willReturn(false);
        var request = new com.carenest.dto.child.HealthDeclarationRequest(
                declarationInput("REPORTED", List.of(), "NONE_REPORTED", null), "Phụ huynh bổ sung");

        assertThatThrownBy(() -> service.checkDeclaration(teacher, CHILD_ID, request))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ACCESS_DENIED));
        verify(declarationRepository, never()).save(any());
    }

    @Test
    @DisplayName("CHD-11: GV lưu khai báo trực tiếp ⇒ 3013 nếu trẻ trong lớp, 3001 nếu trẻ ngoài phạm vi")
    void teacherSaveDeclarationChecksScopeBeforeApprovalNotice() {
        CurrentUser teacher = new CurrentUser(
                20L,
                "kc-20",
                "gv.lop",
                "GV",
                null,
                java.util.Set.of(CareNestRole.TEACHER),
                java.util.Set.of(com.carenest.utils.enums.Permission.CHILD_PROFILE_EDIT));
        var request = new com.carenest.dto.child.HealthDeclarationRequest(
                declarationInput("REPORTED", List.of(), "NONE_REPORTED", null), "Phụ huynh bổ sung");

        assertThatThrownBy(() -> service.saveDeclaration(teacher, CHILD_ID, request))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_PROFILE_CHANGE_NEEDS_APPROVAL));

        org.mockito.Mockito.doThrow(GlobalException.of(ApiCode.CHILD_ACCESS_DENIED))
                .when(childAccessPolicy)
                .checkCanViewChild(teacher, CHILD_ID);
        assertThatThrownBy(() -> service.saveDeclaration(teacher, CHILD_ID, request))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ACCESS_DENIED));
        verify(declarationRepository, never()).save(any());
    }

    @Test
    @DisplayName(
            "G94-180/CHD-07: chuyển sang Có tiếp xúc lao ⇒ báo; dị ứng thay danh sách, lịch sử UPDATED kèm thay đổi")
    void saveDeclarationNotifiesTbContactAndReplacesAllergies() {
        given(declarationRepository.findById(CHILD_ID))
                .willReturn(Optional.of(declaration(com.carenest.utils.enums.DeclarationState.NONE_REPORTED, null)));
        var request = new com.carenest.dto.child.HealthDeclarationRequest(
                declarationInput("REPORTED", List.of("Tôm"), "REPORTED", "2026-11-01"), "Phụ huynh bổ sung");

        service.saveDeclaration(PRINCIPAL, CHILD_ID, request);

        verify(allergyRepository).deleteByChildId(CHILD_ID);
        verify(tbNotifier).contactReported(CHILD_ID, "Bé An", CAMPUS_ID, LocalDate.of(2026, 11, 1), PRINCIPAL.userId());
        verify(tbNotifier, never()).contactDateChanged(any(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<com.carenest.entity.child.ChildHealthDeclarationHistory> history =
                ArgumentCaptor.forClass(com.carenest.entity.child.ChildHealthDeclarationHistory.class);
        verify(declarationHistoryRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(com.carenest.utils.enums.HealthDeclarationAction.UPDATED);
        assertThat(history.getValue().getAllergyChange()).contains("Tôm");
        assertThat(history.getValue().getHistoryChange()).contains("2026-11-01");
    }

    @Test
    @DisplayName("G94-180/CHD-07: đã Có tiếp xúc, đổi ngày ⇒ báo đổi ngày (không báo tiếp xúc mới)")
    void saveDeclarationNotifiesTbDateChange() {
        given(declarationRepository.findById(CHILD_ID))
                .willReturn(Optional.of(
                        declaration(com.carenest.utils.enums.DeclarationState.REPORTED, LocalDate.of(2026, 10, 1))));
        var request = new com.carenest.dto.child.HealthDeclarationRequest(
                declarationInput("NONE_REPORTED", List.of(), "REPORTED", "2026-11-01"), "Sửa ngày");

        service.saveDeclaration(PRINCIPAL, CHILD_ID, request);

        verify(tbNotifier)
                .contactDateChanged(
                        CHILD_ID,
                        "Bé An",
                        CAMPUS_ID,
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 1),
                        "sửa khai báo ăn và sức khỏe",
                        PRINCIPAL.userId());
        verify(tbNotifier, never()).contactReported(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CHD-13: bỏ báo ăn giữ dị ứng, chế độ ăn đã khai")
    void saveDeclarationWithoutMealsKeepsAllergiesAndDiet() {
        var reported = com.carenest.utils.enums.DeclarationState.REPORTED;
        var none = new com.carenest.entity.child.ChildHealthDeclaration.Answer(
                com.carenest.utils.enums.DeclarationState.NONE_REPORTED, null);
        var existing = com.carenest.entity.child.ChildHealthDeclaration.declare(
                CHILD_ID,
                reported,
                new com.carenest.entity.child.ChildHealthDeclaration.Answer(reported, "Ăn chay"),
                new com.carenest.entity.child.ChildHealthDeclaration.Answer(
                        com.carenest.utils.enums.DeclarationState.NOT_PROVIDED, null),
                none,
                none,
                com.carenest.utils.enums.DeclarationState.NONE_REPORTED,
                null,
                9L,
                Instant.parse("2026-09-01T03:00:00Z"));
        given(declarationRepository.findById(CHILD_ID)).willReturn(Optional.of(existing));
        given(allergyRepository.findAllByChildIdOrderByIdAsc(CHILD_ID))
                .willReturn(List.of(com.carenest.entity.child.ChildAllergy.of(CHILD_ID, "Sữa")));
        var noneInput = new com.carenest.dto.child.DeclarationAnswerInput("NONE_REPORTED", null);
        var input = new com.carenest.dto.child.HealthDeclarationInput(
                new com.carenest.dto.child.AllergiesInput("REPORTED", List.of("Tôm")),
                noneInput,
                new com.carenest.dto.child.DeclarationAnswerInput("REPORTED", "Ghi chú mới"),
                new com.carenest.dto.child.MedicalHistoryInput(
                        noneInput, noneInput, new com.carenest.dto.child.TbContactInput("NONE_REPORTED", null)),
                false);

        service.saveDeclaration(
                PRINCIPAL, CHILD_ID, new com.carenest.dto.child.HealthDeclarationRequest(input, "Ngừng ăn bán trú"));

        verify(allergyRepository, never()).deleteByChildId(any());
        verify(allergyRepository, never()).saveAll(any());
        assertThat(existing.getAllergiesState()).isEqualTo(reported);
        assertThat(existing.getDietState()).isEqualTo(reported);
        assertThat(existing.getDietText()).isEqualTo("Ăn chay");
        assertThat(existing.getOtherNotesText()).isEqualTo("Ghi chú mới");
        assertThat(child.isMealRegistered()).isFalse();
    }

    private static com.carenest.entity.child.ChildHealthDeclaration declaration(
            com.carenest.utils.enums.DeclarationState tb, LocalDate tbDate) {
        var none = new com.carenest.entity.child.ChildHealthDeclaration.Answer(
                com.carenest.utils.enums.DeclarationState.NONE_REPORTED, null);
        var notProvided = new com.carenest.entity.child.ChildHealthDeclaration.Answer(
                com.carenest.utils.enums.DeclarationState.NOT_PROVIDED, null);
        return com.carenest.entity.child.ChildHealthDeclaration.declare(
                CHILD_ID,
                com.carenest.utils.enums.DeclarationState.NONE_REPORTED,
                none,
                notProvided,
                none,
                none,
                tb,
                tbDate,
                9L,
                Instant.parse("2026-09-01T03:00:00Z"));
    }

    private static com.carenest.dto.child.HealthDeclarationInput declarationInput(
            String allergyState, List<String> allergies, String tbState, String tbDate) {
        var none = new com.carenest.dto.child.DeclarationAnswerInput("NONE_REPORTED", null);
        return new com.carenest.dto.child.HealthDeclarationInput(
                new com.carenest.dto.child.AllergiesInput(allergyState, allergies),
                none,
                new com.carenest.dto.child.DeclarationAnswerInput("NOT_PROVIDED", null),
                new com.carenest.dto.child.MedicalHistoryInput(
                        none, none, new com.carenest.dto.child.TbContactInput(tbState, tbDate)));
    }

    private static ChildGuardian guardian(Long id, int position, String citizenId, String phone, Long userId) {
        ChildGuardian g =
                ChildGuardian.create(CHILD_ID, position, "Mẹ An", GuardianRelationship.MOTHER, phone, citizenId, null);
        if (userId != null) {
            g.linkAccount(userId, java.time.Instant.EPOCH);
        }
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }

    private static GuardianInput guardianInput(Long id, String citizenId, String phone) {
        return new GuardianInput("Mẹ An", "MOTHER", phone, citizenId, null, null, id);
    }

    private static UpdateChildRequest request(String dateOfBirth, List<GuardianInput> guardians) {
        ChildProfileInput profile = new ChildProfileInput(
                null, YesNo.NO.name(), 39, "Kinh", "Không", null, new ChildAddressInput("Hà Nội", "Phường A", "Số 1"));
        return new UpdateChildRequest(
                new EnrollmentChildInput("Bé An", dateOfBirth, "FEMALE", guardians, profile), "Sửa thông tin");
    }
}
