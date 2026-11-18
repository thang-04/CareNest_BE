package com.carenest.entity.announcement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tệp đính kèm thông báo của trường (tối đa 5, JPG/PNG/PDF ≤ 10 MB); nội dung nằm ở kho S3 riêng tư. */
@Getter
@Entity
@Table(name = "announcement_attachments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", updatable = false)
    private Long announcementId;

    @Column(name = "schedule_id", updatable = false)
    private Long scheduleId;

    public static AnnouncementAttachment forSchedule(
            Long scheduleId, String key, String name, String type, long size, Instant at) {
        AnnouncementAttachment file = of(null, key, name, type, size, at);
        file.scheduleId = scheduleId;
        return file;
    }

    @Column(name = "file_key", nullable = false, updatable = false)
    private String fileKey;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AnnouncementAttachment of(
            Long announcementId, String fileKey, String fileName, String contentType, long sizeBytes, Instant at) {
        AnnouncementAttachment a = new AnnouncementAttachment();
        a.announcementId = announcementId;
        a.fileKey = fileKey;
        a.fileName = fileName;
        a.contentType = contentType;
        a.sizeBytes = sizeBytes;
        a.createdAt = at;
        return a;
    }
}
