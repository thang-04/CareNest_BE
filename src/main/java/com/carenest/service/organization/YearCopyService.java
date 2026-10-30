package com.carenest.service.organization;

import com.carenest.dto.organization.YearCopyRequest;
import com.carenest.dto.organization.YearCopyResponse;
import com.carenest.security.CurrentUser;

/** Thiết lập năm học: chép lớp, giáo viên, tổ trưởng, giờ chốt, học kỳ từ một năm học khác sang năm sắp tới. */
public interface YearCopyService {

    YearCopyResponse copyYear(CurrentUser currentUser, Long targetYearId, YearCopyRequest request);
}
