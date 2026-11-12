package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.account.UserSignatureResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.entity.account.UserSignature;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.account.UserSignatureRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FileCategory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserSignatureServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-10T01:00:00Z");
    private static final CurrentUser PHT = TestPermissions.userWithDefaults(7L, CareNestRole.VICE_PRINCIPAL);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Mock
    private UserSignatureRepository repository;

    @Mock
    private FileStorage storage;

    private UserSignatureServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserSignatureServiceImpl(repository, storage, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "ky.png", "image/png", content);
    }

    private static UserSignature signature(long id, long userId, boolean isDefault) {
        UserSignature s =
                UserSignature.of(userId, "00000000-0000-0000-0000-00000000000" + id, "image/png", 10, isDefault, NOW);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private static FileStorage.StoredFile stored(String key) {
        return new FileStorage.StoredFile(key, "image/png", 12);
    }

    private static void assertApiCode(Throwable thrown, ApiCode expected) {
        assertThat(thrown).isInstanceOf(GlobalException.class);
        assertThat(((GlobalException) thrown).getApiCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("SIG-01: ảnh đầu tiên của tài khoản tự thành mặc định, ảnh lưu qua kho tệp")
    void firstSignatureBecomesDefault() {
        when(repository.countByUserId(7L)).thenReturn(0L);
        when(storage.store(eq(FileCategory.USER_SIGNATURE), eq(7L), any(byte[].class)))
                .thenReturn(stored("11111111-1111-1111-1111-111111111111"));
        when(repository.saveAndFlush(any(UserSignature.class))).thenAnswer(call -> call.getArgument(0));

        UserSignatureResponse saved = service.upload(PHT, file(PNG), false);

        assertThat(saved.isDefault()).isTrue();
        assertThat(saved.contentType()).isEqualTo("image/png");
        verify(repository).lockUser(7L);
        verify(repository).clearDefault(7L);
    }

    @Test
    @DisplayName("SIG-01: ảnh sau không đè mặc định, trừ khi đặt mặc định")
    void laterSignatureKeepsDefaultUnlessAsked() {
        when(repository.countByUserId(7L)).thenReturn(2L);
        when(storage.store(eq(FileCategory.USER_SIGNATURE), eq(7L), any(byte[].class)))
                .thenReturn(stored("22222222-2222-2222-2222-222222222222"));
        when(repository.saveAndFlush(any(UserSignature.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.upload(PHT, file(JPG), false).isDefault()).isFalse();
        verify(repository, never()).clearDefault(7L);

        assertThat(service.upload(PHT, file(PNG), true).isDefault()).isTrue();
        verify(repository).clearDefault(7L);
    }

    @Test
    @DisplayName("SIG-01: tệp rỗng hoặc vượt giới hạn của kho bị từ chối trước khi lưu (định dạng kiểm ở FileStorage)")
    void rejectsEmptyAndOversize() {
        assertApiCode(catchThrowable(() -> service.upload(PHT, file(new byte[0]), false)), ApiCode.SIGNATURE_INVALID);
        doThrow(GlobalException.of(ApiCode.FILE_TOO_LARGE, " (tối đa 2 MB)"))
                .when(storage)
                .checkSize(eq(FileCategory.USER_SIGNATURE), isNull(), anyLong());
        assertApiCode(catchThrowable(() -> service.upload(PHT, file(WEBP), false)), ApiCode.FILE_TOO_LARGE);
        verify(storage, never()).store(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("SIG-01: tối đa 5 chữ ký mỗi tài khoản")
    void rejectsSixthSignature() {
        when(repository.countByUserId(7L)).thenReturn(5L);

        assertApiCode(catchThrowable(() -> service.upload(PHT, file(PNG), false)), ApiCode.SIGNATURE_LIMIT_REACHED);
        verify(storage, never()).store(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("SIG-01: lưu DB lỗi thì dọn tệp vừa tải")
    void removesFileWhenSaveFails() {
        when(repository.countByUserId(7L)).thenReturn(1L);
        when(storage.store(eq(FileCategory.USER_SIGNATURE), eq(7L), any(byte[].class)))
                .thenReturn(stored("33333333-3333-3333-3333-333333333333"));
        when(repository.saveAndFlush(any(UserSignature.class))).thenThrow(new IllegalStateException("db"));

        assertThatThrownBy(() -> service.upload(PHT, file(PNG), false)).isInstanceOf(IllegalStateException.class);
        verify(storage).removeQuietly(FileCategory.USER_SIGNATURE, "33333333-3333-3333-3333-333333333333");
    }

    @Test
    @DisplayName("SIG-01: đổi mặc định chỉ cho ảnh của chính mình")
    void setDefaultOnlyForOwnSignature() {
        UserSignature other = signature(2, 7, false);
        when(repository.findByIdAndUserId(2L, 7L)).thenReturn(Optional.of(other));
        when(repository.findByIdAndUserId(9L, 7L)).thenReturn(Optional.empty());

        assertThat(service.setDefault(PHT, 2L).isDefault()).isTrue();
        verify(repository).clearDefault(7L);
        assertApiCode(catchThrowable(() -> service.setDefault(PHT, 9L)), ApiCode.SIGNATURE_NOT_FOUND);
    }

    @Test
    @DisplayName("SIG-01: xóa ảnh mặc định thì ảnh mới nhất còn lại thành mặc định")
    void deletingDefaultPromotesNext() {
        UserSignature current = signature(1, 7, true);
        UserSignature next = signature(2, 7, false);
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(current));
        when(repository.findMine(7L)).thenReturn(List.of(next));

        service.delete(PHT, 1L);

        verify(repository).delete(current);
        assertThat(next.isDefaultSignature()).isTrue();
        // Không có giao dịch thật trong test đơn vị nên tệp được xóa ngay
        verify(storage).removeQuietly(FileCategory.USER_SIGNATURE, current.getStorageKey());
    }

    @Test
    @DisplayName("SIG-01: ảnh của người khác hoặc không tồn tại đều báo không tìm thấy, không trả nội dung")
    void contentOnlyForOwner() {
        UserSignature mine = signature(1, 7, true);
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(mine));
        when(repository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.empty());
        when(storage.loadBytes(FileCategory.USER_SIGNATURE, mine.getStorageKey()))
                .thenReturn(PNG);

        ImageContent image = service.getContent(PHT, 1L);

        assertThat(image.content()).isEqualTo(PNG);
        assertThat(image.contentType()).isEqualTo("image/png");
        assertApiCode(catchThrowable(() -> service.getContent(PHT, 5L)), ApiCode.SIGNATURE_NOT_FOUND);
        verify(storage, never()).loadBytes(any(), eq("99999999-9999-9999-9999-999999999999"));
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException ex) {
            return ex;
        }
    }
}
