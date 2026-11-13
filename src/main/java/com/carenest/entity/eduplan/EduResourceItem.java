package com.carenest.entity.eduplan;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** EDU-82/84: học liệu trong kho cá nhân của người tạo (tệp S3 hoặc link), lưu một lần, gắn vào giáo án ngày qua {@link EduResourceAttachment}. */
@Entity
@Table(name = "edu_resource_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduResourceItem {
    public static final String FILE = "FILE";
    public static final String LINK = "LINK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String kind;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private Long ownerId;

    private Long ageGroupId;

    @Column(length = 255)
    private String originalName;

    @Column(length = 120)
    private String contentType;

    private Long byteSize;

    @Column(length = 255)
    private String storageKey;

    private UUID uploadRequestId;

    @Column(length = 2000)
    private String url;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static EduResourceItem file(
            Long ownerId,
            String title,
            Long ageGroupId,
            String name,
            String type,
            long size,
            String key,
            UUID requestId,
            Instant now) {
        EduResourceItem i = base(FILE, ownerId, title, ageGroupId, now);
        i.originalName = name;
        i.contentType = type;
        i.byteSize = size;
        i.storageKey = key;
        i.uploadRequestId = requestId;
        return i;
    }

    public static EduResourceItem link(Long ownerId, String title, Long ageGroupId, String url, Instant now) {
        EduResourceItem i = base(LINK, ownerId, title, ageGroupId, now);
        i.url = url;
        return i;
    }

    private static EduResourceItem base(String kind, Long ownerId, String title, Long ageGroupId, Instant now) {
        EduResourceItem i = new EduResourceItem();
        i.kind = kind;
        i.ownerId = ownerId;
        i.title = title;
        i.ageGroupId = ageGroupId;
        i.createdAt = now;
        i.updatedAt = now;
        return i;
    }

    public boolean isLink() {
        return LINK.equals(kind);
    }

    public void rename(String title, Instant now) {
        this.title = title;
        this.updatedAt = now;
    }

    public void changeAgeGroup(Long ageGroupId, Instant now) {
        this.ageGroupId = ageGroupId;
        this.updatedAt = now;
    }
}
