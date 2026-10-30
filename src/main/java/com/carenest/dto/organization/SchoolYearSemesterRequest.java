package com.carenest.dto.organization;

import java.time.LocalDate;

/**
 * Học kỳ I / II do Hiệu trưởng chọn. Không đánh @NotNull: service báo SEMESTER_DATE_REQUIRED theo từng ô để FE hiện
 * lỗi đỏ đúng chỗ; kiểm tra nằm trong năm học, bắt đầu ≤ kết thúc, HK II bắt đầu sau khi HK I kết thúc.
 */
public record SchoolYearSemesterRequest(
        LocalDate semester1Start, LocalDate semester1End, LocalDate semester2Start, LocalDate semester2End) {}
