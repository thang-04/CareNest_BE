package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.child.ChildProfileChangeCreateRequest;
import com.carenest.dto.child.ChildProfileChangeResponse;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.entity.account.ChildProfileChangeRequest;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.ChildProfileChangeRequestRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.account.ChildProfileEditService;
import com.carenest.service.account.PermissionService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.ProfileChangeKind;
import com.carenest.utils.enums.ProfileChangeStatus;
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
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class ChildProfileChangeRequestServiceImplTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-16T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    private static final Long CHILD_ID = 1L;
    private static final Long CAMPUS_ID = 10L;
    private static final CurrentUser TEACHER = new CurrentUser(
            20L,
            "kc-20",
            "gv.lop",
            "Cô Lan",
            null,
            Set.of(CareNestRole.TEACHER),
            Set.of(Permission.CHILD_PROFILE_VIEW, Permission.CHILD_PROFILE_EDIT));
    private static final CurrentUser PRINCIPAL = TestPermissions.userWithDefaults(9L, CareNestRole.PRINCIPAL);
    private static final CurrentUser VICE = TestPermissions.userWithDefaults(3L, CareNestRole.VICE_PRINCIPAL);
    private static final String PROFILE_JSON = "{\"child\":null,\"reason\":\"Phụ huynh báo đổi địa chỉ\"}";

    private final ChildProfileChangeRequestRepository repository = mock(ChildProfileChangeRequestRepository.class);
    private final ChildRecordService childRecordService = mock(ChildRecordService.class);
    private final ChildProfileEditService profileEditService = mock(ChildProfileEditService.class);
    private final ChildIntakeService intakeService = mock(ChildIntakeService.class);
    private final StaffAccessPolicy staffAccessPolicy = mock(StaffAccessPolicy.class);
    private final StaffAssignmentService assignmentService = mock(StaffAssignmentService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AccountService accountService = mock(AccountService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ChildProfileChangeRequestServiceImpl service = new ChildProfileChangeRequestServiceImpl(
            repository,
            childRecordService,
            profileEditService,
            intakeService,
            staffAccessPolicy,
            assignmentService,
            permissionService,
            accountService,
            notificationService,
            JsonMapper.builder().build(),
            CLOCK);

    @BeforeEach
    void setUp() {
        given(intakeService.findChildSummaries(List.of(CHILD_ID)))
                .willReturn(List.of(new GuardianChildSummary(
                        CHILD_ID, "HS26001", "Bé An", ChildStatus.ACTIVE, "Lớp Chồi 1", CAMPUS_ID)));
        given(accountService.findFullNames(anyCollection())).willReturn(Map.of(20L, "Cô Lan"));
        given(staffAccessPolicy.managesWholeSchool(PRINCIPAL)).willReturn(true);
        given(staffAccessPolicy.managedCampusIds(VICE)).willReturn(Set.of(CAMPUS_ID));
        given(repository.save(any(ChildProfileChangeRequest.class))).willAnswer(invocation -> {
            ChildProfileChangeRequest saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 100L);
            }
            return saved;
        });
    }

    @Test
    @DisplayName("CHD-11: GV gửi sửa hồ sơ ⇒ kiểm hợp lệ, hủy yêu cầu chờ cũ, báo HT + PHT có quyền, hồ sơ chưa đổi")
    void teacherSubmitCreatesPendingRequestWithoutWriting() {
        ChildProfileChangeRequest old = pending(50L, TEACHER.userId());
        given(repository.findByChildIdAndKindAndStatus(
                        CHILD_ID, ProfileChangeKind.PROFILE, ProfileChangeStatus.PENDING))
                .willReturn(Optional.of(old));
        given(assignmentService.findPrincipalIds(TODAY)).willReturn(Set.of(9L));
        given(assignmentService.findCampusVicePrincipalIds(CAMPUS_ID, TODAY)).willReturn(Set.of(3L, 4L));
        given(permissionService.hasEffectivePermission(3L, CareNestRole.VICE_PRINCIPAL, Permission.CHILD_PROFILE_EDIT))
                .willReturn(true);
        UpdateChildRequest profile = new UpdateChildRequest(null, "Phụ huynh báo đổi địa chỉ");

        ChildProfileChangeResponse response = service.submit(
                TEACHER, CHILD_ID, new ChildProfileChangeCreateRequest(ProfileChangeKind.PROFILE, profile, null));

        verify(childRecordService).prepareEdit(TEACHER, CHILD_ID, profile);
        verify(profileEditService, never()).update(any(), any(), any());
        assertThat(old.getStatus()).isEqualTo(ProfileChangeStatus.CANCELLED);
        assertThat(response.status()).isEqualTo(ProfileChangeStatus.PENDING);
        assertThat(response.canDecide()).isFalse();
        verify(notificationService)
                .notifyUsers(
                        eq(Set.of(9L, 3L)),
                        eq(NotificationType.CHILD_PROFILE_CHANGE_REQUESTED),
                        any(),
                        any(),
                        eq(CHILD_ID));
    }

    @Test
    @DisplayName("CHD-11: GV gửi sửa khai báo ⇒ thông báo ghi \"đề nghị sửa khai báo ăn và sức khỏe\"")
    void declarationRequestNotificationUsesMealAndHealthName() {
        given(assignmentService.findPrincipalIds(TODAY)).willReturn(Set.of(9L));
        var declaration = new com.carenest.dto.child.HealthDeclarationRequest(null, "Phụ huynh báo dị ứng");

        service.submit(
                TEACHER,
                CHILD_ID,
                new ChildProfileChangeCreateRequest(ProfileChangeKind.HEALTH_DECLARATION, null, declaration));

        verify(notificationService)
                .notifyUsers(
                        eq(Set.of(9L)),
                        eq(NotificationType.CHILD_PROFILE_CHANGE_REQUESTED),
                        any(),
                        org.mockito.ArgumentMatchers.contains("đề nghị sửa khai báo ăn và sức khỏe của"),
                        eq(CHILD_ID));
    }

    @Test
    @DisplayName("CHD-11: HT/PHT không gửi yêu cầu (sửa trực tiếp) ⇒ 403")
    void leaderCannotSubmit() {
        expectCode(
                () -> service.submit(
                        PRINCIPAL,
                        CHILD_ID,
                        new ChildProfileChangeCreateRequest(
                                ProfileChangeKind.PROFILE, new UpdateChildRequest(null, "x"), null)),
                ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("CHD-11: HT duyệt ⇒ áp luồng sửa hồ sơ với tư cách HT, APPROVED, báo GV")
    void principalApproveAppliesAsApprover() {
        given(repository.lockById(100L)).willReturn(Optional.of(pending(100L, TEACHER.userId())));

        ChildProfileChangeResponse response = service.approve(PRINCIPAL, 100L);

        verify(profileEditService).update(eq(PRINCIPAL), eq(CHILD_ID), any(UpdateChildRequest.class));
        assertThat(response.status()).isEqualTo(ProfileChangeStatus.APPROVED);
        assertThat(response.decidedBy()).isEqualTo(PRINCIPAL.userId());
        verify(notificationService)
                .notifyUsers(
                        eq(Set.of(TEACHER.userId())),
                        eq(NotificationType.CHILD_PROFILE_CHANGE_DECIDED),
                        any(),
                        any(),
                        eq(CHILD_ID));
    }

    @Test
    @DisplayName("CHD-11, AUTH-13: PHT điểm trường khác duyệt ⇒ 403, hồ sơ không đổi")
    void vicePrincipalOfOtherCampusIsDenied() {
        given(staffAccessPolicy.managedCampusIds(VICE)).willReturn(Set.of(99L));
        given(repository.lockById(100L)).willReturn(Optional.of(pending(100L, TEACHER.userId())));

        expectCode(() -> service.approve(VICE, 100L), ApiCode.CHILD_PROFILE_CHANGE_DECIDE_DENIED);
        verify(profileEditService, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("CHD-11: PHT điểm trường của trẻ duyệt được")
    void vicePrincipalOfChildCampusApproves() {
        given(repository.lockById(100L)).willReturn(Optional.of(pending(100L, TEACHER.userId())));

        assertThat(service.approve(VICE, 100L).status()).isEqualTo(ProfileChangeStatus.APPROVED);
    }

    @Test
    @DisplayName("AUTH-12: người gửi không tự duyệt; GV không duyệt")
    void requesterAndTeacherCannotDecide() {
        given(repository.lockById(100L)).willReturn(Optional.of(pending(100L, PRINCIPAL.userId())));
        expectCode(() -> service.approve(PRINCIPAL, 100L), ApiCode.CHILD_PROFILE_CHANGE_DECIDE_DENIED);
        expectCode(() -> service.approve(TEACHER, 100L), ApiCode.CHILD_PROFILE_CHANGE_DECIDE_DENIED);
    }

    @Test
    @DisplayName("CHD-11: yêu cầu đã xử lý ⇒ 409")
    void decidedRequestIsNotPending() {
        ChildProfileChangeRequest done = pending(100L, TEACHER.userId());
        done.approve(9L, CLOCK.instant());
        given(repository.lockById(100L)).willReturn(Optional.of(done));

        expectCode(() -> service.approve(PRINCIPAL, 100L), ApiCode.CHILD_PROFILE_CHANGE_NOT_PENDING);
    }

    @Test
    @DisplayName("CHD-11: từ chối bắt buộc lý do; có lý do ⇒ REJECTED, giữ lý do, hồ sơ không đổi")
    void rejectRequiresNote() {
        given(repository.lockById(100L)).willReturn(Optional.of(pending(100L, TEACHER.userId())));
        expectCode(() -> service.reject(PRINCIPAL, 100L, "  "), ApiCode.INVALID_REQUEST_DATA);

        ChildProfileChangeResponse rejected = service.reject(PRINCIPAL, 100L, " Sai địa chỉ ");

        assertThat(rejected.status()).isEqualTo(ProfileChangeStatus.REJECTED);
        assertThat(rejected.decisionNote()).isEqualTo("Sai địa chỉ");
        verify(profileEditService, never()).update(any(), any(), any());
    }

    private static ChildProfileChangeRequest pending(Long id, Long requestedBy) {
        ChildProfileChangeRequest request = ChildProfileChangeRequest.open(
                CHILD_ID, ProfileChangeKind.PROFILE, PROFILE_JSON, requestedBy, Instant.parse("2026-11-15T09:00:00Z"));
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private static void expectCode(Runnable call, ApiCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(code));
    }
}
