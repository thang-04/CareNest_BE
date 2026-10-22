package com.carenest.service.organization;

import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.SchoolYearRequest;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.SchoolYearStatus;
import java.util.List;

/** Quản lý năm học (màn Cấu hình trường › Năm học): xem cho BGH/GV, ghi chỉ Hiệu trưởng. */
public interface SchoolYearService {

    List<SchoolYearDetailResponse> getSchoolYears(CurrentUser currentUser, SchoolYearStatus status);

    SchoolYearDetailResponse getSchoolYear(CurrentUser currentUser, Long schoolYearId);

    SchoolYearDetailResponse createSchoolYear(CurrentUser currentUser, SchoolYearRequest request);

    SchoolYearDetailResponse updateSchoolYear(CurrentUser currentUser, Long schoolYearId, SchoolYearRequest request);

    void deleteSchoolYear(CurrentUser currentUser, Long schoolYearId);

    SchoolYearDetailResponse activateSchoolYear(CurrentUser currentUser, Long schoolYearId, String confirmName);

    SchoolYearDetailResponse closeSchoolYear(CurrentUser currentUser, Long schoolYearId);
}
