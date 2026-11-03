package com.carenest.entity.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ảnh bữa ăn của một điểm trường–ngày–bữa (NUT-16); đăng lại thì thay tệp của chính dòng này. */
@Getter
@Entity
@Table(name = "meal_photos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "photo_date", nullable = false)
    private LocalDate photoDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealSession session;

    @Column(name = "storage_key", nullable = false, length = 64)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public static MealPhoto of(Long campusId, LocalDate photoDate, MealSession session) {
        MealPhoto photo = new MealPhoto();
        photo.campusId = campusId;
        photo.photoDate = photoDate;
        photo.session = session;
        return photo;
    }

    public void replaceFile(String storageKey, String contentType, long sizeBytes, Long userId, Instant at) {
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = userId;
        this.uploadedAt = at;
    }
}
