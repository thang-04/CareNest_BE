package com.carenest.service.child.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Số tuần thực học của năm học: tuần (thứ Hai đến thứ Sáu) trong học kỳ I và II có ít nhất một ngày học, tuần nghỉ
 * trọn các ngày thì không tính. Chuẩn 35 tuần chỉ để cảnh báo ở bước chuẩn bị năm học (user chốt 26/11/2026), chưa
 * đối chiếu nguyên văn văn bản của Bộ GD&ĐT nên không chặn kích hoạt.
 */
final class TeachingWeeks {
    /** Số tuần thực học chuẩn của năm học mầm non. */
    static final int STANDARD = 35;

    record Break(LocalDate start, LocalDate end) {}

    private TeachingWeeks() {}

    /** null nếu thiếu ngày học kỳ (chưa tính được). */
    static Integer count(
            LocalDate semester1Start,
            LocalDate semester1End,
            LocalDate semester2Start,
            LocalDate semester2End,
            List<Break> breaks) {
        if (semester1Start == null || semester1End == null || semester2Start == null || semester2End == null) {
            return null;
        }
        Set<LocalDate> mondays = new HashSet<>();
        collect(semester1Start, semester1End, breaks, mondays);
        collect(semester2Start, semester2End, breaks, mondays);
        return mondays.size();
    }

    private static void collect(LocalDate from, LocalDate to, List<Break> breaks, Set<LocalDate> mondays) {
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            DayOfWeek week = day.getDayOfWeek();
            if (week == DayOfWeek.SATURDAY || week == DayOfWeek.SUNDAY || onBreak(day, breaks)) {
                continue;
            }
            mondays.add(day.with(DayOfWeek.MONDAY));
        }
    }

    private static boolean onBreak(LocalDate day, List<Break> breaks) {
        return breaks.stream()
                .anyMatch(
                        b -> b.start() != null && b.end() != null && !day.isBefore(b.start()) && !day.isAfter(b.end()));
    }
}
