package com.carenest.service.organization;

import com.carenest.dto.organization.YearSetupResponse;

/** Đọc cấu hình năm học (lớp, phòng, GV, tổ trưởng, giờ chốt) cho xếp lớp năm mới ở module child. */
public interface YearSetupService {

    YearSetupResponse getYearSetup(Long schoolYearId);
}
