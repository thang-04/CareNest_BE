package com.carenest.entity.health;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Một lần sửa số đo (HLT-13): ai sửa, lúc nào, lý do, các ô đổi (JSON {@code [{field, from, to}]}); chỉ thêm. */
@Getter
@Entity
@Table(name = "health_measurement_edits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthMeasurementEdit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measurement_id", nullable = false)
    private Long measurementId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 300)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String changes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static HealthMeasurementEdit of(Long measurementId, Long userId, String reason, String changes, Instant at) {
        HealthMeasurementEdit edit = new HealthMeasurementEdit();
        edit.measurementId = measurementId;
        edit.userId = userId;
        edit.reason = reason;
        edit.changes = changes;
        edit.createdAt = at;
        return edit;
    }
}
