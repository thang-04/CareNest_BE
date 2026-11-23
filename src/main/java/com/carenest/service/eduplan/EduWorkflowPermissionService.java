package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.EduWorkflowPermissionRequest;
import com.carenest.dto.eduplan.EduWorkflowPermissionResponse;
import com.carenest.security.CurrentUser;

/** Tab Soạn/duyệt hồ sơ trong trang Phân quyền (EDU-108, EDU-54). */
public interface EduWorkflowPermissionService {

    EduWorkflowPermissionResponse getWorkflowPermissions(CurrentUser currentUser);

    EduWorkflowPermissionResponse saveWorkflowPermissions(
            CurrentUser currentUser, EduWorkflowPermissionRequest request);
}
