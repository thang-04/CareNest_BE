package com.carenest.entity.meals;

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

/** Một sự kiện của sổ báo ăn (tổng hợp, xác nhận, điều chỉnh, điểm danh đổi); chỉ thêm, không sửa. */
@Getter
@Entity
@Table(name = "meal_count_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealCountHistory {

    public static final String AGGREGATED = "AGGREGATED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String ADJUSTED = "ADJUSTED";
    public static final String ATTENDANCE_CHANGED = "ATTENDANCE_CHANGED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meal_count_id", nullable = false)
    private Long mealCountId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "classroom_id")
    private Long classroomId;

    @Column(name = "delta_normal", nullable = false)
    private int deltaNormal;

    @Column(name = "delta_substitute", nullable = false)
    private int deltaSubstitute;

    @Column(length = 200)
    private String note;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private Instant at;

    public static MealCountHistory of(Long mealCountId, String action, Long userId, Instant at) {
        MealCountHistory history = new MealCountHistory();
        history.mealCountId = mealCountId;
        history.action = action;
        history.userId = userId;
        history.at = at;
        return history;
    }

    public MealCountHistory forClass(Long classroomId) {
        this.classroomId = classroomId;
        return this;
    }

    public MealCountHistory change(int deltaNormal, int deltaSubstitute, String note) {
        this.deltaNormal = deltaNormal;
        this.deltaSubstitute = deltaSubstitute;
        this.note = note;
        return this;
    }

    public boolean isAdjustment() {
        return ADJUSTED.equals(action);
    }
}
