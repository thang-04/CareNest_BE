package com.carenest.service.organization;

import com.carenest.dto.organization.CutoffRequest;
import com.carenest.dto.organization.CutoffResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/** Cấu hình trường › Giờ chốt điểm danh (ATT-06). Module điểm danh/suất ăn đọc giờ chốt qua findCutoffTime. */
public interface AttendanceCutoffService {

    CutoffResponse getCutoff(Long schoolYearId);

    CutoffResponse saveCutoff(CurrentUser currentUser, Long schoolYearId, CutoffRequest request);

    Optional<LocalTime> findCutoffTime(Long schoolYearId, LocalDate date);
}
