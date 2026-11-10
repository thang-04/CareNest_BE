package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.carenest.dto.attendance.PickupRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.exception.GlobalException;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.PickupRecordRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.account.ParentPhotoService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.PickupVerification;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test service bằng Mockito (không DB): kiểm tra lỗi khi trả trẻ. Dữ liệu giả. */
@ExtendWith(MockitoExtension.class)
class PickupServiceImplTest {

    // Hôm nay 2026-11-10, 16:00 giờ trường
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-10T09:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long CHILD_ID = 1L;
    private static final long CLASS_ID = 10L;

    @Mock
    private AttendanceDayRules rules;

    @Mock
    private PickupRecordRepository pickupRepository;

    @Mock
    private AttendanceRecordRepository attendanceRepository;

    @Mock
    private ClassroomService classroomService;

    @Mock
    private EnrollmentService enrollmentService;

    @Mock
    private StaffAssignmentService staffAssignmentService;

    @Mock
    private ChildService childService;

    @Mock
    private ChildIntakeService childIntakeService;

    @Mock
    private GuardianChildLinkService guardianChildLinkService;

    @Mock
    private ParentPhotoService parentPhotoService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AccountService accountService;

    private PickupServiceImpl service;
    private final CurrentUser teacher =
            new CurrentUser(7L, "kc-7", "teacher", "Cô A (giả)", null, Set.of(CareNestRole.TEACHER));

    @BeforeEach
    void setUp() {
        service = new PickupServiceImpl(
                rules,
                pickupRepository,
                attendanceRepository,
                classroomService,
                enrollmentService,
                staffAssignmentService,
                childService,
                childIntakeService,
                guardianChildLinkService,
                parentPhotoService,
                notificationService,
                accountService,
                CLOCK);
        // Arrange chung: trẻ đang học lớp của cô, năm học đang hoạt động
        ClassroomInfoResponse cls = new ClassroomInfoResponse(
                CLASS_ID,
                "Lớp A",
                1L,
                1L,
                SchoolYearStatus.ACTIVE,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 5, 31),
                1L,
                List.of(),
                null,
                ClassroomStatus.ACTIVE);
        when(rules.today()).thenReturn(TODAY);
        when(enrollmentService.findActiveClassroomIds(CHILD_ID, TODAY)).thenReturn(List.of(CLASS_ID));
        when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.of(cls));
        when(rules.canRecord(any(), any())).thenReturn(true);
        when(childIntakeService.findGuardianContacts(CHILD_ID)).thenReturn(List.of());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PCK-02: ưu tiên ảnh tiếp nhận khi chưa có tài khoản, fallback ảnh tài khoản")
    void pickupUsesEnrollmentPhotoWithoutAnAccount() {
        when(childIntakeService.findGuardianContacts(CHILD_ID))
                .thenReturn(List.of(
                        new com.carenest.dto.child.ChildGuardianContact(
                                0,
                                "Mẹ giả",
                                com.carenest.utils.enums.GuardianRelationship.MOTHER,
                                "0900000001",
                                null,
                                "/children/1/guardian-photos/11"),
                        new com.carenest.dto.child.ChildGuardianContact(
                                1,
                                "Bố giả",
                                com.carenest.utils.enums.GuardianRelationship.FATHER,
                                "0900000002",
                                71L,
                                null)));
        when(parentPhotoService.findUserIdsWithPhoto(List.of(71L))).thenReturn(Set.of(71L));
        var response = service.getChild(teacher, CHILD_ID);
        assertThat(response.guardians().getFirst().photoUrl()).isEqualTo("/children/1/guardian-photos/11");
        assertThat(response.guardians().get(1).photoUrl()).isEqualTo("/children/1/guardians/71/photo");
    }

    @Test // A: trẻ điểm danh vắng ⇒ không trả được
    void absentChildCannotBeHandedOver() {
        when(attendanceRepository.findByChildIdInAndDate(List.of(CHILD_ID), TODAY))
                .thenReturn(List.of(AttendanceRecord.create(
                        CHILD_ID, CLASS_ID, 1L, TODAY, AttendanceStatus.UNEXCUSED, List.of(), 7L, CLOCK.instant())));

        assertThatThrownBy(() -> service.recordResult(
                        teacher,
                        new PickupRequest(
                                CHILD_ID, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(15, 30), "Mẹ đón")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.PICKUP_CHILD_ABSENT);
    }

    @Test // A: gọi phụ huynh mà không ghi nội dung cuộc gọi
    void phoneVerificationNeedsCallNote() {
        assertThatThrownBy(() -> service.recordResult(
                        teacher,
                        new PickupRequest(
                                CHILD_ID, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(15, 30), " ")))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getFieldErrors())
                        .extracting(FieldErrorResponse::field)
                        .containsExactly("note"));
    }

    @Test // B: giờ trả sau giờ hiện tại 1 phút ⇒ lỗi ô handoverTime
    void handoverTimeCannotBeInTheFuture() {
        assertThatThrownBy(() -> service.recordResult(
                        teacher,
                        new PickupRequest(
                                CHILD_ID, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(16, 1), "Mẹ đón")))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getFieldErrors())
                        .extracting(FieldErrorResponse::field)
                        .containsExactly("handoverTime"));
    }
}
