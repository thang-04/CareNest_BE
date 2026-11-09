package com.carenest.entity.meals;

import com.carenest.utils.enums.FoodSampleDisposalStatus;
import com.carenest.utils.enums.FoodSampleStatus;
import com.carenest.utils.enums.MealSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Bản lưu mẫu thức ăn của một điểm trường–ngày–bữa (NUT-21). Bếp ghi và sửa khi chưa duyệt; hủy mẫu là bước sau của
 * cùng bản ghi, chỉ ghi được khi đã qua {@link #DISPOSAL_AFTER} kể từ lúc lấy mẫu.
 */
@Getter
@Entity
@Table(name = "food_samples")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodSample {

    public static final Duration DISPOSAL_AFTER = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "sample_date", nullable = false)
    private LocalDate sampleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealSession session;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FoodSampleStatus status = FoodSampleStatus.RECORDED;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "disposed_at")
    private Instant disposedAt;

    @Column(name = "disposal_recorded_by")
    private Long disposalRecordedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposal_status", nullable = false, length = 10)
    private FoodSampleDisposalStatus disposalStatus = FoodSampleDisposalStatus.NONE;

    @Column(name = "disposal_approved_by")
    private Long disposalApprovedBy;

    @Column(name = "disposal_approved_at")
    private Instant disposalApprovedAt;

    @Column(nullable = false, length = 500)
    private String note = "";

    // Món đã lưu mẫu (cột bigint[]) thay cho bảng nối riêng; service kiểm món tồn tại trước khi ghi
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dish_ids", nullable = false, columnDefinition = "bigint[]")
    private List<Long> dishIds = List.of();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FoodSample record(
            Long campusId,
            LocalDate sampleDate,
            MealSession session,
            Instant sampledAt,
            Long userId,
            String note,
            Collection<Long> dishIds) {
        FoodSample sample = new FoodSample();
        sample.campusId = campusId;
        sample.recordedBy = userId;
        sample.change(sampleDate, session, sampledAt, note, dishIds);
        return sample;
    }

    /** Sửa được khi chưa duyệt và chưa ghi hủy (hủy tính từ lúc lấy mẫu nên không đổi giờ lấy mẫu sau đó). */
    public boolean isEditable() {
        return status == FoodSampleStatus.RECORDED && disposalStatus == FoodSampleDisposalStatus.NONE;
    }

    public void change(
            LocalDate sampleDate, MealSession session, Instant sampledAt, String note, Collection<Long> dishIds) {
        this.sampleDate = sampleDate;
        this.session = session;
        this.sampledAt = sampledAt;
        this.note = note;
        this.dishIds = dishIds.stream().distinct().sorted().toList();
    }

    public Instant disposableFrom() {
        return sampledAt.plus(DISPOSAL_AFTER);
    }

    public void approve(Long userId, Instant at) {
        status = FoodSampleStatus.APPROVED;
        approvedBy = userId;
        approvedAt = at;
    }

    public void recordDisposal(Long userId, Instant at) {
        disposalStatus = FoodSampleDisposalStatus.RECORDED;
        disposalRecordedBy = userId;
        disposedAt = at;
    }

    public void approveDisposal(Long userId, Instant at) {
        disposalStatus = FoodSampleDisposalStatus.APPROVED;
        disposalApprovedBy = userId;
        disposalApprovedAt = at;
    }
}
