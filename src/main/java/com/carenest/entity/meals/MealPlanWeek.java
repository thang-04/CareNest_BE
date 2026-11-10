package com.carenest.entity.meals;

import com.carenest.utils.enums.MealPlanStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Kế hoạch thực đơn một tuần (thứ 2–6) cho cả trường, theo khối (NUT-08, NUT-13). */
@Getter
@Entity
@Table(name = "meal_plan_weeks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPlanWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MealPlanStatus status = MealPlanStatus.DRAFT;

    @OneToMany(mappedBy = "week", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("menuDate")
    private List<MealPlanDay> days = new ArrayList<>();

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static MealPlanWeek draft(LocalDate weekStart, Long createdBy) {
        MealPlanWeek week = new MealPlanWeek();
        week.weekStart = weekStart;
        week.createdBy = createdBy;
        week.updatedBy = createdBy;
        return week;
    }

    public boolean isPublished() {
        return status == MealPlanStatus.PUBLISHED;
    }

    public Optional<MealPlanDay> day(LocalDate date) {
        return days.stream().filter(d -> d.getMenuDate().equals(date)).findFirst();
    }

    /**
     * Ghi lưới tuần nháp: ngày đã có thì sửa tại chỗ, chưa có thì thêm. Không xóa rồi chèn lại cùng khóa (tuần, ngày)
     * trong một lần flush vì Hibernate chèn trước khi xóa.
     */
    public void changeDays(List<MealPlanDay> newDays, Long updatedBy) {
        this.updatedBy = updatedBy;
        for (MealPlanDay incoming : newDays) {
            Optional<MealPlanDay> existing = day(incoming.getMenuDate());
            if (existing.isPresent()) {
                existing.get().copyFrom(incoming);
            } else {
                incoming.attachTo(this);
                days.add(incoming);
            }
        }
    }

    public void publish(Long userId, Instant at) {
        status = MealPlanStatus.PUBLISHED;
        publishedBy = userId;
        publishedAt = at;
        updatedBy = userId;
    }
}
