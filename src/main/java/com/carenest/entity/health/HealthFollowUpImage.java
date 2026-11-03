package com.carenest.entity.health;

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

/** Ảnh kết quả khám chuyên khoa của một sự kiện EXTERNAL_RESULT; tệp ở S3 theo {@code storageKey}. */
@Getter
@Entity
@Table(name = "health_follow_up_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthFollowUpImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(nullable = false, updatable = false)
    private short position;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 36)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static HealthFollowUpImage of(
            Long eventId, int position, String storageKey, String contentType, int sizeBytes, Instant at) {
        HealthFollowUpImage image = new HealthFollowUpImage();
        image.eventId = eventId;
        image.position = (short) position;
        image.storageKey = storageKey;
        image.contentType = contentType;
        image.sizeBytes = sizeBytes;
        image.createdAt = at;
        return image;
    }
}
