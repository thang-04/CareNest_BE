package com.carenest.dto.attendance;

import com.carenest.utils.enums.HealthAction;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** notificationStatus: PENDING (chưa có module thông báo phụ huynh). */
public record DailyHealthEntryResponse(
        Long id,
        String requestId,
        Long classId,
        Long childId,
        LocalDate date,
        BigDecimal temperature,
        HealthObservation observation,
        List<HealthSymptom> symptoms,
        String note,
        List<HealthAction> actions,
        Referral referral,
        String notificationStatus,
        Long recordedBy,
        String recordedByName,
        Instant recordedAt) {

    public record Referral(
            String place, @JsonFormat(pattern = "HH:mm") LocalTime time, String escort) {}
}
