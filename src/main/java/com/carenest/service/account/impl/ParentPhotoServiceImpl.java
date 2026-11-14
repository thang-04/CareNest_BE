package com.carenest.service.account.impl;

import com.carenest.dto.account.AccountPhotoResponse;
import com.carenest.dto.child.ChildGuardianContact;
import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.account.ParentPhotoService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FileCategory;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ParentPhotoServiceImpl implements ParentPhotoService {
    private static final String LOG_PREFIX = "[ParentPhotoServiceImpl]";

    private final UserAccountRepository userAccountRepository;
    private final FileStorage fileStorage;
    private final ChildAccessPolicy childAccessPolicy;
    private final ChildIntakeService childIntakeService;
    private final Clock clock;

    @Override
    public AccountPhotoResponse updateMyPhoto(CurrentUser currentUser, MultipartFile file) {
        String logPrefix = LOG_PREFIX + "|updateMyPhoto|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            if (file == null || file.isEmpty()) {
                throw GlobalException.of(ApiCode.ACCOUNT_PHOTO_INVALID);
            }
            // Ảnh chân dung chụp/chọn trên điện thoại, App nén trước khi gửi; giới hạn và định dạng theo cấu hình kho
            fileStorage.checkSize(FileCategory.USER_AVATAR, null, file.getSize());
            byte[] content = readBytes(file);
            UserAccount account = userAccountRepository
                    .findById(currentUser.userId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.ACCOUNT_NOT_FOUND));
            Instant now = clock.instant();
            // Loại tệp theo chữ ký đầu tệp, không tin loại khai báo
            FileStorage.StoredFile stored = fileStorage.store(FileCategory.USER_AVATAR, account.getId(), content);
            String key = stored.key();
            try {
                String old = account.replacePhoto(key, stored.contentType(), content.length, now);
                userAccountRepository.saveAndFlush(account);
                if (old != null) {
                    // Ảnh cũ chỉ xóa khi ảnh mới đã lưu xong
                    afterCommit(() -> fileStorage.removeQuietly(FileCategory.USER_AVATAR, old));
                }
            } catch (RuntimeException ex) {
                fileStorage.removeQuietly(FileCategory.USER_AVATAR, key);
                throw ex;
            }
            log.info("{}|END", logPrefix);
            return new AccountPhotoResponse(true, now, false);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent getMyPhoto(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMyPhoto|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            ImageContent photo = photoOf(currentUser.userId());
            log.info("{}|END", logPrefix);
            return photo;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent getGuardianPhoto(CurrentUser currentUser, Long childId, Long guardianUserId) {
        String logPrefix = LOG_PREFIX + "|getGuardianPhoto|userId=" + currentUser.userId() + "|childId=" + childId
                + "|guardianUserId=" + guardianUserId;
        log.info("{}|START", logPrefix);
        try {
            checkGuardianPhotoAccess(currentUser, childId, guardianUserId);
            ImageContent photo = photoOf(guardianUserId);
            log.info("{}|END", logPrefix);
            return photo;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FileUrlResponse getMyPhotoUrl(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMyPhotoUrl|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            FileUrlResponse url = urlOf(currentUser.userId());
            log.info("{}|END", logPrefix);
            return url;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FileUrlResponse getGuardianPhotoUrl(CurrentUser currentUser, Long childId, Long guardianUserId) {
        String logPrefix = LOG_PREFIX + "|getGuardianPhotoUrl|userId=" + currentUser.userId() + "|childId=" + childId
                + "|guardianUserId=" + guardianUserId;
        log.info("{}|START", logPrefix);
        try {
            checkGuardianPhotoAccess(currentUser, childId, guardianUserId);
            FileUrlResponse url = urlOf(guardianUserId);
            log.info("{}|END", logPrefix);
            return url;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findUserIdsWithPhoto(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return userAccountRepository.findAllById(userIds).stream()
                .filter(UserAccount::hasPhoto)
                .map(UserAccount::getId)
                .collect(Collectors.toSet());
    }

    private void checkGuardianPhotoAccess(CurrentUser currentUser, Long childId, Long guardianUserId) {
        // Nhân sự trong phạm vi lớp trẻ đang học, phụ huynh có liên kết với trẻ; ngoài phạm vi trả 404
        if (!childAccessPolicy.canViewChild(currentUser, childId)) {
            throw GlobalException.of(ApiCode.ACCOUNT_PHOTO_NOT_FOUND);
        }
        // Chỉ phụ huynh có trên hồ sơ của trẻ này
        boolean guardianOfChild = childIntakeService.findGuardianContacts(childId).stream()
                .map(ChildGuardianContact::userId)
                .anyMatch(guardianUserId::equals);
        if (!guardianOfChild) {
            throw GlobalException.of(ApiCode.ACCOUNT_PHOTO_NOT_FOUND);
        }
    }

    private UserAccount photoAccount(Long userId) {
        return userAccountRepository
                .findById(userId)
                .filter(UserAccount::hasPhoto)
                .orElseThrow(() -> GlobalException.of(ApiCode.ACCOUNT_PHOTO_NOT_FOUND));
    }

    private FileUrlResponse urlOf(Long userId) {
        UserAccount account = photoAccount(userId);
        var url = fileStorage.presignGet(
                FileCategory.USER_AVATAR, account.getPhotoKey(), account.getPhotoContentType(), null);
        return new FileUrlResponse(url.url(), url.expiresAt());
    }

    private ImageContent photoOf(Long userId) {
        UserAccount account = photoAccount(userId);
        return new ImageContent(
                fileStorage.loadBytes(FileCategory.USER_AVATAR, account.getPhotoKey()), account.getPhotoContentType());
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw GlobalException.withCause(ApiCode.ACCOUNT_PHOTO_INVALID, ex);
        }
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
