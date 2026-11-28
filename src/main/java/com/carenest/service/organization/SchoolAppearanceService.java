package com.carenest.service.organization;

import com.carenest.dto.organization.AppearanceRequest;
import com.carenest.dto.organization.AppearanceResponse;
import com.carenest.security.CurrentUser;

/** Cấu hình trường › Tranh nền giao diện dùng chung cho cả trường. */
public interface SchoolAppearanceService {

    /** withUpdater=false (khách chưa đăng nhập) ẩn họ tên người lưu gần nhất. */
    AppearanceResponse getAppearance(boolean withUpdater);

    AppearanceResponse saveAppearance(CurrentUser currentUser, AppearanceRequest request);
}
