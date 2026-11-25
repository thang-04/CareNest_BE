package com.carenest.dto.child;

import java.time.LocalDate;

/**
 * Lớp trẻ sẽ vào từ ngày {@code from} (sau hôm nay, G94-180/CHD-14): lịch chuyển lớp, hoặc lớp chưa bắt đầu của trẻ mới
 * tiếp nhận / mới xếp. Tới hết ngày trước {@code from} trẻ vẫn ở lớp hiện tại (nếu có).
 */
public record ChildNextClassResponse(Long classId, String className, LocalDate from) {}
