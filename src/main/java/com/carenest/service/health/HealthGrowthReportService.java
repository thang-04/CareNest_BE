package com.carenest.service.health;

import com.carenest.dto.health.HealthGrowthReportResponse;
import com.carenest.security.CurrentUser;
import java.time.YearMonth;

/** Báo cáo tổng hợp tăng trưởng theo tháng, mẫu 5.1 (HLT-20). */
public interface HealthGrowthReportService {

    /** HT mọi điểm trường, PHT điểm trường được phân công; {@code campusId} null = mọi điểm trường trong phạm vi. */
    HealthGrowthReportResponse getGrowthReport(CurrentUser currentUser, YearMonth month, Long campusId);
}
