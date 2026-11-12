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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final int MAX_PER_SECTION = 20;
    private final EduResourceAttachmentRepository attachments;
    private final EduResourceItemRepository items;
    private final ResourceLibraryService library;
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
            List<EduResourceAttachment> rows = attachments.findAllByClassDocIdOrderByIdAsc(doc.getId());
            Map<Long, EduResourceItem> byId = itemsOf(rows);
            List<LearningResourceResponse> result = rows.stream()
                    .filter(a -> byId.containsKey(a.getItemId()))
                    .map(a -> response(doc, a, byId.get(a.getItemId())))
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
            LearningResourceResponse result = commit(user, docKey, binding, requestId, name, stored, null);
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
            LearningResourceResponse result =
                    commit(user, docKey, binding, requestId, name, stored, request.tags());
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
            LearningResourceResponse replay = replay(user, doc, binding, requestId);
            if (replay != null) return new Prepared(doc.getId(), replay);
            validateBinding(doc, binding);
            return new Prepared(doc.getId(), null);
        });
    }

    private record Prepared(Long docId, LearningResourceResponse replay) {}

    /** Tải lại cùng requestId: trả dòng gắn đã có của học liệu đó ở mục này. */
    private LearningResourceResponse replay(
            CurrentUser user, EduClassDoc doc, LearningResourceBinding binding, UUID requestId) {
        return items.findByOwnerIdAndUploadRequestId(user.userId(), requestId)
                .flatMap(item -> attachments.findAllByClassDocIdAndActivityKey(doc.getId(), binding.activityKey()).stream()
                        .filter(a -> a.getItemId().equals(item.getId()))
                        .findFirst()
                        .map(a -> response(doc, a, item)))
                .orElse(null);
    }

    /** EDU-82: tệp đã lưu ⇒ học liệu "Của tôi" + dòng gắn trong một giao dịch; không thành hoặc trùng thì xóa đúng tệp mới. */
    private LearningResourceResponse commit(
            CurrentUser user,
            String docKey,
            LearningResourceBinding binding,
            UUID requestId,
            String name,
            FileStorage.StoredFile stored,
            ResourceTagsRequest tags) {
        String key = stored.key();
        boolean[] cleanupRegistered = {false};
        UploadCommit outcome;
        try {
            outcome = new TransactionTemplate(transactionManager).execute(status -> {
                EduClassDoc doc = classDocs.requireDayAccess(user, docKey, true);
                LearningResourceResponse again = replay(user, doc, binding, requestId);
                if (again != null) return new UploadCommit(again, false);
                validateBinding(doc, binding);
                requireRoom(doc, binding.activityKey(), 1);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int completionStatus) {
                        if (completionStatus != STATUS_COMMITTED) storage.remove(FileCategory.LEARNING_RESOURCE, key);
                    }
                });
                cleanupRegistered[0] = true;
                EduResourceItem item = library.createFileItem(user, ageGroupOf(doc), stored, name, requestId, tags);
                EduResourceAttachment saved = saveAttachment(user, doc, binding, item.getId());
                workflow.recordAuthor(null, doc.getId(), user.userId(), "CONTENT_WRITE", clock.instant());
                return new UploadCommit(response(doc, saved, item), true);
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
    public LearningResourceResponse addLink(CurrentUser user, String docKey, ResourceLinkRequest request) {
        String p = LOG_PREFIX + "|addLink|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, true);
            LearningResourceBinding binding = request.binding();
            validateBinding(doc, binding);
            EduResourceItem item =
                    library.findOrCreateLink(user, request.url(), request.title(), ageGroupOf(doc), request.tags());
            var result = attachAll(user, doc, binding, List.of(item)).get(0);
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public List<LearningResourceResponse> attach(CurrentUser user, String docKey, ResourceAttachRequest request) {
        String p = LOG_PREFIX + "|attach|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, true);
            LearningResourceBinding binding = request.binding();
            validateBinding(doc, binding);
            List<EduResourceItem> chosen = request.itemIds().stream()
                    .distinct()
                    .map(id -> library.requireVisible(user, id))
                    .toList();
            var result = attachAll(user, doc, binding, chosen);
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** EDU-82: mỗi học liệu một lần mỗi mục, tối đa 20 học liệu mỗi mục; gắn tính là sửa nội dung (EDU-64). */
    private List<LearningResourceResponse> attachAll(
            CurrentUser user, EduClassDoc doc, LearningResourceBinding binding, List<EduResourceItem> chosen) {
        for (EduResourceItem item : chosen)
            if (attachments.existsByClassDocIdAndActivityKeyAndItemId(doc.getId(), binding.activityKey(), item.getId()))
                throw GlobalException.of(ApiCode.RESOURCE_ALREADY_ATTACHED);
        requireRoom(doc, binding.activityKey(), chosen.size());
        List<LearningResourceResponse> out = chosen.stream()
                .map(item -> response(doc, saveAttachment(user, doc, binding, item.getId()), item))
                .toList();
        workflow.recordAuthor(null, doc.getId(), user.userId(), "CONTENT_WRITE", clock.instant());
        return out;
    }

    private EduResourceAttachment saveAttachment(
            CurrentUser user, EduClassDoc doc, LearningResourceBinding binding, Long itemId) {
        try {
            return attachments.saveAndFlush(EduResourceAttachment.of(
                    doc.getId(), binding.activityKey(), binding.activityLabel(), itemId, user.userId(), clock.instant()));
        } catch (DataIntegrityViolationException ex) {
            // Hai người gắn cùng lúc: ràng buộc duy nhất ở DB chặn bản thứ hai
            throw GlobalException.withCause(ApiCode.RESOURCE_ALREADY_ATTACHED, ex);
        }
    }

    private void requireRoom(EduClassDoc doc, String activityKey, int adding) {
        if (attachments.countByClassDocIdAndActivityKey(doc.getId(), activityKey) + adding > MAX_PER_SECTION)
            throw GlobalException.of(ApiCode.RESOURCE_SECTION_FULL, MAX_PER_SECTION);
    }

    @Override
    @Transactional
    public void detach(CurrentUser user, String docKey, Long id) {
        String p = LOG_PREFIX + "|detach|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireDayAccess(user, docKey, true);
            // EDU-85: gỡ khỏi mục chỉ xóa dòng gắn; học liệu trong kho và ở ngày khác còn nguyên
            attachments.delete(requireAttachment(doc, id));
            attachments.flush();
            workflow.recordAuthor(null, doc.getId(), user.userId(), "CONTENT_WRITE", clock.instant());
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
            EduResourceItem item = viewable(user, doc, id);
            if (item.isLink()) throw GlobalException.of(ApiCode.LEARNING_RESOURCE_NOT_FOUND);
            Download result = new Download(
                    storage.load(FileCategory.LEARNING_RESOURCE, item.getStorageKey()),
                    item.getOriginalName(),
                    item.getContentType());
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
            // EDU-86: ai xem được giáo án thì xem được học liệu đã gắn, bất kể phạm vi/ẩn của học liệu
            var result = ResourceLibraryServiceImpl.urlOf(storage, viewable(user, doc, id));
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private EduResourceItem viewable(CurrentUser user, EduClassDoc doc, Long id) {
        EduResourceAttachment a = requireAttachment(doc, id);
        if (!isEditor(user, doc) && !matches(doc, a.getActivityKey()))
            throw GlobalException.of(ApiCode.LEARNING_RESOURCE_UNBOUND);
        return items.findById(a.getItemId()).orElseThrow(() -> GlobalException.of(ApiCode.LEARNING_RESOURCE_NOT_FOUND));
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

    private Long ageGroupOf(EduClassDoc doc) {
        return classrooms.findClassroomInfo(doc.getClassroomId())
                .map(c -> c.ageGroupId())
                .orElse(null);
    }

    private EduResourceAttachment requireAttachment(EduClassDoc doc, Long id) {
        return attachments
                .findByIdAndClassDocId(id, doc.getId())
                .orElseThrow(() -> GlobalException.of(ApiCode.LEARNING_RESOURCE_NOT_FOUND));
    }

    private Map<Long, EduResourceItem> itemsOf(List<EduResourceAttachment> rows) {
        if (rows.isEmpty()) return Map.of();
        return items.findAllById(rows.stream().map(EduResourceAttachment::getItemId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(EduResourceItem::getId, Function.identity()));
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

    private LearningResourceResponse response(EduClassDoc doc, EduResourceAttachment a, EduResourceItem item) {
        return new LearningResourceResponse(
                a.getId(),
                doc.getDocKey(),
                a.getActivityKey(),
                a.getActivityLabel(),
                item.getTitle(),
                item.isLink() ? "text/uri-list" : item.getContentType(),
                item.getByteSize() == null ? 0 : item.getByteSize(),
                a.getCreatedBy(),
                a.getCreatedAt(),
                matches(doc, a.getActivityKey()),
                item.getId(),
                item.getKind(),
                item.getTitle(),
                item.getUrl(),
                item.getScope());
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
