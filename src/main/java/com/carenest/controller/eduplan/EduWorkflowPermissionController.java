package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.EduWorkflowPermissionRequest;
import com.carenest.dto.eduplan.EduWorkflowPermissionResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduWorkflowPermissionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Tab Soạn/duyệt hồ sơ trong trang Phân quyền; chỉ HT (EDU-108, EDU-54). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class EduWorkflowPermissionController {
    private static final String LOG_PREFIX = "[EduWorkflowPermissionController]";

    private final EduWorkflowPermissionService workflowPermissionService;

    @GetMapping("/permissions/edu-workflow")
    @PreAuthorize("@permissionGuard.has('PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<EduWorkflowPermissionResponse>> getWorkflowPermissions(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getWorkflowPermissions|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        EduWorkflowPermissionResponse response = workflowPermissionService.getWorkflowPermissions(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get education workflow permissions success", response);
    }

    @PutMapping("/permissions/edu-workflow")
    @PreAuthorize("@permissionGuard.has('PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<EduWorkflowPermissionResponse>> saveWorkflowPermissions(
            @Valid @RequestBody EduWorkflowPermissionRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveWorkflowPermissions|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        EduWorkflowPermissionResponse response =
                workflowPermissionService.saveWorkflowPermissions(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(
                ApiCode.SUCCESSFUL, "Update education workflow permissions success", response);
    }
}
