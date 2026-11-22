package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.PreviousYearTicksResponse;
import com.carenest.security.CurrentUser;

/** EDU-104: đọc tick kế hoạch tổ đã ban hành năm học liền trước để người soạn kế hoạch tổ lấy lại. */
public interface EduPreviousYearTickService {

    PreviousYearTicksResponse getPreviousYearTicks(CurrentUser currentUser, Long planId);
}
