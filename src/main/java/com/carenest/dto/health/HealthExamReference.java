package com.carenest.dto.health;

import java.math.BigDecimal;

/**
 * Mốc tham khảo theo tuổi tháng của trẻ tại ngày khám: khoảng mạch, nhịp thở theo nhóm tuổi, ngưỡng sốt/hạ thân nhiệt,
 * khoảng vòng đầu tạm (null khi trẻ quá 60 tháng), có hỏi nguy cơ tự kỷ (16–30 tháng) hay không.
 */
public record HealthExamReference(
        int ageMonths,
        HealthExamVitalReference vitals,
        HealthExamHeadReference head,
        boolean headApplies,
        boolean autismQuestion,
        String label) {

    public record HealthExamVitalReference(
            String label,
            HealthExamRange pulse,
            HealthExamRange respiratoryRate,
            HealthExamTemperatureReference temperature) {}

    public record HealthExamRange(int min, int max) {}

    public record HealthExamTemperatureReference(BigDecimal feverFrom, BigDecimal hypothermiaBelow) {}

    public record HealthExamHeadReference(BigDecimal low, BigDecimal high) {}
}
