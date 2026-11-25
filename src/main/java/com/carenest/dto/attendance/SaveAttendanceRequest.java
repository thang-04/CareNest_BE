package com.carenest.dto.attendance;

import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.LeavePart;
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
     * meals: bữa ⇒ có ăn; có mặt mà client cũ không gửi trường này thì mặc định ăn đủ bữa của nhóm tuổi. absentPart: chỉ
     * dùng khi lớp/ngày được mở lại, đổi buổi nghỉ của trẻ có đơn nghỉ nửa buổi (ATT-13); status là của buổi còn lại.
     * presentPart (ATT-08, user chốt 26/11/2026): chỉ khi lớp/ngày được mở lại và status=PRESENT; FULL_DAY = có mặt cả
     * ngày, MORNING = chỉ học sáng, AFTERNOON = chỉ đến chiều. Null = giữ cách tính cũ.
     */
    public record Entry(
            @NotNull Long childId,
            @NotNull AttendanceStatus status,
            Map<MealSession, Boolean> meals,
            DayPart absentPart,
            LeavePart presentPart) {

        public Entry(Long childId, AttendanceStatus status, Map<MealSession, Boolean> meals) {
            this(childId, status, meals, null, null);
        }

        public Entry(Long childId, AttendanceStatus status, Map<MealSession, Boolean> meals, DayPart absentPart) {
            this(childId, status, meals, absentPart, null);
        }

        /** Buổi vắng suy từ presentPart: chỉ sáng ⇒ vắng chiều, chỉ chiều ⇒ vắng sáng, cả ngày ⇒ null. */
        public DayPart absentPartFromPresent() {
            return presentPart == null || presentPart == LeavePart.FULL_DAY
                    ? null
                    : presentPart.dayPart().other();
        }
    }
}
