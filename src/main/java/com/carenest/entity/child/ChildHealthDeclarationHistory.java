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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lịch sử khai báo sức khỏe, chỉ thêm; {@code allergy_change}, {@code history_change} (JSONB trước/sau) ghi khi phần đó
 * đổi (sửa khai báo, phiếu khám); khai báo lần đầu khi tiếp nhận không có.
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

    // JSON { from, to } khi dị ứng / tiền sử đổi (sửa khai báo, phiếu khám); null khi phần đó không đổi
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergy_change", updatable = false, columnDefinition = "jsonb")
    private String allergyChange;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "history_change", updatable = false, columnDefinition = "jsonb")
    private String historyChange;

    public static ChildHealthDeclarationHistory record(
            Long childId, HealthDeclarationAction action, Long userId, String note, Instant at) {
        return record(childId, action, userId, note, null, null, at);
    }

    public static ChildHealthDeclarationHistory record(
            Long childId,
            HealthDeclarationAction action,
            Long userId,
            String note,
            String allergyChangeJson,
            String historyChangeJson,
            Instant at) {
        ChildHealthDeclarationHistory entry = new ChildHealthDeclarationHistory();
        entry.childId = childId;
        entry.action = action;
        entry.userId = userId;
        entry.note = note;
        entry.allergyChange = allergyChangeJson;
        entry.historyChange = historyChangeJson;
        entry.createdAt = at;
        return entry;
    }
}
