package com.carenest.dto.organization;

import com.carenest.utils.enums.YearCopyPart;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import java.util.List;

/**
 * Kết quả chép năm học: số lượng đã chép từng phần và những dòng bị bỏ qua kèm lý do (phòng không dùng được,
 * giáo viên đã nghỉ...). Phần đã có dữ liệu ở năm đích thì giữ nguyên, không ghi đè và không báo.
 */
public record YearCopyResponse(
        List<String> copiedClasses,
        List<String> withoutRoom,
        int teacherClasses,
        int teamLeaders,
        @JsonFormat(pattern = "HH:mm") LocalTime cutoffTime,
        boolean semesterCopied,
        List<Skipped> skipped) {

    public record Skipped(YearCopyPart part, String name, String reason) {}
}
