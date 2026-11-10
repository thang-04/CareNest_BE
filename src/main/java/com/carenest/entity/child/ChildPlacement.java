package com.carenest.entity.child;

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

/**
 * Một lần xếp / chuyển lớp của trẻ, chỉ thêm (V86): tiếp nhận có lớp ({@code fromClassroomId} null) và xếp / chuyển lớp
 * (CHD-10, G94-180/CHD-02). Lớp tại một ngày vẫn đọc từ {@link Enrollment}; bảng này chỉ để hiện lịch sử kèm lý do.
 */
@Getter
@Entity
@Table(name = "child_placements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildPlacement {

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

    public static ChildPlacement record(
            Long childId, Long fromClassroomId, Long toClassroomId, Long placedBy, String reason, Instant at) {
        ChildPlacement placement = new ChildPlacement();
        placement.childId = childId;
        placement.fromClassroomId = fromClassroomId;
        placement.toClassroomId = toClassroomId;
        placement.placedBy = placedBy;
        placement.reason = reason;
        placement.placedAt = at;
        return placement;
    }
}
