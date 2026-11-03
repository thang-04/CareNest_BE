package com.carenest.dto.health;

import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả khám chuyên khoa phụ huynh báo lại. {@code images}: tối đa 3 ảnh dạng data URL ({@code data:image/jpeg;base64,...};
 * JPG, PNG, WEBP, mỗi ảnh tối đa 1 MB); {@code nextVisitOn}: ngày hẹn khám lại, sau ngày khám.
 */
public record HealthFollowUpExternalResultRequest(
        LocalDate resultOn, String note, String facility, List<String> images, LocalDate nextVisitOn) {}
