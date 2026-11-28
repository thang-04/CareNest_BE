package com.carenest.entity.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một lần sửa ngoại lệ một ngày của thực đơn tuần đã công bố (NUT-08, P-39); chỉ thêm, không sửa, không xóa. */
@Getter
@Entity
@Table(name = "meal_plan_day_revisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPlanDayRevision {

    /** Món lúc sửa; giữ tên để lịch sử đọc được cả khi món đổi tên sau này. */
    public record Item(MealSession session, MenuBlock block, Long dishId, String dishName) {}

    /** Cảnh báo kiểm cân đối của ngày lúc sửa (không chặn lưu). */
    public record Issue(String severity, String code, String message) {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_id", nullable = false)
    private Long weekId;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Column(name = "before_note", nullable = false, length = 300)
    private String beforeNote;

    @Convert(converter = JsonListConverter.RevisionItems.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_items", nullable = false, columnDefinition = "jsonb")
    private List<Item> beforeItems;

    @Column(name = "after_note", nullable = false, length = 300)
    private String afterNote;

    @Convert(converter = JsonListConverter.RevisionItems.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_items", nullable = false, columnDefinition = "jsonb")
    private List<Item> afterItems;

    @Column(nullable = false, length = 500)
    private String reason;

    @Convert(converter = JsonListConverter.RevisionIssues.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Issue> issues;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public static MealPlanDayRevision of(
            Long weekId,
            LocalDate menuDate,
            String beforeNote,
            List<Item> beforeItems,
            String afterNote,
            List<Item> afterItems,
            String reason,
            List<Issue> issues,
            Long changedBy,
            Instant changedAt) {
        MealPlanDayRevision revision = new MealPlanDayRevision();
        revision.weekId = weekId;
        revision.menuDate = menuDate;
        revision.beforeNote = beforeNote == null ? "" : beforeNote;
        revision.beforeItems = List.copyOf(beforeItems);
        revision.afterNote = afterNote == null ? "" : afterNote;
        revision.afterItems = List.copyOf(afterItems);
        revision.reason = reason;
        revision.issues = List.copyOf(issues);
        revision.changedBy = changedBy;
        revision.changedAt = changedAt;
        return revision;
    }
}
