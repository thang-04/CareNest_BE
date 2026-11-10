package com.carenest.controller.organization;

import com.carenest.dto.account.CreateStaffAssignmentRequest;
import com.carenest.dto.account.EndStaffAssignmentRequest;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.StaffingService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Quyền lớp 1; phạm vi role/campus được phân công do StaffAccessPolicy kiểm ở service (AUTH-16)
@RestController
@RequestMapping("/staff-assignments")
@RequiredArgsConstructor
@Slf4j
public class StaffAssignmentController {
    private static final String LOG_PREFIX = "[StaffAssignmentController]";

    private final StaffingService staffingService;

    @PostMapping
    @PreAuthorize("@permissionGuard.has('STAFF_ASSIGNMENT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<StaffAssignmentResponse>> assignStaff(
            @Valid @RequestBody CreateStaffAssignmentRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|assignStaff|actorUserId=" + currentUser.userId() + "|userId=" + request.userId();
        log.info("{}|START", logPrefix);
        StaffAssignmentResponse created = staffingService.assignStaff(currentUser, request);
        log.info("{}|assignmentId={}|END", logPrefix, created.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Assign staff success", created);
    }

    @PatchMapping("/{assignmentId}/end")
    @PreAuthorize("@permissionGuard.has('STAFF_ASSIGNMENT_MANAGE')")
    public ResponseEntity<ResponseJson<StaffAssignmentResponse>> endAssignment(
            @PathVariable Long assignmentId,
            @Valid @RequestBody EndStaffAssignmentRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|endAssignment|actorUserId=" + currentUser.userId() + "|assignmentId=" + assignmentId;
        log.info("{}|START", logPrefix);
        StaffAssignmentResponse ended = staffingService.endAssignment(currentUser, assignmentId, request.validTo());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "End assignment success", ended);
    }
}
