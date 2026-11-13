package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.EnrollmentPhotos;
import com.carenest.dto.common.ImageContent;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FileCategory;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianRelationship;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ChildPhotoServiceImplTest {
    @Mock
    private ChildRepository children;

    @Mock
    private ChildGuardianRepository guardians;

    @Mock
    private FileStorage storage;

    @Mock
    private ChildIntakeService intake;

    @Mock
    private ChildAccessPolicy access;

    @Mock
    private StaffAccessPolicy staff;

    @Mock
    private com.carenest.security.authorization.CampusAccessPolicy campus;

    @Mock
    private ChildViewResponse view;

    private ChildPhotoServiceImpl service;
    private static final Instant NOW = Instant.parse("2026-11-05T03:00:00Z");
    private static final CurrentUser TEACHER = TestPermissions.userWithDefaults(10L, CareNestRole.TEACHER);

    @BeforeEach
    void setup() {
        service = new ChildPhotoServiceImpl(
                children, guardians, storage, intake, access, campus, staff, Clock.fixed(NOW, ZoneOffset.UTC));
        // Giới hạn ảnh trẻ lấy từ cấu hình kho (mặc định 2 MB)
        lenient().when(storage.maxBytes(FileCategory.CHILD_AVATAR, null)).thenReturn(2L * 1024 * 1024);
        lenient().when(storage.maxSizeText(FileCategory.CHILD_AVATAR, null)).thenReturn("2 MB");
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("CHD-02: ảnh không bắt buộc, nhận nội dung PNG dù MIME/tên client sai")
    void validatesActualContentAndAllowsNoImages() throws Exception {
        assertThat(service.validatePhotos(1, Map.of()).guardians()).isEmpty();
        MultipartFile image = image("childPhoto");
        var photos = service.validatePhotos(2, Map.of("childPhoto", List.of(image), "guardianPhoto1", List.of(image)));
        assertThat(photos.child().contentType()).isEqualTo("image/png");
        assertThat(photos.guardians()).containsOnlyKeys(1);
        verifyNoInteractions(children, guardians);
        verify(storage, never()).store(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("CHD-02: từ chối rỗng, giả ảnh, quá 2 MiB, ảnh bị cắt trước mọi ghi")
    void rejectsInvalidFilesBeforeStorage() throws Exception {
        for (byte[] content : List.of(new byte[0], "not an image".getBytes(), new byte[2097153], new byte[] {
            (byte) 137, 80, 78, 71, 13, 10, 26, 10
        })) {
            MultipartFile file = new MockMultipartFile("childPhoto", "photo.png", "image/png", content);
            assertThatThrownBy(() -> service.validatePhotos(1, Map.of("childPhoto", List.of(file))))
                    .isInstanceOf(GlobalException.class);
        }
        verifyNoInteractions(children, guardians);
        verify(storage, never()).store(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("CHD-02: part lặp, part sai và ảnh của phụ huynh không tồn tại bị từ chối")
    void rejectsAmbiguousPhotoBinding() throws Exception {
        MultipartFile image = image("childPhoto");
        for (Map<String, List<MultipartFile>> files : List.of(
                Map.of("childPhoto", List.of(image, image)),
                Map.of("guardianPhoto2", List.of(image)),
                Map.of("guardianPhoto3", List.of(image)),
                Map.of("unknown", List.of(image)),
                Map.of("payload", List.of(image, image)))) {
            assertThatThrownBy(() -> service.validatePhotos(1, files)).isInstanceOf(GlobalException.class);
        }
        verifyNoInteractions(children, guardians);
        verify(storage, never()).store(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("CHD-02: ảnh gắn đúng vị trí trong cùng request và dọn tất cả object mới khi rollback")
    void attachesCorrectGuardianAndCleansRollback() throws Exception {
        Child child = Child.enroll(
                "TEST", "Trẻ giả", LocalDate.of(2023, 1, 1), Gender.MALE, false, 1L, LocalDate.of(2026, 11, 5));
        ChildGuardian first = guardian(10L, 1L, 0), second = guardian(11L, 1L, 1);
        when(children.findById(1L)).thenReturn(Optional.of(child));
        when(guardians.findAllByChildIdOrderByPositionAsc(1L)).thenReturn(List.of(first, second));
        when(storage.store(eq(FileCategory.CHILD_AVATAR), eq(1L), any(byte[].class)))
                .thenReturn(
                        new FileStorage.StoredFile("child-key", "image/png", 3),
                        new FileStorage.StoredFile("guardian-key", "image/png", 3));
        ImageContent image = new ImageContent(image("childPhoto").getBytes(), "image/png");
        TransactionSynchronizationManager.initSynchronization();
        service.attachToEnrollment(1L, new EnrollmentPhotos(image, Map.of(1, image)));
        assertThat(child.getPhotoKey()).isEqualTo("child-key");
        assertThat(first.getPhotoKey()).isNull();
        assertThat(second.getPhotoKey()).isEqualTo("guardian-key");
        assertThat(second.getPhotoUpdatedAt()).isEqualTo(NOW);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).removeQuietly(FileCategory.CHILD_AVATAR, "child-key");
        verify(storage).removeQuietly(FileCategory.CHILD_AVATAR, "guardian-key");
    }

    @Test
    @DisplayName("AUTH-04: ngoài phạm vi không đọc kho hoặc dò id người giám hộ")
    void rejectsOutOfScopeBeforeLoading() {
        assertThatThrownBy(() -> service.getGuardianPhoto(TEACHER, 1L, 11L)).isInstanceOf(GlobalException.class);
        verifyNoInteractions(intake, children, guardians, storage);
    }

    @Test
    @DisplayName("AUTH-04: người giám hộ phải thuộc đúng trẻ, không chỉ đúng id")
    void rejectsGuardianOfAnotherChild() {
        when(access.canReadChild(TEACHER, 1L)).thenReturn(true);
        when(intake.getChildView(1L)).thenReturn(view);
        when(guardians.findById(11L)).thenReturn(Optional.of(guardian(11L, 2L, 0)));
        assertThatThrownBy(() -> service.getGuardianPhoto(TEACHER, 1L, 11L)).isInstanceOf(GlobalException.class);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("CHD-02: PHT được đọc ảnh trẻ chờ xếp lớp trong campus phụ trách")
    void readsUnplacedChildInManagedCampus() {
        CurrentUser vp = TestPermissions.userWithDefaults(3L, CareNestRole.VICE_PRINCIPAL);
        when(campus.canReadAllCampuses(vp)).thenReturn(true);
        when(intake.getChildView(1L)).thenReturn(view);
        ChildGuardian guardian = guardian(11L, 1L, 0);
        guardian.assignEnrollmentPhoto("photo-key", "image/png", 3, NOW);
        when(guardians.findById(11L)).thenReturn(Optional.of(guardian));
        when(storage.loadBytes(FileCategory.CHILD_AVATAR, "photo-key")).thenReturn(new byte[] {1, 2, 3});
        assertThat(service.getGuardianPhoto(vp, 1L, 11L).content()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("CHD-02: giữ ảnh của trẻ khi mở lại hồ sơ, không thay ảnh tài khoản")
    void preservesPortraitOnReadmission() {
        Child child = Child.enroll(
                "TEST", "Trẻ giả", LocalDate.of(2023, 1, 1), Gender.MALE, false, 7L, LocalDate.of(2026, 11, 5));
        child.assignEnrollmentPhoto("existing", "image/png", 10, NOW);
        child.readmit(8L, LocalDate.of(2026, 11, 6));
        assertThat(child.getPhotoKey()).isEqualTo("existing");
        assertThat(child.getPhotoUpdatedAt()).isEqualTo(NOW);
    }

    static MockMultipartFile image(String name) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile(name, "portrait.txt", "application/octet-stream", out.toByteArray());
    }

    @Test
    @DisplayName("CHD-02: WEBP chỉ có header VP8X không có frame bị từ chối")
    void rejectsWebpWithoutAnImageFrame() {
        byte[] bytes = new byte[30];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy("WEBPVP8X".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 8, 8);
        bytes[4] = 22;
        bytes[16] = 10;
        var file = new MockMultipartFile("childPhoto", "header.webp", "image/webp", bytes);
        assertThatThrownBy(() -> service.validatePhotos(1, Map.of("childPhoto", List.of(file))))
                .isInstanceOf(GlobalException.class);
        verify(storage, never()).store(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("AUTH-20: giáo viên xem hồ sơ có ảnh nhưng không nhận thông tin quản trị tài khoản/SMS")
    void teacherProfileHidesAccountAdministration() {
        when(access.canReadChild(TEACHER, 1L)).thenReturn(true);
        when(intake.getChildView(1L)).thenReturn(profileWithAccountDetails());
        var result = service.getProfile(TEACHER, 1L);
        assertThat(result.guardians().getFirst().accountStatus()).isNull();
        assertThat(result.guardians().getFirst().smsStatus()).isNull();
        assertThat(result.guardians().getFirst().smsAt()).isNull();
        assertThat(result.guardians().getFirst().photoUrl()).isEqualTo("/children/1/guardian-photos/11");
    }

    @Test
    @DisplayName("AUTH-20: PHT đọc trẻ chờ xếp lớp khác campus nhưng không xem quản trị tài khoản campus đó")
    void crossCampusProfileRetainsPhotoWithoutAccountDetails() {
        CurrentUser vp = TestPermissions.userWithDefaults(3L, CareNestRole.VICE_PRINCIPAL);
        when(campus.canReadAllCampuses(vp)).thenReturn(true);
        when(staff.managedCampusIds(vp)).thenReturn(Set.of(9L));
        when(intake.getChildView(1L)).thenReturn(profileWithAccountDetails());
        var result = service.getProfile(vp, 1L);
        assertThat(result.guardians().getFirst().accountStatus()).isNull();
        assertThat(result.photoUrl()).isEqualTo("/children/1/photo");
    }

    private static ChildViewResponse profileWithAccountDetails() {
        var guardian = new com.carenest.dto.child.ChildGuardianResponse(
                "Phụ huynh giả",
                GuardianRelationship.MOTHER,
                "0900000000",
                "999000000001",
                null,
                com.carenest.utils.enums.GuardianAccountStatus.ACTIVE,
                com.carenest.utils.enums.SmsStatus.SENT,
                NOW,
                "/children/1/guardian-photos/11");
        return new ChildViewResponse(
                1L,
                "TEST",
                "Trẻ giả",
                LocalDate.of(2023, 1, 1),
                Gender.MALE,
                com.carenest.utils.enums.ChildStatus.PENDING_PLACEMENT,
                7L,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 11, 5),
                null,
                null,
                null,
                List.of(),
                false,
                null,
                List.of(guardian),
                List.of(),
                "/children/1/photo");
    }

    private static ChildGuardian guardian(Long id, Long childId, int position) {
        ChildGuardian g = ChildGuardian.create(
                childId, position, "Phụ huynh giả", GuardianRelationship.MOTHER, "0900000000", "999000000001", null);
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }
}
