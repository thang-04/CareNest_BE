package com.carenest.service.eduplan.impl;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.PresignedUploadResponse;
import com.carenest.dto.eduplan.*;
import com.carenest.entity.eduplan.*;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.integration.storage.FileTypes;
import com.carenest.repository.eduplan.*;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.*;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.LearningResourcePolicy;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FileCategory;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningResourceServiceImpl implements LearningResourceService {
    private static final String LOG_PREFIX = "[LearningResourceServiceImpl]";
    private final EduClassDocService classDocs;
    private final EduLearningResourceRepository resources;
    private final StaffAssignmentService assignments;
    private final FileStorage storage;
    private final Clock clock;
    private final EduWorkflowService workflow;
    private final com.carenest.service.organization.ClassroomService classrooms;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Override
    @Transactional(readOnly = true)
    public List<LearningResourceResponse> list(CurrentUser user, String docKey) {
        String p = LOG_PREFIX + "|list|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, false);
            boolean editor = isEditor(user, doc);
            List<LearningResourceResponse> result = resources.findAllByClassDocIdOrderByIdAsc(doc.getId()).stream()
                    .map(r -> response(doc, r))
                    .filter(r -> editor || r.active())
                    .toList();
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public LearningResourceResponse upload(
            CurrentUser user, String docKey, MultipartFile file, LearningResourceBinding binding, UUID requestId) {
        String p = LOG_PREFIX + "|upload|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            if (requestId == null) throw GlobalException.of(ApiCode.UPLOAD_REQUEST_ID_REQUIRED);
            Prepared prepared = prepare(user, docKey, binding, requestId);
            if (prepared.replay() != null) {
                log.info("{}|END", p);
                return prepared.replay();
            }
            String name = LearningResourcePolicy.safeName(file.getOriginalFilename());
            if (isOffice(name)) validateOffice(file::getInputStream, name);
            FileStorage.StoredFile stored;
            try (var input = file.getInputStream()) {
                stored = storage.store(FileCategory.LEARNING_RESOURCE, prepared.docId(), name, input, file.getSize());
            } catch (IOException ex) {
                throw GlobalException.of(ApiCode.FILE_UNREADABLE);
            }
            LearningResourceResponse result = commit(user, docKey, binding, requestId, name, stored);
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedUploadResponse uploadUrl(
            CurrentUser user, String docKey, LearningResourceUploadUrlRequest request) {
        String p = LOG_PREFIX + "|uploadUrl|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, true);
            String name = LearningResourcePolicy.safeName(request.fileName());
            var upload = storage.presignPut(
                    FileCategory.LEARNING_RESOURCE, doc.getId(), name, request.contentType(), request.size());
            log.info("{}|END", p);
            return new PresignedUploadResponse(
                    upload.uploadId(), upload.url(), "PUT", upload.headers(), upload.expiresAt());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public LearningResourceResponse confirmUpload(
            CurrentUser user, String docKey, LearningResourceConfirmRequest request, UUID requestId) {
        String p = LOG_PREFIX + "|confirmUpload|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            if (requestId == null) throw GlobalException.of(ApiCode.UPLOAD_REQUEST_ID_REQUIRED);
            LearningResourceBinding binding = request.binding();
            Prepared prepared = prepare(user, docKey, binding, requestId);
            if (prepared.replay() != null) {
                log.info("{}|END", p);
                return prepared.replay();
            }
            String name = LearningResourcePolicy.safeName(request.fileName());
            // Đuôi tên gốc phải khớp tệp đã tải (đuôi nằm trong uploadId đã ký) để tên hiển thị không đánh lừa loại tệp
            if (!Objects.equals(FileTypes.extension(name), FileTypes.extension(request.uploadId())))
                throw GlobalException.of(ApiCode.FILE_NAME_INVALID);
            FileStorage.StoredFile stored =
                    storage.confirm(FileCategory.LEARNING_RESOURCE, prepared.docId(), request.uploadId());
            if (isOffice(name)) {
                try {
                    validateOffice(
                            () -> storage.load(FileCategory.LEARNING_RESOURCE, stored.key())
                                    .getInputStream(),
                            name);
                } catch (RuntimeException ex) {
                    storage.removeQuietly(FileCategory.LEARNING_RESOURCE, stored.key());
                    throw ex;
                }
            }
            LearningResourceResponse result = commit(user, docKey, binding, requestId, name, stored);
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Kiểm quyền + hoạt động trước khi đụng kho; cùng requestId đã ghi thì trả lại kết quả cũ (idempotent). */
    private Prepared prepare(CurrentUser user, String docKey, LearningResourceBinding binding, UUID requestId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, true);
            var existing = resources.findByClassDocIdAndUploadRequestId(doc.getId(), requestId);
            if (existing.isPresent()) return new Prepared(doc.getId(), response(doc, existing.get()));
            validateBinding(doc, binding);
            return new Prepared(doc.getId(), null);
        });
    }

    private record Prepared(Long docId, LearningResourceResponse replay) {}

    /** Ghi bản ghi học liệu cho tệp đã lưu; giao dịch không thành hoặc trùng requestId thì xóa đúng tệp mới. */
    private LearningResourceResponse commit(
            CurrentUser user,
            String docKey,
            LearningResourceBinding binding,
            UUID requestId,
            String name,
            FileStorage.StoredFile stored) {
        String key = stored.key();
        boolean[] cleanupRegistered = {false};
        UploadCommit outcome;
        try {
            outcome = new TransactionTemplate(transactionManager).execute(status -> {
                EduClassDoc doc = classDocs.requireDayAccess(user, docKey, true);
                var existing = resources.findByClassDocIdAndUploadRequestId(doc.getId(), requestId);
                if (existing.isPresent()) return new UploadCommit(response(doc, existing.get()), false);
                validateBinding(doc, binding);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int completionStatus) {
                        if (completionStatus != STATUS_COMMITTED) storage.remove(FileCategory.LEARNING_RESOURCE, key);
                    }
                });
                cleanupRegistered[0] = true;
                EduLearningResource saved = resources.saveAndFlush(EduLearningResource.create(
                        doc.getId(),
                        binding.activityKey(),
                        binding.activityLabel(),
                        name,
                        stored.contentType(),
                        stored.size(),
                        key,
                        requestId,
                        user.userId(),
                        clock.instant()));
                workflow.recordAuthor(null, doc.getId(), user.userId(), "CONTENT_WRITE", clock.instant());
                return new UploadCommit(response(doc, saved), true);
            });
        } catch (RuntimeException ex) {
            if (!cleanupRegistered[0]) {
                try {
                    storage.remove(FileCategory.LEARNING_RESOURCE, key);
                } catch (RuntimeException cleanupFailure) {
                    ex.addSuppressed(cleanupFailure);
                }
            }
            throw ex;
        }
        if (!outcome.usedStoredFile()) storage.remove(FileCategory.LEARNING_RESOURCE, key);
        return outcome.response();
    }

    private record UploadCommit(LearningResourceResponse response, boolean usedStoredFile) {}

    @Override
    @Transactional
    public LearningResourceResponse bind(CurrentUser user, String docKey, Long id, LearningResourceBinding binding) {
        String p = LOG_PREFIX + "|bind|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, true);
            validateBinding(doc, binding);
            EduLearningResource r = requireResource(doc, id);
            boolean changed = !Objects.equals(r.getActivityKey(), binding.activityKey())
                    || !Objects.equals(r.getActivityLabel(), binding.activityLabel());
            r.bind(binding.activityKey(), binding.activityLabel());
            resources.saveAndFlush(r);
            if (changed) workflow.recordAuthor(null, doc.getId(), user.userId(), "CONTENT_WRITE", clock.instant());
            log.info("{}|END", p);
            return response(doc, r);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void detach(CurrentUser user, String docKey, Long id) {
        String p = LOG_PREFIX + "|detach|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, true);
            EduLearningResource r = requireResource(doc, id);
            boolean changed = r.getActivityKey() != null;
            r.detach();
            resources.saveAndFlush(r);
            if (changed) workflow.recordAuthor(null, doc.getId(), user.userId(), "CONTENT_WRITE", clock.instant());
            log.info("{}|END", p);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Download content(CurrentUser user, String docKey, Long id) {
        String p = LOG_PREFIX + "|content|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, false);
            EduLearningResource r = requireResource(doc, id);
            if (!isEditor(user, doc) && !active(doc, r)) throw GlobalException.of(ApiCode.LEARNING_RESOURCE_UNBOUND);
            Download result = new Download(
                    storage.load(FileCategory.LEARNING_RESOURCE, r.getStorageKey()),
                    r.getOriginalName(),
                    r.getContentType());
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FileUrlResponse contentUrl(CurrentUser user, String docKey, Long id) {
        String p = LOG_PREFIX + "|contentUrl|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, false);
            EduLearningResource r = requireResource(doc, id);
            if (!isEditor(user, doc) && !active(doc, r)) throw GlobalException.of(ApiCode.LEARNING_RESOURCE_UNBOUND);
            var url = storage.presignGet(
                    FileCategory.LEARNING_RESOURCE, r.getStorageKey(), r.getContentType(), r.getOriginalName());
            log.info("{}|END", p);
            return new FileUrlResponse(url.url(), url.expiresAt());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private boolean isEditor(CurrentUser user, EduClassDoc doc) {
        var classroom = classrooms.findClassroomInfo(doc.getClassroomId()).orElseThrow();
        return doc.getStatus().editable()
                && user.hasRole(CareNestRole.TEACHER)
                && Objects.equals(
                        assignments
                                .findHomeroomTeacherIds(List.of(classroom.id()), LocalDate.now(clock))
                                .get(classroom.id()),
                        user.userId());
    }

    private EduLearningResource requireResource(EduClassDoc doc, Long id) {
        return resources
                .findByIdAndClassDocId(id, doc.getId())
                .orElseThrow(() -> GlobalException.of(ApiCode.LEARNING_RESOURCE_NOT_FOUND));
    }

    private static final java.util.regex.Pattern KEY =
            java.util.regex.Pattern.compile(LearningResourceBinding.KEY_PATTERN);

    /** EDU-81: mục hoạt động phải là một trong 4 mục cố định của một ngày (thứ 2–6) thuộc tuần của kế hoạch ngày. */
    private void validateBinding(EduClassDoc doc, LearningResourceBinding b) {
        if (b == null
                || b.activityLabel() == null
                || b.activityLabel().isBlank()
                || b.activityLabel().length() > 1000
                || !matches(doc, b.activityKey())) throw GlobalException.of(ApiCode.ACTIVITY_CHANGED);
    }

    private static boolean matches(EduClassDoc doc, String key) {
        if (key == null || !KEY.matcher(key).matches() || doc.getWeekStart() == null) return false;
        try {
            LocalDate date = LocalDate.parse(key.substring(0, key.indexOf('#')));
            return !date.isBefore(doc.getWeekStart())
                    && !date.isAfter(doc.getWeekStart().plusDays(4));
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static boolean active(EduClassDoc doc, EduLearningResource r) {
        return matches(doc, r.getActivityKey());
    }

    private LearningResourceResponse response(EduClassDoc doc, EduLearningResource r) {
        return new LearningResourceResponse(
                r.getId(),
                doc.getDocKey(),
                r.getActivityKey(),
                r.getActivityLabel(),
                r.getOriginalName(),
                r.getContentType(),
                r.getByteSize(),
                r.getUploadedBy(),
                r.getCreatedAt(),
                active(doc, r));
    }

    private interface ContentSource {
        java.io.InputStream open() throws IOException;
    }

    private static boolean isOffice(String name) {
        return name.matches("(?i).*\\.(pptx|docx|xlsx)$");
    }

    private void validateOffice(ContentSource source, String name) {
        String folder = name.toLowerCase(java.util.Locale.ROOT).endsWith("pptx")
                ? "ppt/"
                : name.toLowerCase(java.util.Locale.ROOT).endsWith("docx") ? "word/" : "xl/";
        boolean types = false, body = false;
        int entries = 0;
        long expanded = 0;
        try (var zip = new java.util.zip.ZipInputStream(source.open())) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10000) throw new IOException("Too many entries");
                types |= entry.getName().equals("[Content_Types].xml");
                body |= entry.getName().startsWith(folder);
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    expanded += n;
                    if (expanded > 100L * 1024 * 1024) throw new IOException("Archive too large");
                }
            }
        } catch (IOException ex) {
            throw GlobalException.of(ApiCode.FILE_CONTENT_MISMATCH);
        }
        if (!types || !body) throw GlobalException.of(ApiCode.FILE_CONTENT_MISMATCH);
    }
}
