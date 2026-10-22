package com.carenest.dto.attendance;

import java.time.LocalDate;
import java.util.List;

/** Response của sức khỏe trong ngày (lớp/trẻ, tổng hợp Ban giám hiệu). */
public final class DailyHealthResponse {

    private DailyHealthResponse() {}

    /** Các lần ghi nhận trong ngày của một trẻ, mới nhất trước; canRecord = GV lớp, hôm nay, trẻ đã lưu Có mặt. */
    public record ChildDay(
            Long classId,
            String className,
            Long childId,
            String childName,
            LocalDate date,
            boolean canRecord,
            List<DailyHealthEntryResponse> entries) {}

    public record Overview(
            LocalDate date,
            Long campusId,
            boolean schoolDay,
            Options options,
            Totals totals,
            List<ClassRow> classes,
            List<Attention> attention) {}

    public record Options(List<Option> ageGroups, List<ClassOption> classes) {}

    public record Option(Long id, String name) {}

    public record ClassOption(Long id, String name, Long ageGroupId, Long campusId) {}

    /** checked = trẻ có mặt đã được ghi nhận; noted = lần mới nhất của trẻ là "Có dấu hiệu cần lưu ý". */
    public record Totals(int present, int checked, int noted, int notified) {}

    public record ClassRow(
            Long classId, String className, Long campusId, int size, int present, int checked, int noted) {}

    /** Trẻ có lần ghi nhận mới nhất là NOTED; readings = các lần trong ngày theo thứ tự thời gian. */
    public record Attention(
            Long childId,
            String childName,
            Long classId,
            String className,
            Long campusId,
            DailyHealthEntryResponse latest,
            List<DailyHealthEntryResponse> readings) {}
}
