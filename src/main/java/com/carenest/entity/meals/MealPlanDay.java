package com.carenest.entity.meals;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một ngày của kế hoạch tuần; {@code off} = không phục vụ bữa, không kiểm cân đối, không trừ kho. */
@Getter
@Entity
@Table(name = "meal_plan_days")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPlanDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_id")
    private MealPlanWeek week;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Column(nullable = false)
    private boolean off;

    @Column(nullable = false, length = 300)
    private String note = "";

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<MealPlanItem> items = new ArrayList<>();

    public static MealPlanDay of(LocalDate menuDate, boolean off, String note, List<MealPlanItem> items) {
        MealPlanDay day = new MealPlanDay();
        day.menuDate = menuDate;
        day.off = off;
        day.note = note == null ? "" : note;
        items.forEach(item -> {
            item.attachTo(day);
            day.items.add(item);
        });
        return day;
    }

    /** Giữ món trùng (bữa, khối, món), bỏ món không còn, thêm món mới, xếp lại thứ tự. */
    void copyFrom(MealPlanDay incoming) {
        this.off = incoming.off;
        this.note = incoming.note;
        List<MealPlanItem> wanted = incoming.items;
        items.removeIf(existing -> wanted.stream().noneMatch(w -> w.sameSlot(existing)));
        for (int i = 0; i < wanted.size(); i++) {
            MealPlanItem w = wanted.get(i);
            Optional<MealPlanItem> keep = items.stream().filter(w::sameSlot).findFirst();
            if (keep.isPresent()) {
                keep.get().moveTo(i);
            } else {
                w.attachTo(this);
                w.moveTo(i);
                items.add(w);
            }
        }
    }

    void attachTo(MealPlanWeek week) {
        this.week = week;
    }
}
