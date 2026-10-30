package com.carenest.service.health;

import com.carenest.dto.health.HealthCheckClassResponse;
import com.carenest.dto.health.HealthCheckCloseResponse;
import com.carenest.dto.health.HealthCheckDetailResponse;
import com.carenest.dto.health.HealthCheckReopenRequest;
import com.carenest.dto.health.HealthCheckRequest;
import com.carenest.dto.health.HealthCheckResponse;
import com.carenest.dto.health.HealthScheduleWarningResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * Đợt khám sức khỏe và kỳ cân đo theo điểm trường (HLT-01, HLT-08): HT mọi điểm trường, PHT điểm trường của mình quản
 * lý; giáo viên xem điểm trường của lớp mình.
 */
public interface HealthCheckService {

    /** schoolYearId: đợt/kỳ thuộc năm học của ngày bắt đầu (G94-180/HLT-08); null = mọi năm. */
    List<HealthCheckResponse> listHealthChecks(
            CurrentUser currentUser, Long campusId, HealthCheckKind kind, HealthCheckStatus status, Long schoolYearId);

    HealthScheduleWarningResponse getScheduleWarnings(
            CurrentUser currentUser, Long campusId, HealthCheckKind kind, LocalDate startDate, Long excludeId);

    HealthCheckDetailResponse getHealthCheck(CurrentUser currentUser, Long campaignId);

    HealthCheckClassResponse getHealthCheckClass(CurrentUser currentUser, Long campaignId, Long classId);

    HealthCheckResponse createHealthCheck(CurrentUser currentUser, HealthCheckRequest request);

    HealthCheckResponse updateHealthCheck(CurrentUser currentUser, Long campaignId, HealthCheckRequest request);

    HealthCheckResponse cancelHealthCheck(CurrentUser currentUser, Long campaignId);

    HealthCheckCloseResponse closeHealthCheck(CurrentUser currentUser, Long campaignId);

    HealthCheckResponse reopenHealthCheck(CurrentUser currentUser, Long campaignId, HealthCheckReopenRequest request);
}
