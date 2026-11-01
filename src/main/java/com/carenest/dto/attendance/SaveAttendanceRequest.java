package com.carenest.dto.attendance;

import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.MealSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Lưu điểm danh cả lớp một ngày; gửi lặp cùng trẻ/ngày chỉ cập nhật (ATT-01b). */
public record SaveAttendanceRequest(
        @NotNull LocalDate date, @NotEmpty @Valid List<Entry> entries) {

    /**
     * meals: deprecated, BE bỏ qua — suất tính từ status (ATT-02: có mặt là ăn mọi bữa của nhóm tuổi). Giữ field để
     * client cũ không vỡ; sẽ bỏ ở bản sau.
     */
    public record Entry(@NotNull Long childId, @NotNull AttendanceStatus status, Map<MealSession, Boolean> meals) {}
}
