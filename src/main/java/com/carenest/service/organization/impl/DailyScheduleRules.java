package com.carenest.service.organization.impl;

import com.carenest.dto.common.FieldErrorResponse;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PCK-04, ATT-06, ATT-13 (Cần chốt 25/11/2026, phương án A): thứ tự giờ chốt và lịch sinh hoạt. Mỗi vi phạm báo lỗi ở
 * đúng ô (ô đứng sau trong cặp so sánh), mỗi ô một lỗi đầu tiên; FE {@code schoolConfigValidation.js} dùng cùng luật
 * và cùng câu.
 */
final class DailyScheduleRules {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private DailyScheduleRules() {}

    record Schedule(
            LocalTime time,
            LocalTime latestAfternoonArrival,
            LocalTime dropoffOpenFrom,
            LocalTime morningStart,
            LocalTime morningEnd,
            LocalTime afternoonStart,
            LocalTime fullDayPickupFrom,
            LocalTime pickupStart,
            LocalTime pickupEnd,
            LocalTime afternoonEnd) {}

    static List<FieldErrorResponse> violations(Schedule s) {
        Map<String, String> errors = new LinkedHashMap<>();
        // Mở đón ≤ bắt đầu sáng < kết thúc sáng < bắt đầu chiều < mốc đủ ngày ≤ bắt đầu trả < kết thúc trả ≤ kết thúc
        // chiều
        notBefore(errors, "morningStart", "Bắt đầu sáng", s.morningStart(), "Mở đón trẻ", s.dropoffOpenFrom());
        after(errors, "morningEnd", "Kết thúc sáng", s.morningEnd(), "Bắt đầu sáng", s.morningStart());
        after(errors, "afternoonStart", "Bắt đầu chiều", s.afternoonStart(), "Kết thúc sáng", s.morningEnd());
        after(
                errors,
                "fullDayPickupFrom",
                "Mốc tính đủ ngày",
                s.fullDayPickupFrom(),
                "Bắt đầu chiều",
                s.afternoonStart());
        notBefore(errors, "pickupStart", "Bắt đầu trả trẻ", s.pickupStart(), "Mốc tính đủ ngày", s.fullDayPickupFrom());
        after(errors, "pickupEnd", "Kết thúc trả trẻ", s.pickupEnd(), "Bắt đầu trả trẻ", s.pickupStart());
        notBefore(errors, "afternoonEnd", "Kết thúc chiều", s.afternoonEnd(), "Kết thúc trả trẻ", s.pickupEnd());
        // Giờ chốt: từ giờ mở đón trẻ, trước kết thúc sáng
        notBefore(errors, "time", "Giờ chốt", s.time(), "Mở đón trẻ", s.dropoffOpenFrom());
        before(errors, "time", "Giờ chốt", s.time(), "Kết thúc sáng", s.morningEnd());
        // ATT-13: giờ đến muộn nhất buổi chiều sau giờ chốt, từ kết thúc sáng, trước bắt đầu trả trẻ
        String arrival = "Giờ đến muộn nhất buổi chiều";
        after(errors, "latestAfternoonArrival", arrival, s.latestAfternoonArrival(), "Giờ chốt", s.time());
        notBefore(
                errors, "latestAfternoonArrival", arrival, s.latestAfternoonArrival(), "Kết thúc sáng", s.morningEnd());
        before(
                errors,
                "latestAfternoonArrival",
                arrival,
                s.latestAfternoonArrival(),
                "Bắt đầu trả trẻ",
                s.pickupStart());
        List<FieldErrorResponse> result = new ArrayList<>();
        errors.forEach((field, message) -> result.add(new FieldErrorResponse(field, message)));
        return result;
    }

    private static void after(
            Map<String, String> errors,
            String field,
            String label,
            LocalTime value,
            String otherLabel,
            LocalTime other) {
        if (!value.isAfter(other)) {
            errors.putIfAbsent(field, label + " phải sau " + otherLabel + " (" + HH_MM.format(other) + ")");
        }
    }

    private static void notBefore(
            Map<String, String> errors,
            String field,
            String label,
            LocalTime value,
            String otherLabel,
            LocalTime other) {
        if (value.isBefore(other)) {
            errors.putIfAbsent(field, label + " phải bằng hoặc sau " + otherLabel + " (" + HH_MM.format(other) + ")");
        }
    }

    private static void before(
            Map<String, String> errors,
            String field,
            String label,
            LocalTime value,
            String otherLabel,
            LocalTime other) {
        if (!value.isBefore(other)) {
            errors.putIfAbsent(field, label + " phải trước " + otherLabel + " (" + HH_MM.format(other) + ")");
        }
    }
}
