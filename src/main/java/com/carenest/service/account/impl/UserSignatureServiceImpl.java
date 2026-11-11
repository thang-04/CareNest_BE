package com.carenest.service.account.impl;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.account.UserSignatureResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.entity.account.UserSignature;
import com.carenest.exception.GlobalException;
import com.carenest.utils.enums.FileCategory;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.account.UserSignatureRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.UserSignatureService;
import com.carenest.utils.ApiCode;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
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
public class UserSignatureServiceImpl implements UserSignatureService {
    private static final String LOG_PREFIX = "[UserSignatureServiceImpl]";
    static final int MAX_SIGNATURES = 5;
    // Ảnh chữ ký nền trong suốt: PNG hoặc JPG

    private final UserSignatureRepository repository;
    private final FileStorage storage;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<UserSignatureResponse> listMine(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listMine|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            List<UserSignatureResponse> response = repository.findMine(currentUser.userId()).stream()
                    .map(UserSignatureServiceImpl::toResponse)
                    .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public UserSignatureResponse upload(CurrentUser currentUser, MultipartFile file, boolean makeDefault) {
        String logPrefix = LOG_PREFIX + "|upload|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            if (file == null || file.isEmpty()) {
                throw GlobalException.of(ApiCode.SIGNATURE_INVALID);
            }
            storage.checkSize(FileCategory.USER_SIGNATURE, null, file.getSize());
            byte[] content = readBytes(file);
            Long userId = currentUser.userId();
            repository.lockUser(userId);
            long count = repository.countByUserId(userId);
            if (count >= MAX_SIGNATURES) {
                throw GlobalException.of(ApiCode.SIGNATURE_LIMIT_REACHED, MAX_SIGNATURES);
            }
            boolean isDefault = makeDefault || count == 0;
            // Loại tệp theo chữ ký đầu tệp (PNG/JPG theo cấu hình), không tin loại khai báo
            FileStorage.StoredFile stored = storage.store(FileCategory.USER_SIGNATURE, userId, content);
            String key = stored.key();
            String contentType = stored.contentType();
            try {
                if (isDefault) {
                    repository.clearDefault(userId);
                }
                UserSignature saved = repository.saveAndFlush(
                        UserSignature.of(userId, key, contentType, content.length, isDefault, clock.instant()));
                log.info("{}|signatureId={}|END", logPrefix, saved.getId());
                return toResponse(saved);
            } catch (RuntimeException ex) {
                storage.removeQuietly(FileCategory.USER_SIGNATURE, key);
                throw ex;
            }
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public UserSignatureResponse setDefault(CurrentUser currentUser, Long signatureId) {
        String logPrefix = LOG_PREFIX + "|setDefault|userId=" + currentUser.userId() + "|signatureId=" + signatureId;
        log.info("{}|START", logPrefix);
        try {
            Long userId = currentUser.userId();
            repository.lockUser(userId);
            UserSignature signature = mine(userId, signatureId);
            if (!signature.isDefaultSignature()) {
                repository.clearDefault(userId);
                // clearDefault làm sạch persistence context: nạp lại trước khi đổi
                signature = mine(userId, signatureId);
                signature.changeDefault(true);
                repository.saveAndFlush(signature);
            }
            log.info("{}|END", logPrefix);
            return toResponse(signature);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void delete(CurrentUser currentUser, Long signatureId) {
        String logPrefix = LOG_PREFIX + "|delete|userId=" + currentUser.userId() + "|signatureId=" + signatureId;
        log.info("{}|START", logPrefix);
        try {
            Long userId = currentUser.userId();
            repository.lockUser(userId);
            UserSignature signature = mine(userId, signatureId);
            boolean wasDefault = signature.isDefaultSignature();
            String key = signature.getStorageKey();
            repository.delete(signature);
            repository.flush();
            if (wasDefault) {
                // Xóa ảnh mặc định: ảnh mới nhất còn lại thành mặc định để luôn có sẵn một ảnh để ký
                repository.findMine(userId).stream().findFirst().ifPresent(next -> {
                    next.changeDefault(true);
                    repository.saveAndFlush(next);
                });
            }
            // Tệp chỉ xóa khi giao dịch đã commit
            afterCommit(() -> storage.removeQuietly(FileCategory.USER_SIGNATURE, key));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent getContent(CurrentUser currentUser, Long signatureId) {
        String logPrefix = LOG_PREFIX + "|getContent|userId=" + currentUser.userId() + "|signatureId=" + signatureId;
        log.info("{}|START", logPrefix);
        try {
            UserSignature signature = mine(currentUser.userId(), signatureId);
            ImageContent image = new ImageContent(
                    storage.loadBytes(FileCategory.USER_SIGNATURE, signature.getStorageKey()), signature.getContentType());
            log.info("{}|END", logPrefix);
            return image;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FileUrlResponse getContentUrl(CurrentUser currentUser, Long signatureId) {
        String logPrefix =
                LOG_PREFIX + "|getContentUrl|userId=" + currentUser.userId() + "|signatureId=" + signatureId;
        log.info("{}|START", logPrefix);
        try {
            UserSignature signature = mine(currentUser.userId(), signatureId);
            var url = storage.presignGet(
                    FileCategory.USER_SIGNATURE, signature.getStorageKey(), signature.getContentType(), null);
            FileUrlResponse result = new FileUrlResponse(url.url(), url.expiresAt());
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ImageContent> findForSigning(CurrentUser currentUser, Long signatureId) {
        String logPrefix = LOG_PREFIX + "|findForSigning|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            UserSignature signature = signatureId == null
                    ? repository.findMine(currentUser.userId()).stream()
                            .findFirst()
                            .orElse(null)
                    : mine(currentUser.userId(), signatureId);
            Optional<ImageContent> image = Optional.ofNullable(signature)
                    .map(s -> new ImageContent(
                            storage.loadBytes(FileCategory.USER_SIGNATURE, s.getStorageKey()), s.getContentType()));
            log.info("{}|found={}|END", logPrefix, image.isPresent());
            return image;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static UserSignatureResponse toResponse(UserSignature signature) {
        return new UserSignatureResponse(
                signature.getId(),
                signature.isDefaultSignature(),
                signature.getContentType(),
                signature.getSizeBytes(),
                signature.getCreatedAt());
    }

    // Ảnh của người khác và ảnh không tồn tại đều báo như nhau
    private UserSignature mine(Long userId, Long signatureId) {
        return repository
                .findByIdAndUserId(signatureId, userId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SIGNATURE_NOT_FOUND));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw GlobalException.withCause(ApiCode.SIGNATURE_INVALID, ex);
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
