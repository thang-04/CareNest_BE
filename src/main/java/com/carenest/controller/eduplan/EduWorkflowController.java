package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.EduWorkflowPolicyRequest;
import com.carenest.dto.eduplan.EduWorkflowPolicyResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduWorkflowService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cấu hình quy trình soạn/duyệt kế hoạch giáo dục (EDU-54).
 */
@RestController
@RequestMapping("/education-plans")
@RequiredArgsConstructor
@Slf4j
public class EduWorkflowController {

    private static final String LOG_PREFIX = "[EduWorkflowController]";

    private final EduWorkflowService workflowService;

    @GetMapping("/workflow-policy")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<EduWorkflowPolicyResponse>> getWorkflowPolicy(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getWorkflowPolicy|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        EduWorkflowPolicyResponse response = workflowService.getPolicy(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get education workflow policy success", response);
    }

    @PutMapping("/workflow-policy")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<EduWorkflowPolicyResponse>> updateWorkflowPolicy(
            @Valid @RequestBody EduWorkflowPolicyRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateWorkflowPolicy|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        EduWorkflowPolicyResponse response = workflowService.savePolicy(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update education workflow policy success", response);
    }
}
