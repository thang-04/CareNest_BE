package com.carenest.service.organization;

import java.time.LocalDate;
import java.util.Optional;

/** Module có dữ liệu đặt trước theo ngày báo cho cấu hình năm học biết khi rút ngắn ngày kết thúc (CFG-YR-01). */
public interface SchoolYearEndUsage {

    /** Mô tả ngắn dữ liệu còn nằm sau ngày kết thúc mới, rỗng khi không có. */
    Optional<String> usageAfter(Long schoolYearId, LocalDate newEnd);
}
