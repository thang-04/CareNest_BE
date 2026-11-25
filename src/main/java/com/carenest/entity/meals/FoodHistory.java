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

/** Một trường của thực phẩm đã đổi (hoặc sự kiện tạo, đổi trạng thái); chỉ thêm, không sửa. */
@Getter
@Entity
@Table(name = "food_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodHistory {

    public static final String CREATED = "CREATED";
    public static final String UPDATED = "UPDATED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String PRICE_ADJUSTED = "PRICE_ADJUSTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(length = 60)
    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    public static FoodHistory of(
            Long foodId, Long userId, Instant changedAt, String action, String field, String oldValue, String newValue) {
        FoodHistory history = new FoodHistory();
        history.foodId = foodId;
        history.userId = userId;
        history.changedAt = changedAt;
        history.action = action;
        history.field = field;
        history.oldValue = oldValue;
        history.newValue = newValue;
        return history;
    }
}
