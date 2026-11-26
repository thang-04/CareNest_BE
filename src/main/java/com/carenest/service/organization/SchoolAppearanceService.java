package com.carenest.service.organization;

import com.carenest.dto.organization.AppearanceRequest;
import com.carenest.dto.organization.AppearanceResponse;
import com.carenest.security.CurrentUser;

/** Cấu hình trường › Tranh nền giao diện dùng chung cho cả trường. */
public interface SchoolAppearanceService {

    AppearanceResponse getAppearance();

    AppearanceResponse saveAppearance(CurrentUser currentUser, AppearanceRequest request);
}
