package com.carenest.service.announcement.impl;

import com.carenest.entity.announcement.AnnouncementAttachment;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.announcement.AnnouncementAttachmentRepository;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FileCategory;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

final class AnnouncementFiles {
    private AnnouncementFiles() {}

    static void store(
            AnnouncementAttachmentRepository repository,
            FileStorage storage,
            Long parentId,
            boolean schedule,
            List<MultipartFile> files,
            Instant now,
            List<String> storedKeys) {
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null
                    ? "tep"
                    : file.getOriginalFilename().replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).trim();
            if (name.isEmpty()) name = "tep";
            if (name.length() > 200) name = name.substring(0, 200);
            FileStorage.StoredFile stored;
            try (InputStream input = file.getInputStream()) {
                stored = storage.store(FileCategory.ANNOUNCEMENT, parentId, name, input, file.getSize());
            } catch (IOException ex) {
                throw GlobalException.withCause(ApiCode.FILE_UNREADABLE, ex);
            }
            storedKeys.add(stored.key());
            repository.save(
                    schedule
                            ? AnnouncementAttachment.forSchedule(
                                    parentId, stored.key(), name, stored.contentType(), stored.size(), now)
                            : AnnouncementAttachment.of(
                                    parentId, stored.key(), name, stored.contentType(), stored.size(), now));
        }
    }

    static void cleanupAfterCommit(
            AnnouncementAttachmentRepository repository, FileStorage storage, List<String> keys) {
        // Lịch và các tin đã gửi có thể dùng cùng file_key; chỉ dọn sau commit và khi không còn metadata tham chiếu.
        Runnable cleanup = () -> keys.stream()
                .distinct()
                .filter(key -> !repository.existsByFileKey(key))
                .forEach(key -> storage.removeQuietly(FileCategory.ANNOUNCEMENT, key));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else cleanup.run();
    }
}
