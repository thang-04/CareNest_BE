package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.common.FieldErrorResponse;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PCK-04, ATT-06, ATT-13 (Cần chốt 25/11/2026, phương án A): biên từng cặp và ô báo lỗi. */
class DailyScheduleRulesTest {

    private static Map<String, String> errors(String... overrides) {
        Map<String, LocalTime> v = new HashMap<>();
        v.put("time", LocalTime.of(8, 30));
        v.put("latestAfternoonArrival", LocalTime.of(14, 0));
        v.put("dropoffOpenFrom", LocalTime.of(6, 30));
        v.put("morningStart", LocalTime.of(7, 0));
        v.put("morningEnd", LocalTime.of(11, 0));
        v.put("afternoonStart", LocalTime.of(13, 30));
        v.put("fullDayPickupFrom", LocalTime.of(15, 0));
        v.put("pickupStart", LocalTime.of(15, 0));
        v.put("pickupEnd", LocalTime.of(17, 0));
        v.put("afternoonEnd", LocalTime.of(17, 0));
        for (int i = 0; i < overrides.length; i += 2) {
            v.put(overrides[i], LocalTime.parse(overrides[i + 1]));
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (FieldErrorResponse e : DailyScheduleRules.violations(new DailyScheduleRules.Schedule(
                v.get("time"),
                v.get("latestAfternoonArrival"),
                v.get("dropoffOpenFrom"),
                v.get("morningStart"),
                v.get("morningEnd"),
                v.get("afternoonStart"),
                v.get("fullDayPickupFrom"),
                v.get("pickupStart"),
                v.get("pickupEnd"),
                v.get("afternoonEnd")))) {
            result.put(e.field(), e.message());
        }
        return result;
    }

    @Test
    void defaultScheduleIsValid() {
        assertThat(errors()).isEmpty();
    }

    @Test
    void morningStartMayEqualDropoffButNotPrecedeIt() {
        assertThat(errors("morningStart", "06:30")).isEmpty();
        assertThat(errors("morningStart", "06:29"))
                .containsExactly(Map.entry("morningStart", "Bắt đầu sáng phải bằng hoặc sau Mở đón trẻ (06:30)"));
    }

    @Test
    void morningEndMustBeAfterMorningStart() {
        assertThat(errors("morningStart", "08:00", "morningEnd", "08:00", "time", "07:30"))
                .containsExactly(Map.entry("morningEnd", "Kết thúc sáng phải sau Bắt đầu sáng (08:00)"));
    }

    @Test
    void afternoonStartMustBeAfterMorningEnd() {
        assertThat(errors("afternoonStart", "11:00"))
                .containsExactly(Map.entry("afternoonStart", "Bắt đầu chiều phải sau Kết thúc sáng (11:00)"));
        assertThat(errors("afternoonStart", "11:01")).isEmpty();
    }

    @Test
    void fullDayPickupFromMustBeAfterAfternoonStart() {
        assertThat(errors("fullDayPickupFrom", "13:30"))
                .containsExactly(Map.entry("fullDayPickupFrom", "Mốc tính đủ ngày phải sau Bắt đầu chiều (13:30)"));
        assertThat(errors("fullDayPickupFrom", "13:31")).isEmpty();
    }

    @Test
    void pickupStartMayEqualFullDayPickupFromButNotPrecedeIt() {
        assertThat(errors("fullDayPickupFrom", "15:00", "pickupStart", "15:00")).isEmpty();
        assertThat(errors("fullDayPickupFrom", "15:30", "pickupStart", "15:00"))
                .containsExactly(
                        Map.entry("pickupStart", "Bắt đầu trả trẻ phải bằng hoặc sau Mốc tính đủ ngày (15:30)"));
    }

    @Test
    void pickupEndMustBeAfterPickupStart() {
        assertThat(errors("pickupEnd", "15:00"))
                .containsExactly(Map.entry("pickupEnd", "Kết thúc trả trẻ phải sau Bắt đầu trả trẻ (15:00)"));
    }

    @Test
    void afternoonEndMayEqualPickupEndButNotPrecedeIt() {
        assertThat(errors("afternoonEnd", "17:00")).isEmpty();
        assertThat(errors("afternoonEnd", "16:59"))
                .containsExactly(
                        Map.entry("afternoonEnd", "Kết thúc chiều phải bằng hoặc sau Kết thúc trả trẻ (17:00)"));
    }

    @Test
    void cutoffTimeFromDropoffAndBeforeMorningEnd() {
        assertThat(errors("time", "06:30")).isEmpty();
        assertThat(errors("time", "06:29"))
                .containsExactly(Map.entry("time", "Giờ chốt phải bằng hoặc sau Mở đón trẻ (06:30)"));
        assertThat(errors("time", "10:59")).isEmpty();
        assertThat(errors("time", "11:00"))
                .containsExactly(Map.entry("time", "Giờ chốt phải trước Kết thúc sáng (11:00)"));
    }

    @Test
    void latestAfternoonArrivalAfterCutoffFromMorningEndBeforePickupStart() {
        assertThat(errors("time", "10:30", "latestAfternoonArrival", "10:30"))
                .containsExactly(
                        Map.entry("latestAfternoonArrival", "Giờ đến muộn nhất buổi chiều phải sau Giờ chốt (10:30)"));
        assertThat(errors("latestAfternoonArrival", "11:00")).isEmpty();
        assertThat(errors("latestAfternoonArrival", "10:59"))
                .containsExactly(Map.entry(
                        "latestAfternoonArrival",
                        "Giờ đến muộn nhất buổi chiều phải bằng hoặc sau Kết thúc sáng (11:00)"));
        assertThat(errors("latestAfternoonArrival", "14:59")).isEmpty();
        assertThat(errors("latestAfternoonArrival", "15:00"))
                .containsExactly(Map.entry(
                        "latestAfternoonArrival", "Giờ đến muộn nhất buổi chiều phải trước Bắt đầu trả trẻ (15:00)"));
    }

    @Test
    void everyOffendingFieldGetsItsOwnError() {
        assertThat(errors("afternoonStart", "10:00", "pickupEnd", "15:00"))
                .containsExactly(
                        Map.entry("afternoonStart", "Bắt đầu chiều phải sau Kết thúc sáng (11:00)"),
                        Map.entry("pickupEnd", "Kết thúc trả trẻ phải sau Bắt đầu trả trẻ (15:00)"));
    }
}
