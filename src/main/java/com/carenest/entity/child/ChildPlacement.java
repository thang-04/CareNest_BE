package com.carenest.entity.child;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một lần xếp / chuyển lớp của trẻ, chỉ thêm (V89): tiếp nhận có lớp ({@code fromClassroomId} null) và xếp / chuyển lớp
 * (CHD-10, G94-180/CHD-02). Lớp tại một ngày vẫn đọc từ {@link Enrollment}; bảng này chỉ để hiện lịch sử kèm lý do.
 * G94-180/CHD-14 (V186): ngày vào lớp mới có thể ở tương lai; lịch chưa tới ngày bị hủy / bị thay thì ghi
 * {@code cancelledAt}, dòng vẫn giữ.
 */
@Getter
@Entity
@Table(name = "child_placements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildPlacement {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    @Column(name = "from_classroom_id", updatable = false)
    private Long fromClassroomId;

    @Column(name = "to_classroom_id", nullable = false, updatable = false)
    private Long toClassroomId;

    @Column(name = "placed_by", nullable = false, updatable = false)
    private Long placedBy;

    @Column(updatable = false, length = 300)
    private String reason;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "effective_from", updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    public static ChildPlacement record(
            Long childId,
            Long fromClassroomId,
            Long toClassroomId,
            Long placedBy,
            String reason,
            Instant at,
            LocalDate effectiveFrom) {
        ChildPlacement placement = new ChildPlacement();
        placement.childId = childId;
        placement.fromClassroomId = fromClassroomId;
        placement.toClassroomId = toClassroomId;
        placement.placedBy = placedBy;
        placement.reason = reason;
        placement.placedAt = at;
        placement.effectiveFrom = effectiveFrom;
        return placement;
    }

    /** Ngày vào lớp mới; dòng cũ / seed không có thì là ngày lưu (giờ Việt Nam). */
    public LocalDate effectiveDate() {
        return effectiveFrom != null ? effectiveFrom : placedAt.atZone(VIETNAM).toLocalDate();
    }

    /** Lịch chưa tới ngày bị hủy hoặc bị thay bằng lịch khác (G94-180/CHD-14). */
    public void cancel(Long by, Instant at) {
        this.cancelledBy = by;
        this.cancelledAt = at;
    }
}
