package com.carenest.entity.child;

import com.carenest.utils.enums.HealthDeclarationAction;
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
 * Lịch sử khai báo sức khỏe, chỉ thêm. Cột {@code allergy_change}, {@code history_change} (JSONB trước/sau) chưa map:
 * khai báo lần đầu khi tiếp nhận không có thay đổi.
 */
@Getter
@Entity
@Table(name = "child_health_declaration_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildHealthDeclarationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private HealthDeclarationAction action;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(updatable = false, length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ChildHealthDeclarationHistory record(
            Long childId, HealthDeclarationAction action, Long userId, String note, Instant at) {
        ChildHealthDeclarationHistory entry = new ChildHealthDeclarationHistory();
        entry.childId = childId;
        entry.action = action;
        entry.userId = userId;
        entry.note = note;
        entry.createdAt = at;
        return entry;
    }
}
