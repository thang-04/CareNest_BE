package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.account.AccountPhotoResponse;
import com.carenest.dto.account.AccountPhotoUrlResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.AccountPhotoAccessPolicy;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FileCategory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountPhotoServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-14T03:00:00Z");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final CurrentUser TEACHER = TestPermissions.userWithDefaults(30L, CareNestRole.TEACHER);

    @Mock
    private UserAccountRepository accounts;

    @Mock
    private FileStorage storage;

    @Mock
    private ChildAccessPolicy childAccessPolicy;

    @Mock
    private AccountPhotoAccessPolicy photoAccessPolicy;

    @Mock
    private ChildIntakeService childIntakeService;

    private AccountPhotoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountPhotoServiceImpl(
                accounts,
                storage,
                childAccessPolicy,
                photoAccessPolicy,
                childIntakeService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserAccount account(long id, CareNestRole role, String photoKey) {
        UserAccount account = UserAccount.create("kc-" + id, "user" + id, null, "Người " + id, role);
        ReflectionTestUtils.setField(account, "id", id);
        if (photoKey != null) {
            account.replacePhoto(photoKey, "image/png", 12, NOW);
        }
        return account;
    }

    private static void assertApiCode(Throwable thrown, ApiCode expected) {
        assertThat(thrown).isInstanceOf(GlobalException.class);
        assertThat(((GlobalException) thrown).getApiCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("AUTH-27: GV tải ảnh đại diện vào kho USER_AVATAR, ảnh cũ bị xóa")
    void teacherUploadsPhotoReplacingOld() {
        UserAccount gv = account(30L, CareNestRole.TEACHER, "avatars/users/old.png");
        given(accounts.findById(30L)).willReturn(Optional.of(gv));
        given(storage.store(FileCategory.USER_AVATAR, 30L, PNG))
                .willReturn(new FileStorage.StoredFile("avatars/users/user_30_new.png", "image/png", PNG.length));

        AccountPhotoResponse result =
                service.updateMyPhoto(TEACHER, new MockMultipartFile("file", "a.png", "image/png", PNG));

        assertThat(result.photoUploaded()).isTrue();
        assertThat(gv.getPhotoKey()).isEqualTo("avatars/users/user_30_new.png");
        verify(storage).checkSize(FileCategory.USER_AVATAR, null, PNG.length);
        verify(storage).removeQuietly(FileCategory.USER_AVATAR, "avatars/users/old.png");
    }

    @Test
    @DisplayName("AUTH-27: tệp quá cỡ bị chặn trước khi lưu")
    void oversizedPhotoRejectedBeforeStore() {
        MockMultipartFile big = new MockMultipartFile("file", "a.png", "image/png", PNG);
        GlobalException tooLarge = GlobalException.of(ApiCode.FILE_TOO_LARGE, " (tối đa 2 MB)");
        org.mockito.Mockito.doThrow(tooLarge).when(storage).checkSize(any(), any(), eq((long) PNG.length));

        assertApiCode(catchThrowable(() -> service.updateMyPhoto(TEACHER, big)), ApiCode.FILE_TOO_LARGE);
        verify(storage, never()).store(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("AUTH-27: nhân sự xóa ảnh, tệp bị dọn; xóa lại vẫn thành công")
    void staffDeletesPhotoIdempotently() {
        UserAccount gv = account(30L, CareNestRole.TEACHER, "avatars/users/user_30.png");
        given(accounts.findById(30L)).willReturn(Optional.of(gv));

        AccountPhotoResponse first = service.deleteMyPhoto(TEACHER);
        AccountPhotoResponse second = service.deleteMyPhoto(TEACHER);

        assertThat(first.photoUploaded()).isFalse();
        assertThat(second.photoUploaded()).isFalse();
        assertThat(gv.hasPhoto()).isFalse();
        verify(storage).removeQuietly(FileCategory.USER_AVATAR, "avatars/users/user_30.png");
        verify(accounts).saveAndFlush(gv);
    }

    @Test
    @DisplayName("AUTH-27: tài khoản có role phụ huynh (kể cả GV kiêm phụ huynh) không xóa được ảnh")
    void parentCannotDeletePhoto() {
        CurrentUser parent = TestPermissions.userWithDefaults(70L, CareNestRole.PARENT);
        CurrentUser teacherParent = TestPermissions.userWithDefaults(31L, CareNestRole.TEACHER, CareNestRole.PARENT);

        assertApiCode(catchThrowable(() -> service.deleteMyPhoto(parent)), ApiCode.ACCOUNT_PHOTO_REQUIRED);
        assertApiCode(catchThrowable(() -> service.deleteMyPhoto(teacherParent)), ApiCode.ACCOUNT_PHOTO_REQUIRED);
        verifyNoInteractions(accounts, storage);
    }

    @Test
    @DisplayName("AUTH-27: link chỉ trả người được xem, có ảnh và là nhân sự (trừ ảnh của chính mình)")
    void photoUrlsFilteredByAccessPhotoAndStaff() {
        CurrentUser parentViewer = TestPermissions.userWithDefaults(70L, CareNestRole.PARENT);
        given(photoAccessPolicy.visibleUserIds(parentViewer, List.of(30L, 31L, 32L, 70L, 71L)))
                .willReturn(Set.of(30L, 32L, 70L, 71L));
        UserAccount gvWithPhoto = account(30L, CareNestRole.TEACHER, "avatars/users/a.png");
        UserAccount gvNoPhoto = account(32L, CareNestRole.TEACHER, null);
        UserAccount self = account(70L, CareNestRole.PARENT, "avatars/users/self.png");
        UserAccount otherParent = account(71L, CareNestRole.PARENT, "avatars/users/p.png");
        given(accounts.findAllById(Set.of(30L, 32L, 70L, 71L)))
                .willReturn(List.of(gvWithPhoto, gvNoPhoto, self, otherParent));
        given(storage.presignGet(eq(FileCategory.USER_AVATAR), any(), any(), any()))
                .willReturn(new FileStorage.FileUrl("https://s3/x", NOW.plusSeconds(900)));

        List<AccountPhotoUrlResponse> urls = service.findPhotoUrls(parentViewer, List.of(30L, 31L, 32L, 70L, 71L));

        assertThat(urls).extracting(AccountPhotoUrlResponse::userId).containsExactlyInAnyOrder(30L, 70L);
        assertThat(urls).allSatisfy(u -> assertThat(u.expiresAt()).isEqualTo(NOW.plusSeconds(900)));
    }

    @Test
    @DisplayName("AUTH-27: không ai được xem thì không đọc DB")
    void nobodyVisibleSkipsLookup() {
        given(photoAccessPolicy.visibleUserIds(TEACHER, List.of(99L))).willReturn(Set.of());

        assertThat(service.findPhotoUrls(TEACHER, List.of(99L))).isEmpty();
        verifyNoInteractions(accounts, storage);
    }
}
