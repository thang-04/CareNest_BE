package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carenest.dto.attendance.PickupBoardResponse;
import com.carenest.dto.attendance.PickupChildResponse;
import com.carenest.dto.attendance.PickupRecordResponse;
import com.carenest.dto.attendance.PickupRequest;
import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.ChildGuardianContact;
import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.entity.attendance.PickupRecord;
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
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PickupOutcome;
import com.carenest.utils.enums.PickupStatus;
import com.carenest.utils.enums.PickupVerification;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test service bằng Mockito (không DB): bảng đón trẻ, hồ sơ người đón, ghi kết quả trả trẻ, lịch sử. Dữ liệu giả. */
@ExtendWith(MockitoExtension.class)
class PickupServiceImplTest {

    // Hôm nay 2026-11-10, 16:00 giờ trường
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T09:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final LocalDate YEAR_START = LocalDate.of(2026, 9, 1);
    private static final long CHILD_ID = 1L;
    private static final long OTHER_CHILD_ID = 2L;
    private static final long THIRD_CHILD_ID = 3L;
    private static final long CLASS_ID = 10L;
    private static final long CAMPUS_ID = 1L;
    private static final long TEACHER_ID = 7L;

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

    @Captor
    private ArgumentCaptor<PickupRecord> savedRecord;

    @Captor
    private ArgumentCaptor<Collection<Long>> notifiedUsers;

