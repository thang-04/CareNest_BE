package com.carenest.entity.eduplan;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "edu_learning_resources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduLearningResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long classDocId;

    @Column(length = 180)
    private String activityKey;

    @Column(length = 64)
    private String activitySignature;

    @Column(nullable = false, length = 1000)
    private String activityLabel;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private long byteSize;

    @Column(nullable = false, length = 100)
    private String storageKey;

    @Column(nullable = false)
    private UUID uploadRequestId;

    @Column(nullable = false)
    private Long uploadedBy;

    @Column(nullable = false)
    private Instant createdAt;

    public static EduLearningResource create(
            Long docId,
            String key,
            String signature,
            String label,
            String name,
            String type,
            long size,
            String storageKey,
            UUID requestId,
            Long userId,
            Instant now) {
        EduLearningResource r = new EduLearningResource();
        r.classDocId = docId;
        r.bind(key, signature, label);
        r.originalName = name;
        r.contentType = type;
        r.byteSize = size;
        r.storageKey = storageKey;
        r.uploadRequestId = requestId;
        r.uploadedBy = userId;
        r.createdAt = now;
        return r;
    }

    public void bind(String key, String signature, String label) {
        activityKey = key;
        activitySignature = signature;
        activityLabel = label;
    }

    public void detach() {
        activityKey = null;
        activitySignature = null;
    }
}
