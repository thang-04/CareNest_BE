package com.carenest.entity.meals;

import com.carenest.utils.enums.MealSession;
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

/** Một món trong một bữa của một ngày (tuần 1–4, thứ 2–6) thuộc bộ thực đơn mẫu. */
@Getter
@Entity
@Table(name = "menu_template_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id")
    private MenuTemplateSet set;

    @Column(name = "week_no", nullable = false)
    private short weekNo;

    @Column(nullable = false)
    private short weekday;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealSession session;

    @Column(name = "dish_id", nullable = false)
    private Long dishId;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    public static MenuTemplateItem of(int weekNo, int weekday, MealSession session, Long dishId, int sortOrder) {
        MenuTemplateItem item = new MenuTemplateItem();
        item.weekNo = (short) weekNo;
        item.weekday = (short) weekday;
        item.session = session;
        item.dishId = dishId;
        item.sortOrder = (short) sortOrder;
        return item;
    }

    void attachTo(MenuTemplateSet set) {
        this.set = set;
    }
}
