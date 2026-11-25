package com.carenest.service.health;

import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Số đo của trẻ trong kỳ cân đo (HLT-09, HLT-13): GV phụ trách lớp nhập, sửa có lý do; BGH, GV xem sổ sức khỏe. */
public interface HealthMeasurementService {

    ChildHealthRecordResponse getHealthRecord(CurrentUser currentUser, Long childId);

    /**
     * Phần phụ huynh xem (PAR-01, PAR-03): kết quả đã công bố, biểu đồ tăng trưởng, giải thích đã duyệt. Phụ huynh có
     * liên kết với trẻ chưa nghỉ; nhân sự trong phạm vi xem trước được. {@code schoolYearId} null ⇒ mọi năm; có ⇒ chỉ
     * kết quả trong năm trẻ có học (không học ⇒ 2001).
     */
    com.carenest.dto.health.PublishedHealthResponse getPublishedHealth(
            CurrentUser currentUser, Long childId, Long schoolYearId);

    /**
     * Phiếu khám đã kết luận của trẻ cho phụ huynh (HLT-11, PAR-03): {@code resultId} là id kết quả khám trong
     * {@link #getPublishedHealth}; phiếu nháp, số đo kỳ cân đo, kết quả của trẻ khác ⇒ HEALTH_EXAM_NOT_FOUND.
     */
    com.carenest.dto.health.ParentHealthExamResponse getPublishedExam(
            CurrentUser currentUser, Long childId, Long resultId);

    HealthMeasurementResponse createMeasurement(
            CurrentUser currentUser, Long childId, HealthMeasurementRequest request);

    HealthMeasurementResponse updateMeasurement(
            CurrentUser currentUser, Long childId, Long measurementId, HealthMeasurementRequest request);

    /** Số đo cho module khác đọc (không kiểm quyền, nơi gọi tự kiểm). */
    record LatestMeasurement(LocalDate date, BigDecimal weightKg, BigDecimal heightCm) {}

    /** Lần cân đo gần nhất có cân nặng hoặc chiều cao, tới hết ngày onOrBefore (phiếu cuối năm, OBS-28). */
    Optional<LatestMeasurement> findLatestMeasurement(Long childId, LocalDate onOrBefore);
}
