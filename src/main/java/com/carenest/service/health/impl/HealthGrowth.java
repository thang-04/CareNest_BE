package com.carenest.service.health.impl;

import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.health.PublishedHealthResponse.GrowthPoint;
import com.carenest.dto.health.PublishedHealthResponse.TrendWarning;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HealthTrendFollowUpType;
import com.carenest.utils.enums.HealthTrendWarningCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xu hướng tăng trưởng tính xác định từ kết quả đã công bố (HLT-02) theo chuẩn WHO (HLT-03): điểm biểu đồ và cảnh báo
 * trung tính, không chẩn đoán (HLT-04). AI chỉ viết lời giải thích dựa trên các số này.
 */
final class HealthGrowth {
    static final String WARNING = "warning";
    static final String INFO = "info";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal WEIGHT_DROP_KG = new BigDecimal("0.2");
    private static final BigDecimal Z_LOW = new BigDecimal("-2");
    private static final BigDecimal Z_HIGH = new BigDecimal("2");
    private static final BigDecimal BMI_Z_JUMP = BigDecimal.ONE;
    private static final long STALL_DAYS = 90;

    private HealthGrowth() {}

    /** Điểm biểu đồ, cũ nhất trước; bỏ lần đo thiếu tuổi hoặc thiếu cả chiều cao lẫn cân nặng. */
    static List<GrowthPoint> points(List<HealthMeasurementResponse> published, Gender gender) {
        return published.stream()
                .filter(m -> m.ageMonths() != null && (m.heightCm() != null || m.weightKg() != null))
                .sorted(Comparator.comparing(HealthMeasurementResponse::date)
                        .thenComparing(HealthMeasurementResponse::id))
                .map(m -> {
                    WhoWeightForAge.Result weight = WhoWeightForAge.classify(gender, m.ageMonths(), m.weightKg());
                    WhoHeightForAge.Result height = WhoHeightForAge.classify(gender, m.ageMonths(), m.heightCm());
                    BigDecimal bmiZ = m.bmiZScore();
                    if (bmiZ == null && m.heightCm() != null && m.weightKg() != null) {
                        WhoBmiForAge.Result bmi =
                                WhoBmiForAge.classify(gender, m.ageMonths(), m.heightCm(), m.weightKg());
                        bmiZ = bmi == null ? null : bmi.zScore();
                    }
                    return new GrowthPoint(
                            m.id(),
                            m.date(),
                            m.ageMonths(),
                            m.heightCm(),
                            m.weightKg(),
                            m.bmi(),
                            weight == null ? null : weight.zScore(),
                            height == null ? null : height.zScore(),
                            bmiZ);
                })
                .toList();
    }

