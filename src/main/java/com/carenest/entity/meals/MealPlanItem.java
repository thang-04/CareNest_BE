package com.carenest.entity.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một món trong một bữa của một khối, một ngày; định lượng lấy theo nhóm tuổi của món (NUT-18). */
@Getter
@Entity
@Table(name = "meal_plan_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id")
    private MealPlanDay day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_block", nullable = false, length = 20)
    private MenuBlock menuBlock;

    @Column(name = "dish_id", nullable = false)
    private Long dishId;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    public static MealPlanItem of(MealSession session, MenuBlock menuBlock, Long dishId, int sortOrder) {
        MealPlanItem item = new MealPlanItem();
        item.session = session;
        item.menuBlock = menuBlock;
        item.dishId = dishId;
        item.sortOrder = (short) sortOrder;
        return item;
    }

    boolean sameSlot(MealPlanItem other) {
        return session == other.session && menuBlock == other.menuBlock && dishId.equals(other.dishId);
    }

    void moveTo(int sortOrder) {
        this.sortOrder = (short) sortOrder;
    }

    void attachTo(MealPlanDay day) {
        this.day = day;
    }
}
