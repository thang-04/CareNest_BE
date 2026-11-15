package com.carenest.entity.meals;

import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

/**
 * Một ngày của kế hoạch tuần; {@code off} = không phục vụ bữa, không kiểm cân đối, không trừ kho. Không phải bảng
 * riêng: ngày nghỉ/ghi chú lưu ở {@code meal_plan_weeks.days}, món lưu ở {@code meal_plan_items} theo
 * {@code menu_date}; {@link MealPlanWeek#day(LocalDate)} ghép lại thành đối tượng này.
 */
@Getter
public class MealPlanDay {

    private final LocalDate menuDate;
    private final boolean off;
    private final String note;
    private final List<MealPlanItem> items;

    private MealPlanDay(LocalDate menuDate, boolean off, String note, List<MealPlanItem> items) {
        this.menuDate = menuDate;
        this.off = off;
        this.note = note == null ? "" : note;
        this.items = List.copyOf(items);
    }

    public static MealPlanDay of(LocalDate menuDate, boolean off, String note, List<MealPlanItem> items) {
        return new MealPlanDay(menuDate, off, note, items);
    }

    /** Phần ngày lưu trong cột jsonb của tuần (không gồm món). */
    public record Info(LocalDate date, boolean off, String note) {}
}
