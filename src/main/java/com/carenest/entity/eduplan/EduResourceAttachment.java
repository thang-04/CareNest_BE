package com.carenest.entity.eduplan;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** EDU-81/82: dòng gắn một học liệu vào một mục hoạt động ({@code ngày#mục}) của giáo án ngày; không chứa tệp. */
@Entity
@Table(name = "edu_resource_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduResourceAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long classDocId;

    @Column(nullable = false, length = 180)
    private String activityKey;

    @Column(nullable = false, length = 1000)
    private String activityLabel;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    public static EduResourceAttachment of(Long docId, String key, String label, Long itemId, Long userId, Instant now) {
        EduResourceAttachment a = new EduResourceAttachment();
        a.classDocId = docId;
        a.activityKey = key;
        a.activityLabel = label;
        a.itemId = itemId;
        a.createdBy = userId;
        a.createdAt = now;
        return a;
    }
}
