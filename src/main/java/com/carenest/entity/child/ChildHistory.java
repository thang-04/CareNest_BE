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

/**
 * Lịch sử hồ sơ trẻ, chỉ thêm (mục 3.7 của plan tiếp nhận). Cột {@code changes} (JSONB, các ô đổi khi Sửa hồ sơ) chưa
 * map: tiếp nhận và mở lại hồ sơ không ghi.
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

    public static ChildHistory record(Long childId, ChildHistoryAction action, Long userId, String note, Instant at) {
        ChildHistory entry = new ChildHistory();
        entry.childId = childId;
        entry.action = action;
        entry.userId = userId;
        entry.note = note;
        entry.createdAt = at;
        return entry;
    }
}