    private PickupServiceImpl service;
    private final CurrentUser teacher = new CurrentUser(
            TEACHER_ID,
            "kc-7",
            "teacher",
            "Cô A (giả)",
            null,
            Set.of(CareNestRole.TEACHER),
            Set.of(Permission.PICKUP_RECORD));

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
    }

    @Nested
    @DisplayName("getBoard — bảng đón trẻ của lớp (PCK-02, ATT-10)")
    class GetBoard {

        @Test
        @DisplayName("Lớp không tồn tại ⇒ CLASS_NOT_FOUND")
        void unknownClass() {
            when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.empty());

            assertApiCode(() -> service.getBoard(teacher, CLASS_ID, null), ApiCode.CLASS_NOT_FOUND);
        }

        @Test
        @DisplayName("Thiếu quyền PICKUP_RECORD ⇒ PICKUP_ACCESS_DENIED, không đọc dữ liệu trẻ")
        void missingPermission() {
            when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.of(activeClass()));
            CurrentUser noPermission = new CurrentUser(
                    TEACHER_ID, "kc-7", "teacher", "Cô A (giả)", null, Set.of(CareNestRole.TEACHER), Set.of());

            assertApiCode(() -> service.getBoard(noPermission, CLASS_ID, null), ApiCode.PICKUP_ACCESS_DENIED);
            verifyNoInteractions(enrollmentService, attendanceRepository, pickupRepository);
        }

        @Test
        @DisplayName("GV không phụ trách lớp ⇒ PICKUP_ACCESS_DENIED")
        void teacherNotInCharge() {
            when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.of(activeClass()));
            when(rules.canRecord(teacher, activeClass())).thenReturn(false);

            assertApiCode(() -> service.getBoard(teacher, CLASS_ID, null), ApiCode.PICKUP_ACCESS_DENIED);
        }

        @Test
        @DisplayName("Lớp của năm học đã đóng ⇒ PICKUP_YEAR_NOT_ACTIVE")
        void closedYear() {
            ClassroomInfoResponse closed = classOf(CLASS_ID, SchoolYearStatus.CLOSED);
            when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.of(closed));
            when(rules.canRecord(teacher, closed)).thenReturn(true);

            assertApiCode(() -> service.getBoard(teacher, CLASS_ID, null), ApiCode.PICKUP_YEAR_NOT_ACTIVE);
        }

        @Test
        @DisplayName("Ngày sau hôm nay ⇒ PICKUP_DATE_INVALID")
        void futureDate() {
            givenTeacherCanRecordClass();

            assertApiCode(() -> service.getBoard(teacher, CLASS_ID, TODAY.plusDays(1)), ApiCode.PICKUP_DATE_INVALID);
        }

        @Test
        @DisplayName("Ngày trước đầu năm học (năm đã bắt đầu) ⇒ PICKUP_DATE_INVALID")
        void dateBeforeSchoolYear() {
            givenTeacherCanRecordClass();

            assertApiCode(
                    () -> service.getBoard(teacher, CLASS_ID, YEAR_START.minusDays(1)), ApiCode.PICKUP_DATE_INVALID);
        }

        @Test
        @DisplayName("Lớp chưa có trẻ ⇒ bảng rỗng, không truy vấn điểm danh/đón")
        void emptyClass() {
            givenTeacherCanRecordClass();
            when(enrollmentService.findChildrenInClassroom(CLASS_ID, TODAY)).thenReturn(List.of());

            PickupBoardResponse board = service.getBoard(teacher, CLASS_ID, null);

            assertThat(board.rows()).isEmpty();
            assertThat(board.date()).isEqualTo(TODAY);
            verifyNoInteractions(attendanceRepository, pickupRepository);
        }

        @Test
        @DisplayName("Trạng thái từng trẻ: đã đón / chờ đón / vắng / chưa điểm danh; lần trả đầu tiên là lần đón")
        void rowStatuses() {
            givenTeacherCanRecordClass();
            long unmarkedChild = 4L;
            List<Long> ids = List.of(CHILD_ID, OTHER_CHILD_ID, THIRD_CHILD_ID, unmarkedChild);
            when(enrollmentService.findChildrenInClassroom(CLASS_ID, TODAY))
                    .thenReturn(ids.stream()
                            .map(id -> new ClassChildResponse(id, "Bé " + id, LocalDate.of(2022, 1, 1)))
                            .toList());
            when(childService.findChildBriefs(any()))
                    .thenReturn(Map.of(
                            CHILD_ID, brief(CHILD_ID),
                            OTHER_CHILD_ID, brief(OTHER_CHILD_ID),
                            THIRD_CHILD_ID, brief(THIRD_CHILD_ID)));
            when(attendanceRepository.findByChildIdInAndDate(ids, TODAY))
                    .thenReturn(List.of(
                            attendance(CHILD_ID, AttendanceStatus.PRESENT),
                            attendance(OTHER_CHILD_ID, AttendanceStatus.PRESENT),
                            attendance(THIRD_CHILD_ID, AttendanceStatus.EXCUSED)));
            PickupRecord declined = record(CHILD_ID, PickupVerification.PHONE_DECLINED, null);
            PickupRecord handed = record(CHILD_ID, PickupVerification.PHONE_CONFIRMED, LocalTime.of(15, 30));
            when(pickupRepository.findByChildIdInAndDateOrderByRecordedAtAscIdAsc(ids, TODAY))
                    .thenReturn(List.of(declined, handed));
            when(accountService.findFullNames(any())).thenReturn(Map.of(TEACHER_ID, "Cô A (giả)"));

            PickupBoardResponse board = service.getBoard(teacher, CLASS_ID, null);

            assertThat(board.rows())
                    .extracting(PickupBoardResponse.PickupRow::status)
                    .containsExactly(
                            PickupStatus.PICKED_UP, PickupStatus.WAITING, PickupStatus.ABSENT, PickupStatus.UNMARKED);
            PickupBoardResponse.PickupRow picked = board.rows().getFirst();
            assertThat(picked.pickup().outcome()).isEqualTo(PickupOutcome.HANDED_OVER);
            assertThat(picked.attempts())
                    .extracting(PickupRecordResponse::verification)
                    .containsExactly(PickupVerification.PHONE_DECLINED, PickupVerification.PHONE_CONFIRMED);
            assertThat(picked.pickup().className()).isEqualTo("Lớp A");
            assertThat(picked.pickup().recordedByName()).isEqualTo("Cô A (giả)");
            assertThat(board.rows().get(1).pickup()).isNull();
            assertThat(board.rows().get(3).attendanceStatus()).isNull();
            // Trẻ không có thông tin rút gọn vẫn có dòng, tên rỗng
            assertThat(board.rows().get(3).child().fullName()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getChild — trẻ và danh sách phụ huynh kèm ảnh (PCK-02)")
    class GetChild {

        @Test
        @DisplayName("PCK-02: ưu tiên ảnh tiếp nhận khi chưa có tài khoản, fallback ảnh tài khoản")
        void pickupUsesEnrollmentPhotoWithoutAnAccount() {
            givenChildInTeachersClass();
            when(childIntakeService.findGuardianContacts(CHILD_ID))
                    .thenReturn(List.of(
                            new ChildGuardianContact(
                                    0,
                                    "Mẹ giả",
                                    GuardianRelationship.MOTHER,
                                    "0900000001",
                                    null,
                                    "/children/1/guardian-photos/11"),
                            new ChildGuardianContact(
                                    1, "Bố giả", GuardianRelationship.FATHER, "0900000002", 71L, null)));
            when(parentPhotoService.findUserIdsWithPhoto(List.of(71L))).thenReturn(Set.of(71L));
            givenNoPickupToday();

            PickupChildResponse response = service.getChild(teacher, CHILD_ID);

            assertThat(response.guardians().getFirst().photoUrl()).isEqualTo("/children/1/guardian-photos/11");
            assertThat(response.guardians().get(1).photoUrl()).isEqualTo("/children/1/guardians/71/photo");
        }

        @Test
        @DisplayName("PCK-02: phụ huynh chưa có ảnh ⇒ photoUrl null (GV gọi phụ huynh)")
        void guardianWithoutPhoto() {
            givenChildInTeachersClass();
            when(childIntakeService.findGuardianContacts(CHILD_ID))
                    .thenReturn(List.of(
                            new ChildGuardianContact(0, "Mẹ giả", GuardianRelationship.MOTHER, "0900000001", 72L, null),
                            new ChildGuardianContact(
                                    1, "Bà giả", GuardianRelationship.GRANDMOTHER, "0900000003", null, null)));
            when(parentPhotoService.findUserIdsWithPhoto(List.of(72L))).thenReturn(Set.of());
            givenNoPickupToday();

            PickupChildResponse response = service.getChild(teacher, CHILD_ID);

            assertThat(response.guardians())
                    .extracting(PickupChildResponse.PickupGuardian::photoUrl)
                    .containsOnlyNulls();
            assertThat(response.classId()).isEqualTo(CLASS_ID);
            assertThat(response.date()).isEqualTo(TODAY);
            assertThat(response.row().status()).isEqualTo(PickupStatus.WAITING);
        }

        @Test
        @DisplayName("Trẻ không đang học lớp nào hôm nay ⇒ PICKUP_CHILD_NOT_FOUND")
        void childWithoutActiveClass() {
            when(rules.today()).thenReturn(TODAY);
            when(enrollmentService.findActiveClassroomIds(CHILD_ID, TODAY)).thenReturn(List.of());

            assertApiCode(() -> service.getChild(teacher, CHILD_ID), ApiCode.PICKUP_CHILD_NOT_FOUND);
        }

        @Test
        @DisplayName("Trẻ của lớp GV không phụ trách ⇒ 404 PICKUP_CHILD_NOT_FOUND, không lộ là có trẻ")
        void childOutsideTeachersScope() {
            when(rules.today()).thenReturn(TODAY);
            when(enrollmentService.findActiveClassroomIds(CHILD_ID, TODAY)).thenReturn(List.of(CLASS_ID));
            when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.of(activeClass()));
            when(rules.canRecord(any(), any())).thenReturn(false);

            assertApiCode(() -> service.getChild(teacher, CHILD_ID), ApiCode.PICKUP_CHILD_NOT_FOUND);
            verifyNoInteractions(childIntakeService);
        }
    }

    @Nested
    @DisplayName("recordResult — ghi kết quả trả trẻ (PCK-02, PCK-04, ATT-10)")
    class RecordResult {

        @Test
        @DisplayName("Thiếu childId ⇒ PICKUP_CHILD_NOT_FOUND")
        void missingChildId() {
            assertApiCode(
                    () -> service.recordResult(
                            teacher,
                            new PickupRequest(
                                    null, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(15, 30), "Mẹ đón")),
                    ApiCode.PICKUP_CHILD_NOT_FOUND);
        }

        @Test
        @DisplayName("Thiếu cách xác minh ⇒ lỗi ô verification")
        void missingVerification() {
            givenChildInTeachersClass();

            assertFieldErrors(
                    () -> service.recordResult(
                            teacher, new PickupRequest(CHILD_ID, null, null, LocalTime.of(15, 30), "")),
                    "verification");
        }

        @Test
        @DisplayName("Khớp ảnh nhưng vị trí phụ huynh không có trong hồ sơ ⇒ lỗi ô guardianPosition")
        void photoMatchWithUnknownGuardian() {
            givenChildInTeachersClass();
            givenGuardians();

            assertFieldErrors(
                    () -> service.recordResult(
                            teacher,
                            new PickupRequest(CHILD_ID, PickupVerification.PHOTO_MATCH, 5, LocalTime.of(15, 30), "")),
                    "guardianPosition");
        }

        @Test
        @DisplayName("Khớp ảnh mà không chọn phụ huynh và thiếu giờ trả ⇒ báo cả hai ô một lần")
        void photoMatchCollectsAllErrors() {
            givenChildInTeachersClass();
            givenGuardians();

            assertFieldErrors(
                    () -> service.recordResult(
                            teacher, new PickupRequest(CHILD_ID, PickupVerification.PHOTO_MATCH, null, null, "")),
                    "guardianPosition",
                    "handoverTime");
        }

        @Test // A: gọi phụ huynh mà không ghi nội dung cuộc gọi
        void phoneVerificationNeedsCallNote() {
            givenChildInTeachersClass();

            assertFieldErrors(
                    () -> service.recordResult(
                            teacher,
                            new PickupRequest(
                                    CHILD_ID, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(15, 30), " ")),
                    "note");
        }

        @Test
        @DisplayName("Ghi chú quá 500 ký tự ⇒ lỗi ô note")
        void noteTooLong() {
            givenChildInTeachersClass();
            givenGuardians();

            assertFieldErrors(
                    () -> service.recordResult(
                            teacher,
                            new PickupRequest(
                                    CHILD_ID,
                                    PickupVerification.PHOTO_MATCH,
                                    0,
                                    LocalTime.of(15, 30),
                                    "a".repeat(501))),
                    "note");
        }

        @Test // B: giờ trả sau giờ hiện tại 1 phút ⇒ lỗi ô handoverTime
        void handoverTimeCannotBeInTheFuture() {
            givenChildInTeachersClass();

            assertFieldErrors(
                    () -> service.recordResult(
                            teacher,
                            new PickupRequest(
                                    CHILD_ID, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(16, 1), "Mẹ đón")),
                    "handoverTime");
        }

        @Test
        @DisplayName("Trẻ chưa được điểm danh hôm nay ⇒ PICKUP_CHILD_NOT_PRESENT")
        void childNotMarkedToday() {
            givenChildInTeachersClass();
            when(attendanceRepository.findByChildIdInAndDate(List.of(CHILD_ID), TODAY))
                    .thenReturn(List.of());

            assertApiCode(() -> service.recordResult(teacher, phoneConfirmed()), ApiCode.PICKUP_CHILD_NOT_PRESENT);
            verify(pickupRepository, never()).save(any());
        }

        @Test // A: trẻ điểm danh vắng ⇒ không trả được
        void absentChildCannotBeHandedOver() {
            givenChildInTeachersClass();
            when(attendanceRepository.findByChildIdInAndDate(List.of(CHILD_ID), TODAY))
                    .thenReturn(List.of(attendance(CHILD_ID, AttendanceStatus.UNEXCUSED)));

            assertApiCode(() -> service.recordResult(teacher, phoneConfirmed()), ApiCode.PICKUP_CHILD_ABSENT);
        }

        @Test
        @DisplayName("Trẻ đã được trả hôm nay ⇒ PICKUP_ALREADY_HANDED_OVER, không lưu thêm")
        void alreadyHandedOver() {
            givenChildInTeachersClass();
            givenPresentToday();
            when(pickupRepository.existsByChildIdAndDateAndOutcome(CHILD_ID, TODAY, PickupOutcome.HANDED_OVER))
                    .thenReturn(true);

            assertApiCode(() -> service.recordResult(teacher, phoneConfirmed()), ApiCode.PICKUP_ALREADY_HANDED_OVER);
            verify(pickupRepository, never()).save(any());
        }

        @Test
        @DisplayName("PCK-02/PCK-04: khớp ảnh ⇒ lưu người đón theo hồ sơ, báo mọi tài khoản phụ huynh (bỏ trùng)")
        void photoMatchHandsOverAndNotifiesParents() {
            givenChildInTeachersClass();
            givenGuardians();
            givenReadyToSave();
            when(guardianChildLinkService.findLinksOfChild(CHILD_ID))
                    .thenReturn(List.of(link(71L), link(72L), link(71L)));
            when(childService.findChildBriefs(any())).thenReturn(Map.of(CHILD_ID, brief(CHILD_ID)));
            when(accountService.findFullNames(any())).thenReturn(Map.of(TEACHER_ID, "Cô A (giả)"));

            PickupRecordResponse result = service.recordResult(
                    teacher,
                    new PickupRequest(
                            CHILD_ID, PickupVerification.PHOTO_MATCH, 0, LocalTime.of(15, 30), "  Đón sớm  "));

            verify(pickupRepository).save(savedRecord.capture());
            PickupRecord saved = savedRecord.getValue();
            assertThat(saved.getOutcome()).isEqualTo(PickupOutcome.HANDED_OVER);
            assertThat(saved.getPickupPersonName()).isEqualTo("Mẹ giả");
            assertThat(saved.getPickupPersonRelationship()).isEqualTo(GuardianRelationship.MOTHER);
            assertThat(saved.getPickupPersonPhone()).isEqualTo("0900000001");
            assertThat(saved.getGuardianPosition()).isEqualTo((short) 0);
            assertThat(saved.getHandedOverAt()).isEqualTo(Instant.parse("2026-11-10T08:30:00Z"));
            assertThat(saved.getNote()).isEqualTo("Đón sớm");
            assertThat(saved.getCampusId()).isEqualTo(CAMPUS_ID);
            assertThat(saved.getRecordedBy()).isEqualTo(TEACHER_ID);
            assertThat(saved.getRecordedAt()).isEqualTo(CLOCK.instant());

            verify(notificationService)
                    .notifyUsers(
                            notifiedUsers.capture(),
                            eq(NotificationType.CHILD_PICKED_UP),
                            eq("Bé Nguyễn Văn 1 đã được đón"),
                            eq("Bé Nguyễn Văn 1 đã được đón lúc 15:30 ngày 10/11/2026. Người đón: Mẹ giả (Mẹ)."),
                            eq(CHILD_ID));
            assertThat(notifiedUsers.getValue()).containsExactly(71L, 72L);
            assertThat(result.notifiedParents()).isEqualTo(2);
            assertThat(result.recordedByName()).isEqualTo("Cô A (giả)");
            assertThat(result.className()).isEqualTo("Lớp A");
        }

        @Test
        @DisplayName("Gọi phụ huynh xác nhận ⇒ trả trẻ, thông báo không nêu tên người đón")
        void phoneConfirmedHidesPickupPersonInNotification() {
            givenChildInTeachersClass();
            givenReadyToSave();
            when(guardianChildLinkService.findLinksOfChild(CHILD_ID)).thenReturn(List.of(link(71L)));
            when(childService.findChildBriefs(any())).thenReturn(Map.of(CHILD_ID, brief(CHILD_ID)));
            when(accountService.findFullNames(any())).thenReturn(Map.of());

            PickupRecordResponse result = service.recordResult(teacher, phoneConfirmed());

            verify(pickupRepository).save(savedRecord.capture());
            assertThat(savedRecord.getValue().getPickupPersonName()).isEmpty();
            assertThat(savedRecord.getValue().getGuardianPosition()).isNull();
            verify(notificationService)
                    .notifyUsers(
                            any(),
                            eq(NotificationType.CHILD_PICKED_UP),
                            anyString(),
                            eq("Bé Nguyễn Văn 1 đã được đón lúc 15:30 ngày 10/11/2026."
                                    + " Người đón đã được phụ huynh xác nhận qua điện thoại."),
                            eq(CHILD_ID));
            assertThat(result.notifiedParents()).isEqualTo(1);
        }

        @Test
        @DisplayName("Giờ trả đúng bằng giờ hiện tại vẫn hợp lệ")
        void handoverAtCurrentMinute() {
            givenChildInTeachersClass();
            givenReadyToSave();
            when(guardianChildLinkService.findLinksOfChild(CHILD_ID)).thenReturn(List.of());
            when(childService.findChildBriefs(any())).thenReturn(Map.of());
            when(accountService.findFullNames(any())).thenReturn(Map.of());

            PickupRecordResponse result = service.recordResult(
                    teacher,
                    new PickupRequest(
                            CHILD_ID, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(16, 0), "Bố đón"));

            assertThat(result.handedOverAt()).isEqualTo(CLOCK.instant());
        }

        @Test
        @DisplayName("Trẻ chưa có tài khoản phụ huynh liên kết ⇒ vẫn trả, không gửi thông báo")
        void noLinkedParentAccount() {
            givenChildInTeachersClass();
            givenReadyToSave();
            when(guardianChildLinkService.findLinksOfChild(CHILD_ID)).thenReturn(List.of());
            when(childService.findChildBriefs(any())).thenReturn(Map.of(CHILD_ID, brief(CHILD_ID)));
            when(accountService.findFullNames(any())).thenReturn(Map.of());

            PickupRecordResponse result = service.recordResult(teacher, phoneConfirmed());

            assertThat(result.outcome()).isEqualTo(PickupOutcome.HANDED_OVER);
            assertThat(result.notifiedParents()).isZero();
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("Phụ huynh từ chối qua điện thoại ⇒ lưu lần không trả, không cần giờ trả, không báo phụ huynh")
        void phoneDeclinedKeepsChild() {
            givenChildInTeachersClass();
            givenReadyToSave();
            when(childService.findChildBriefs(any())).thenReturn(Map.of(CHILD_ID, brief(CHILD_ID)));
            when(accountService.findFullNames(any())).thenReturn(Map.of());

            PickupRecordResponse result = service.recordResult(
                    teacher,
                    new PickupRequest(
                            CHILD_ID, PickupVerification.PHONE_DECLINED, null, null, "Mẹ báo không quen người đón"));

            assertThat(result.outcome()).isEqualTo(PickupOutcome.NOT_HANDED_OVER);
            assertThat(result.handedOverAt()).isNull();
            assertThat(result.notifiedParents()).isZero();
            verifyNoInteractions(guardianChildLinkService, notificationService);
        }
    }

    @Nested
    @DisplayName("listResults — lịch sử trả trẻ các lớp GV đang phụ trách")
    class ListResults {

        @Test
        @DisplayName("Không phải GV ⇒ PICKUP_ACCESS_DENIED dù có quyền PICKUP_RECORD")
        void nonTeacherDenied() {
            CurrentUser principal = new CurrentUser(
                    9L,
                    "kc-9",
                    "principal",
                    "Thầy B (giả)",
                    null,
                    Set.of(CareNestRole.PRINCIPAL),
                    Set.of(Permission.PICKUP_RECORD));

            assertApiCode(() -> service.listResults(principal, null), ApiCode.PICKUP_ACCESS_DENIED);
            verifyNoInteractions(staffAssignmentService, pickupRepository);
        }

        @Test
        @DisplayName("GV thiếu quyền PICKUP_RECORD ⇒ PICKUP_ACCESS_DENIED")
        void teacherWithoutPermissionDenied() {
            CurrentUser noPermission = new CurrentUser(
                    TEACHER_ID, "kc-7", "teacher", "Cô A (giả)", null, Set.of(CareNestRole.TEACHER), Set.of());

            assertApiCode(() -> service.listResults(noPermission, null), ApiCode.PICKUP_ACCESS_DENIED);
        }

        @Test
        @DisplayName("GV chưa được phân lớp ⇒ danh sách rỗng, không truy vấn bản ghi đón")
        void teacherWithoutClasses() {
            when(rules.today()).thenReturn(TODAY);
            when(staffAssignmentService.findCurrentTeacherClassroomIds(TEACHER_ID, TODAY))
                    .thenReturn(Set.of());

            assertThat(service.listResults(teacher, null)).isEmpty();
            verifyNoInteractions(pickupRepository);
        }

        @Test
        @DisplayName("Chỉ lấy lớp của năm học đang hoạt động; trả tên lớp và tên người ghi")
        void onlyActiveYearClasses() {
            long oldClassId = 11L;
            when(rules.today()).thenReturn(TODAY);
            when(staffAssignmentService.findCurrentTeacherClassroomIds(TEACHER_ID, TODAY))
                    .thenReturn(Set.of(CLASS_ID, oldClassId));
            when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.of(activeClass()));
            when(classroomService.findClassroomInfo(oldClassId))
                    .thenReturn(Optional.of(classOf(oldClassId, SchoolYearStatus.CLOSED)));
            PickupRecord handed = record(CHILD_ID, PickupVerification.PHONE_CONFIRMED, LocalTime.of(15, 30));
            when(pickupRepository.findByClassroomIdInAndDateOrderByRecordedAtDescIdDesc(Set.of(CLASS_ID), TODAY))
                    .thenReturn(List.of(handed));
            when(childService.findChildBriefs(any())).thenReturn(Map.of(CHILD_ID, brief(CHILD_ID)));
            when(accountService.findFullNames(any())).thenReturn(Map.of(TEACHER_ID, "Cô A (giả)"));

            List<PickupRecordResponse> results = service.listResults(teacher, null);

            assertThat(results).hasSize(1);
            PickupRecordResponse first = results.getFirst();
            assertThat(first.className()).isEqualTo("Lớp A");
            assertThat(first.childName()).isEqualTo("Nguyễn Văn 1");
            assertThat(first.recordedByName()).isEqualTo("Cô A (giả)");
        }

        @Test
        @DisplayName("Ngày sau hôm nay ⇒ PICKUP_DATE_INVALID")
        void futureDate() {
            when(rules.today()).thenReturn(TODAY);
            when(staffAssignmentService.findCurrentTeacherClassroomIds(TEACHER_ID, TODAY))
                    .thenReturn(Set.of(CLASS_ID));
            when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.of(activeClass()));

            assertApiCode(() -> service.listResults(teacher, TODAY.plusDays(1)), ApiCode.PICKUP_DATE_INVALID);
        }
    }

    // --- Arrange dùng chung ---

    private void givenTeacherCanRecordClass() {
        when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.of(activeClass()));
        when(rules.canRecord(teacher, activeClass())).thenReturn(true);
        when(rules.today()).thenReturn(TODAY);
    }

    /** Trẻ đang học lớp của cô, năm học đang hoạt động. */
    private void givenChildInTeachersClass() {
        when(rules.today()).thenReturn(TODAY);
        when(enrollmentService.findActiveClassroomIds(CHILD_ID, TODAY)).thenReturn(List.of(CLASS_ID));
        when(classroomService.findClassroomInfo(CLASS_ID)).thenReturn(Optional.of(activeClass()));
        when(rules.canRecord(any(), any())).thenReturn(true);
        when(childIntakeService.findGuardianContacts(CHILD_ID)).thenReturn(List.of());
    }

    private void givenGuardians() {
        when(childIntakeService.findGuardianContacts(CHILD_ID))
                .thenReturn(List.of(
                        new ChildGuardianContact(0, "Mẹ giả", GuardianRelationship.MOTHER, "0900000001", 71L, null),
                        new ChildGuardianContact(1, "Bố giả", GuardianRelationship.FATHER, "0900000002", 72L, null)));
    }

    private void givenPresentToday() {
        when(attendanceRepository.findByChildIdInAndDate(List.of(CHILD_ID), TODAY))
                .thenReturn(List.of(attendance(CHILD_ID, AttendanceStatus.PRESENT)));
    }

    private void givenReadyToSave() {
        givenPresentToday();
        when(pickupRepository.existsByChildIdAndDateAndOutcome(CHILD_ID, TODAY, PickupOutcome.HANDED_OVER))
                .thenReturn(false);
        when(pickupRepository.save(any(PickupRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenNoPickupToday() {
        when(childService.findChildBriefs(List.of(CHILD_ID))).thenReturn(Map.of(CHILD_ID, brief(CHILD_ID)));
        givenPresentToday();
        when(pickupRepository.findByChildIdInAndDateOrderByRecordedAtAscIdAsc(List.of(CHILD_ID), TODAY))
                .thenReturn(List.of());
    }

    private static PickupRequest phoneConfirmed() {
        return new PickupRequest(CHILD_ID, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(15, 30), "Mẹ đón");
    }

    private static ClassroomInfoResponse activeClass() {
        return classOf(CLASS_ID, SchoolYearStatus.ACTIVE);
    }

    private static ClassroomInfoResponse classOf(long id, SchoolYearStatus yearStatus) {
        return new ClassroomInfoResponse(
                id,
                id == CLASS_ID ? "Lớp A" : "Lớp cũ",
                CAMPUS_ID,
                1L,
                yearStatus,
                YEAR_START,
                LocalDate.of(2027, 5, 31),
                1L,
                List.of(),
                null,
                ClassroomStatus.ACTIVE);
    }

    private static ChildBriefResponse brief(long childId) {
        return new ChildBriefResponse(
                childId, "HS" + childId, "Nguyễn Văn " + childId, LocalDate.of(2022, 1, 1), Gender.MALE);
    }

    private static AttendanceRecord attendance(long childId, AttendanceStatus status) {
        return AttendanceRecord.create(
                childId, CLASS_ID, CAMPUS_ID, TODAY, status, List.of(), TEACHER_ID, CLOCK.instant());
    }

    private static PickupRecord record(long childId, PickupVerification verification, LocalTime handoverTime) {
        return PickupRecord.builder()
                .childId(childId)
                .classroomId(CLASS_ID)
                .campusId(CAMPUS_ID)
                .date(TODAY)
                .verification(verification)
                .handedOverAt(
                        handoverTime == null
                                ? null
                                : TODAY.atTime(handoverTime).atZone(ZONE).toInstant())
                .note("Ghi chú giả")
                .recordedBy(TEACHER_ID)
                .recordedAt(CLOCK.instant())
                .build();
    }

    private static GuardianLinkResponse link(long userId) {
        return new GuardianLinkResponse(userId, CHILD_ID, GuardianRelationship.MOTHER, CLOCK.instant());
    }

    private static void assertApiCode(ThrowingCallable call, ApiCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(expected);
    }

    private static void assertFieldErrors(ThrowingCallable call, String... fields) {
        assertThatThrownBy(call).isInstanceOf(GlobalException.class).satisfies(ex -> {
            assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
            assertThat(((GlobalException) ex).getFieldErrors())
                    .extracting(FieldErrorResponse::field)
                    .containsExactly(fields);
        });
    }
}
