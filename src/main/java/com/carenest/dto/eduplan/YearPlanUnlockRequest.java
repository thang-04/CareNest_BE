package com.carenest.dto.eduplan;

import java.time.LocalDate;

/** Spec 004 (Q5): Hiệu trưởng mở khóa kế hoạch năm học / tổ — lý do và hạn mới (sau hôm nay). */
public record YearPlanUnlockRequest(String note, LocalDate deadline) {}
