package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.entity.child.ChildHistory;
import com.carenest.entity.child.ChildProfile;
import com.carenest.entity.child.Enrollment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.repository.child.ChildHistoryRepository;
import com.carenest.repository.child.ChildProfileRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.child.YearTransitionService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildHistoryAction;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.NotificationType;
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
import tools.jackson.databind.json.JsonMapper;

class ChildRecordServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZONE);
    private static final Long CHILD_ID = 1L;
    private static final Long CLASS_ID = 10L;
    private static final Long CAMPUS_ID = 2L;
    private static final CurrentUser PRINCIPAL = TestPermissions.userWithDefaults(9L, CareNestRole.PRINCIPAL);

    private final ChildRepository childRepository = mock(ChildRepository.class);
    private final ChildProfileRepository profileRepository = mock(ChildProfileRepository.class);
    private final ChildGuardianRepository guardianRepository = mock(ChildGuardianRepository.class);
    private final ChildHistoryRepository historyRepository = mock(ChildHistoryRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final ChildIntakeService intakeService = mock(ChildIntakeService.class);
    private final GuardianChildLinkService linkService = mock(GuardianChildLinkService.class);
    private final ClassroomService classroomService = mock(ClassroomService.class);
    private final StaffAssignmentService assignmentService = mock(StaffAssignmentService.class);
    private final StaffAccessPolicy policy = mock(StaffAccessPolicy.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
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
            notificationService,
            events,
            JsonMapper.builder().build(),
            CLOCK);

    private Child child;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        child = Child.enroll(
                "HS26001", "Bé An", LocalDate.of(2022, 3, 1), Gender.FEMALE, true, null, LocalDate.of(2026, 9, 1));
        ReflectionTestUtils.setField(child, "id", CHILD_ID);
        enrollment = Enrollment.start(CHILD_ID, CLASS_ID, LocalDate.of(2026, 9, 1));
        given(childRepository.findById(CHILD_ID)).willReturn(Optional.of(child));
        given(policy.managesWholeSchool(PRINCIPAL)).willReturn(true);
        given(enrollmentRepository.findActiveByChildIdIn(List.of(CHILD_ID), TODAY))
                .willReturn(List.of(enrollment));
        given(enrollmentRepository.findOpenByChildId(CHILD_ID, TODAY)).willReturn(List.of(enrollment));
        given(classroomService.findCampusId(CLASS_ID)).willReturn(Optional.of(CAMPUS_ID));
        given(assignmentService.findCurrentTeacherIds(List.of(CLASS_ID), TODAY))
                .willReturn(Map.of(CLASS_ID, List.of(7L, 8L)));
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
    @DisplayName("G94-180/CHD-03: nghỉ ngay ngày vào lớp ⇒ bỏ lớp đó (không có ngày học nào)")
    void withdrawOnFirstClassDayDropsEnrollment() {
        Enrollment today = Enrollment.start(CHILD_ID, CLASS_ID, TODAY);
        given(enrollmentRepository.findOpenByChildId(CHILD_ID, TODAY)).willReturn(List.of(today));

        service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest("2026-11-10", "Lý do"));

        verify(enrollmentRepository).delete(today);
    }

    @Test
    @DisplayName("G94-180/CHD-03: ngày nghỉ ở tương lai, trước ngày tiếp nhận, thiếu lý do ⇒ lỗi theo ô")
    void withdrawValidatesFields() {
        assertThatThrownBy(() -> service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest("2026-11-11", "")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .containsExactly("leftAt", "reason"));
        assertThatThrownBy(() -> service.withdraw(PRINCIPAL, CHILD_ID, new WithdrawChildRequest("2026-08-31", "x")))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .containsExactly("leftAt"));
        verify(childRepository, never()).save(any());
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

    private static ChildGuardian guardian(Long id, int position, String citizenId, String phone, Long userId) {
        ChildGuardian g = ChildGuardian.create(
                CHILD_ID, position, "Mẹ An", GuardianRelationship.MOTHER, phone, citizenId, null, userId);
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
