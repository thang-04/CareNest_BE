package com.carenest.entity.attendance;

import com.carenest.utils.enums.LeavePart;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Đơn nghỉ phụ huynh gửi (ATT-05): không duyệt; hủy từ một ngày thì hiệu lực tới ngày trước đó. */
@Entity
@Table(name = "leave_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * ATT-12: cả ngày hoặc một buổi (đơn 1 ngày); eats: công tắc ăn cho các bữa của buổi còn học, null = không áp dụng
     * (đơn cả ngày, hoặc buổi còn học không có bữa nào).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LeavePart part = LeavePart.FULL_DAY;

    @Column(nullable = false)
    private Boolean eats;

    @Column(name = "cancelled_from")
    private LocalDate cancelledFrom;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public static LeaveRequest create(
            Long childId, LocalDate fromDate, LocalDate toDate, String reason, Long createdBy, Instant at) {
        LeaveRequest r = new LeaveRequest();
        r.childId = childId;
        r.fromDate = fromDate;
        r.toDate = toDate;
        r.reason = reason;
        r.createdBy = createdBy;
        r.createdAt = at;
        return r;
    }

    public LeaveRequest withPart(LeavePart part, Boolean eats) {
        this.part = part == null ? LeavePart.FULL_DAY : part;
        this.eats = eats;
        return this;
    }

    /** Ngày cuối còn hiệu lực; trước from_date nghĩa là đã hủy toàn bộ. */
    public LocalDate effectiveTo() {
        return cancelledFrom == null ? toDate : cancelledFrom.minusDays(1);
    }

    public boolean coversDate(LocalDate date) {
        return !date.isBefore(fromDate) && !date.isAfter(effectiveTo());
    }

    public void cancelFrom(LocalDate from, Long userId, Instant at) {
        cancelledFrom = from;
        cancelledBy = userId;
        cancelledAt = at;
    }
}
