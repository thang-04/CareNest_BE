package com.carenest.entity.meals;

import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionTab;
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

/**
 * Yêu cầu / quyền mở ghi biên bản kiểm thực của một mẫu–ngày–điểm trường đã qua (INS-05). Người gửi và người quyết định
 * lưu kèm tên để lịch sử đọc được dù tài khoản đổi tên.
 */
@Getter
@Entity
@Table(name = "inspection_day_grants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionDayGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "sheet_date", nullable = false)
    private LocalDate sheetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InspectionTab tab;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InspectionGrantStatus status;

    // HT tự mở không qua duyệt (INS-05)
    @Column(name = "self_opened", nullable = false)
    private boolean selfOpened;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_by_name", nullable = false)
    private String requestedByName;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_by_name")
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    /** PHT gửi yêu cầu, chờ HT. */
    public static InspectionDayGrant request(
            Long campusId,
            LocalDate sheetDate,
            InspectionTab tab,
            String reason,
            Long userId,
            String userName,
            Instant at) {
        InspectionDayGrant grant = new InspectionDayGrant();
        grant.campusId = campusId;
        grant.sheetDate = sheetDate;
        grant.tab = tab;
        grant.reason = reason;
        grant.requestedBy = userId;
        grant.requestedByName = userName;
        grant.requestedAt = at;
        grant.status = InspectionGrantStatus.PENDING;
        return grant;
    }

    /** HT tự mở: mở ngay, người gửi cũng là người quyết định. */
    public static InspectionDayGrant selfOpen(
            Long campusId,
            LocalDate sheetDate,
            InspectionTab tab,
            String reason,
            Long userId,
            String userName,
            Instant at,
            Instant expiresAt) {
        InspectionDayGrant grant = request(campusId, sheetDate, tab, reason, userId, userName, at);
        grant.selfOpened = true;
        grant.approve(userId, userName, null, at, expiresAt);
        return grant;
    }

    public void approve(Long userId, String userName, String note, Instant at, Instant expiresAt) {
        this.status = InspectionGrantStatus.APPROVED;
        decide(userId, userName, note, at);
        this.expiresAt = expiresAt;
    }

    public void reject(Long userId, String userName, String note, Instant at) {
        this.status = InspectionGrantStatus.REJECTED;
        decide(userId, userName, note, at);
    }

    public void revoke(Long userId, String userName, String note, Instant at) {
        this.status = InspectionGrantStatus.REVOKED;
        decide(userId, userName, note, at);
    }

    /** Ký thành công: đóng yêu cầu, lần mở lại sau cần yêu cầu mới. */
    public void consume(Instant at) {
        this.status = InspectionGrantStatus.CONSUMED;
        this.consumedAt = at;
    }

    public void expire() {
        this.status = InspectionGrantStatus.EXPIRED;
    }

    /** Đang mở và còn hạn tại {@code now}. */
    public boolean isUsable(Instant now) {
        return status == InspectionGrantStatus.APPROVED && expiresAt != null && expiresAt.isAfter(now);
    }

    private void decide(Long userId, String userName, String note, Instant at) {
        this.decidedBy = userId;
        this.decidedByName = userName;
        this.decisionNote = note;
        this.decidedAt = at;
    }
}
