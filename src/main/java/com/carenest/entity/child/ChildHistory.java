package com.carenest.entity.child;

import com.carenest.utils.enums.ChildHistoryAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lịch sử hồ sơ trẻ, chỉ thêm (mục 3.7 của plan tiếp nhận). Cột {@code changes} chỉ ghi ở Sửa hồ sơ; tiếp nhận, nghỉ
 * học, mở lại hồ sơ để null.
 */
@Getter
@Entity
@Table(name = "child_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ChildHistoryAction action;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(updatable = false, length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Sửa hồ sơ: JSON [{ field, label, from, to }]; số định danh của trẻ, CCCD phụ huynh chỉ ghi 3 số cuối
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private String changes;

    public static ChildHistory record(Long childId, ChildHistoryAction action, Long userId, String note, Instant at) {
        return record(childId, action, userId, note, null, at);
    }

    public static ChildHistory record(
            Long childId, ChildHistoryAction action, Long userId, String note, String changesJson, Instant at) {
        ChildHistory entry = new ChildHistory();
        entry.childId = childId;
        entry.action = action;
        entry.userId = userId;
        entry.note = note;
        entry.changes = changesJson;
        entry.createdAt = at;
        return entry;
    }
}
