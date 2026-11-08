package com.carenest.service.observation;

import com.carenest.dto.observation.QualityReportResponse;
import com.carenest.security.CurrentUser;
import java.time.YearMonth;

/**
 * Báo cáo chất lượng tháng (sổ chỉ đạo chuyên môn PHT, mục 4.1 + 4.2) cho PHT (điểm trường mình) và HT (toàn trường, lọc
 * điểm trường). Năm học theo header (null = năm đang hoạt động); month null = tháng đã kết thúc gần nhất (đầu năm học: tháng đầu).
 */
public interface QualityReportService {

    QualityReportResponse getReport(CurrentUser currentUser, Long schoolYearId, YearMonth month, Long campusId);
}
