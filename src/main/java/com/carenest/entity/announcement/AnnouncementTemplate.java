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

/** Mẫu soạn sẵn của BGH; {@code campusId} null = mẫu toàn trường (FR-017). */
@Getter
@Entity
@Table(name = "announcement_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "campus_id")
    private Long campusId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AnnouncementTemplate create(
            String name, String title, String body, Long campusId, Long createdBy, Instant at) {
        AnnouncementTemplate t = new AnnouncementTemplate();
        t.createdBy = createdBy;
        t.createdAt = at;
        t.edit(name, title, body, campusId, at);
        return t;
    }

    public void edit(String name, String title, String body, Long campusId, Instant at) {
        this.name = name;
        this.title = title;
        this.body = body;
        this.campusId = campusId;
        this.updatedAt = at;
    }
}