    /** Cảnh báo xu hướng (cần ít nhất 2 điểm); không có gì bất thường ⇒ một dòng info. */
    static List<TrendWarning> warnings(List<GrowthPoint> points) {
        List<TrendWarning> warnings = new ArrayList<>();
        if (points.size() < 2) {
            return warnings;
        }
        GrowthPoint last = points.getLast();
        GrowthPoint prev = points.get(points.size() - 2);
        if (last.weightKg() != null
                && prev.weightKg() != null
                && prev.weightKg().subtract(last.weightKg()).compareTo(WEIGHT_DROP_KG) >= 0) {
            warnings.add(new TrendWarning(
                    HealthTrendWarningCode.WEIGHT_DROP,
                    WARNING,
                    "Cân nặng lần đo ngày " + DMY.format(last.date()) + " giảm "
                            + prev.weightKg()
                                    .subtract(last.weightKg())
                                    .stripTrailingZeros()
                                    .toPlainString()
                            + " kg so với lần đo ngày " + DMY.format(prev.date()) + "."));
        }
        if (last.heightCm() != null
                && prev.heightCm() != null
                && ChronoUnit.DAYS.between(prev.date(), last.date()) >= STALL_DAYS
                && last.heightCm().compareTo(prev.heightCm()) <= 0) {
            warnings.add(new TrendWarning(
                    HealthTrendWarningCode.HEIGHT_STALL,
                    WARNING,
                    "Chiều cao chưa tăng giữa hai lần đo ngày " + DMY.format(prev.date()) + " và "
                            + DMY.format(last.date()) + "."));
        }
        if (last.weightForAgeZ() != null && last.weightForAgeZ().compareTo(Z_LOW) < 0) {
            warnings.add(new TrendWarning(
                    HealthTrendWarningCode.WFA_LOW,
                    WARNING,
                    "Cân nặng theo tuổi lần đo gần nhất dưới mức -2SD của chuẩn WHO."));
        }
        if (last.heightForAgeZ() != null && last.heightForAgeZ().compareTo(Z_LOW) < 0) {
            warnings.add(new TrendWarning(
                    HealthTrendWarningCode.HFA_LOW,
                    WARNING,
                    "Chiều cao theo tuổi lần đo gần nhất dưới mức -2SD của chuẩn WHO."));
        }
        if (last.bmiZScore() != null
                && (last.bmiZScore().compareTo(Z_LOW) < 0 || last.bmiZScore().compareTo(Z_HIGH) > 0)) {
            warnings.add(new TrendWarning(
                    HealthTrendWarningCode.BMI_OUT_OF_RANGE,
                    WARNING,
                    "BMI theo tuổi lần đo gần nhất ngoài khoảng -2SD đến +2SD của chuẩn WHO."));
        }
        if (last.bmiZScore() != null
                && prev.bmiZScore() != null
                && last.bmiZScore().subtract(prev.bmiZScore()).abs().compareTo(BMI_Z_JUMP) >= 0) {
            warnings.add(new TrendWarning(
                    HealthTrendWarningCode.BMI_Z_JUMP,
                    INFO,
                    "BMI theo tuổi thay đổi nhanh giữa hai lần đo gần nhất (z-score từ "
                            + prev.bmiZScore().setScale(2, RoundingMode.HALF_UP) + " sang "
                            + last.bmiZScore().setScale(2, RoundingMode.HALF_UP) + ")."));
        }
        if (warnings.isEmpty()) {
            warnings.add(new TrendWarning(
                    HealthTrendWarningCode.NORMAL,
                    INFO,
                    "Các chỉ số lần đo gần nhất nằm trong khoảng bình thường theo chuẩn WHO."));
        }
        return warnings;
    }

    /** HLT-21: tập lần đo dùng lúc phân tích khác tập lần đo đã công bố hiện tại ⇒ bản giải thích cần phân tích lại. */
    static boolean stale(List<Long> analyzedMeasurementIds, List<GrowthPoint> current) {
        return !Set.copyOf(analyzedMeasurementIds)
                .equals(current.stream().map(GrowthPoint::measurementId).collect(Collectors.toSet()));
    }

    /**
     * Loại gợi ý theo dõi AI được dùng (HLT-21): theo dõi kỳ sau luôn được; có cảnh báo thêm trao đổi phụ huynh; đo lại chỉ
     * khi {@code remeasurePossible} (kỳ cân đo của lần đo mới nhất đang diễn ra và chưa từng gợi ý đo lại lần đo đó).
     */
    static Set<HealthTrendFollowUpType> allowedFollowUps(List<TrendWarning> warnings, boolean remeasurePossible) {
        Set<HealthTrendFollowUpType> allowed = EnumSet.of(HealthTrendFollowUpType.RECHECK_NEXT_ROUND);
        boolean hasWarning = warnings.stream().anyMatch(w -> w.code() != HealthTrendWarningCode.NORMAL);
        if (hasWarning) {
            allowed.add(HealthTrendFollowUpType.TALK_TO_PARENT);
            if (remeasurePossible) {
                allowed.add(HealthTrendFollowUpType.REMEASURE);
            }
        }
        return allowed;
    }
}
