package com.carenest.entity.meals;

import com.carenest.utils.enums.MealPlanStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

    // Ngày nghỉ, ghi chú của từng ngày (jsonb) thay cho bảng ngày riêng; món của ngày ở items theo menuDate
    @Convert(converter = JsonListConverter.PlanDays.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "days", nullable = false, columnDefinition = "jsonb")
    private List<MealPlanDay.Info> dayInfos = List.of();

    @OneToMany(mappedBy = "week", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("menuDate, sortOrder")
    private List<MealPlanItem> items = new ArrayList<>();

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

    /** Các ngày đã lưu của tuần, theo ngày tăng dần, kèm món của từng ngày. */
    public List<MealPlanDay> getDays() {
        return dayInfos.stream()
                .sorted(Comparator.comparing(MealPlanDay.Info::date))
                .map(this::toDay)
                .toList();
    }

    public Optional<MealPlanDay> day(LocalDate date) {
        return dayInfos.stream()
                .filter(info -> info.date().equals(date))
                .findFirst()
                .map(this::toDay);
    }

    /** Ghi lưới tuần nháp: mỗi ngày gửi lên thay ghi chú/ngày nghỉ và danh sách món của ngày đó. */
    public void changeDays(List<MealPlanDay> newDays, Long updatedBy) {
        this.updatedBy = updatedBy;
        Map<LocalDate, MealPlanDay.Info> infoByDate = new TreeMap<>();
        dayInfos.forEach(info -> infoByDate.put(info.date(), info));
        for (MealPlanDay day : newDays) {
            infoByDate.put(day.getMenuDate(), new MealPlanDay.Info(day.getMenuDate(), day.isOff(), day.getNote()));
            replaceItems(day.getMenuDate(), day.getItems());
        }
        this.dayInfos = List.copyOf(infoByDate.values());
    }

    /**
     * Món của một ngày: giữ dòng trùng (bữa, khối, món) và chỉ đổi thứ tự, bỏ món không còn, thêm món mới. Không xóa hết
     * rồi chèn lại vì Hibernate chèn trước khi xóa ⇒ trùng khóa duy nhất.
     */
    private void replaceItems(LocalDate date, List<MealPlanItem> wanted) {
        items.removeIf(old -> old.getMenuDate().equals(date) && wanted.stream().noneMatch(w -> w.sameSlot(old)));
        for (int order = 0; order < wanted.size(); order++) {
            MealPlanItem item = existingItem(date, wanted.get(order)).orElse(wanted.get(order));
            if (item.getWeek() == null) {
                item.attachTo(this, date);
                items.add(item);
            }
            item.moveTo(order);
        }
    }

    private Optional<MealPlanItem> existingItem(LocalDate date, MealPlanItem wanted) {
        return items.stream()
                .filter(old -> old.getMenuDate().equals(date) && wanted.sameSlot(old))
                .findFirst();
    }

    private MealPlanDay toDay(MealPlanDay.Info info) {
        List<MealPlanItem> dayItems = items.stream()
                .filter(item -> item.getMenuDate().equals(info.date()))
                .sorted(Comparator.comparingInt(MealPlanItem::getSortOrder))
                .toList();
        return MealPlanDay.of(info.date(), info.off(), info.note(), dayItems);
    }

    public void publish(Long userId, Instant at) {
        status = MealPlanStatus.PUBLISHED;
        publishedBy = userId;
        publishedAt = at;
        updatedBy = userId;
    }
}
