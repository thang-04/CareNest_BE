package com.carenest.controller.organization;

import com.carenest.dto.organization.VicePrincipalAssignmentRequest;
import com.carenest.dto.organization.VicePrincipalAssignmentResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.VicePrincipalAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cấu hình trường › Phân công Phó hiệu trưởng vào điểm trường (chỉ Hiệu trưởng). */
@RestController
@RequestMapping("/school-years/{schoolYearId}/vice-principal-assignments")
@RequiredArgsConstructor
@Slf4j
public class VicePrincipalAssignmentController {
    private static final String LOG_PREFIX = "[VicePrincipalAssignmentController]";

    private final VicePrincipalAssignmentService vicePrincipalAssignmentService;

    @GetMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<VicePrincipalAssignmentResponse>> getAssignments(
            @PathVariable Long schoolYearId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getAssignments|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        VicePrincipalAssignmentResponse assignments =
                vicePrincipalAssignmentService.getAssignments(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get vice principal assignments success", assignments);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<VicePrincipalAssignmentResponse>> assign(
            @PathVariable Long schoolYearId,
            @PathVariable Long userId,
            @RequestBody VicePrincipalAssignmentRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|assign|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId
                + "|vicePrincipalId=" + userId;
        log.info("{}|START", logPrefix);
        VicePrincipalAssignmentResponse assignments =
                vicePrincipalAssignmentService.assign(currentUser, schoolYearId, userId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Assign vice principal success", assignments);
    }
}
