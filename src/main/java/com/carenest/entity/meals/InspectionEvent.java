package com.carenest.entity.meals;

import java.time.Instant;

/**
 * Một dòng lịch sử biên bản: ký hoặc sửa lại. {@code imageKey} giữ tệp ảnh của lần ký bị thay (chỉ có ở SIGNED và
 * REOPENED) để chứng từ cũ vẫn xem được.
 */
public record InspectionEvent(String action, Long actorId, String actorName, Instant at, String note, String imageKey) {

    public static final String SIGNED = "SIGNED";
    public static final String REOPENED = "REOPENED";
}
