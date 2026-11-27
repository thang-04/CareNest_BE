package com.carenest.service.child.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Số tuần thực học của năm học: tuần (thứ Hai đến thứ Sáu) trong học kỳ I và II có ít nhất một ngày học, tuần nghỉ
 * trọn các ngày thì không tính. Chuẩn 35 tuần (học kỳ I 18, học kỳ II 17) chỉ để cảnh báo
 * ở bước chuẩn bị năm học (user chốt 26/11/2026), không chặn kích hoạt.
 */
final class TeachingWeeks {
    /** Số tuần thực học chuẩn của năm học mầm non. */
    static final int STANDARD = 35;

    private static final int SEMESTER_1_STANDARD = 18;
    private static final int SEMESTER_2_STANDARD = 17;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    // Khung kế hoạch thời gian năm học (QĐ 2308/QĐ-BGDĐT): chỉ cảnh báo vì địa phương được điều chỉnh
    static List<String> warnings(
            LocalDate semester1Start,
            LocalDate semester1End,
            LocalDate semester2Start,
            LocalDate semester2End,
            LocalDate yearStart,
            LocalDate yearEnd,
            List<Break> breaks) {
        if (semester1Start == null || semester1End == null || semester2Start == null || semester2End == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        addShortSemester(result, "học kỳ I", semester1Start, semester1End, SEMESTER_1_STANDARD, breaks);
        addShortSemester(result, "học kỳ II", semester2Start, semester2End, SEMESTER_2_STANDARD, breaks);
        LocalDate semester1Deadline = LocalDate.of(yearStart.getYear() + 1, 1, 18);
        if (!semester1End.isBefore(semester1Deadline)) {
            result.add("học kỳ I phải kết thúc trước " + DMY.format(semester1Deadline));
        }
        LocalDate yearDeadline = LocalDate.of(yearStart.getYear() + 1, 5, 31);
        if (yearEnd.isAfter(yearDeadline)) {
            result.add("năm học phải kết thúc trước hoặc trong ngày " + DMY.format(yearDeadline));
        }
        return result;
    }

    private static void addShortSemester(
            List<String> result, String label, LocalDate from, LocalDate to, int standard, List<Break> breaks) {
        Set<LocalDate> mondays = new HashSet<>();
        collect(from, to, breaks, mondays);
        if (mondays.size() < standard) {
            result.add(label + " " + mondays.size() + " tuần (khung " + standard + ")");
        }
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
