package com.carenest.entity.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Khung giờ đăng ảnh của một bữa, chung toàn trường (NUT-16). */
@Getter
@Entity
@Table(name = "meal_photo_windows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPhotoWindow {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MealSession session;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static MealPhotoWindow of(MealSession session, LocalTime start, LocalTime end, Long userId, Instant at) {
        MealPhotoWindow window = new MealPhotoWindow();
        window.session = session;
        window.change(start, end, userId, at);
        return window;
    }

    public void change(LocalTime start, LocalTime end, Long userId, Instant at) {
        this.startTime = start;
        this.endTime = end;
        this.updatedBy = userId;
        this.updatedAt = at;
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(startTime) && !time.isAfter(endTime);
    }
}
