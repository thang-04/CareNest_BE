package com.carenest.controller.account;

import com.carenest.dto.child.ChildProfileChangeCreateRequest;
import com.carenest.dto.child.ChildProfileChangeDecisionRequest;
import com.carenest.dto.child.ChildProfileChangeResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.ChildProfileChangeRequestService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.ProfileChangeStatus;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CHD-11 (user chốt 2026-11-15): GV có CHILD_PROFILE_EDIT gửi yêu cầu sửa hồ sơ/khai báo sức khỏe trẻ lớp mình; HT
 * hoặc PHT điểm trường của trẻ duyệt/từ chối. Lớp 1 ở đây; vai trò người gửi/duyệt, phạm vi trẻ kiểm ở service.
 */
@RestController
@RequestMapping("/children")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("@permissionGuard.has('CHILD_PROFILE_EDIT')")
public class ChildProfileChangeRequestController {
    private static final String LOG_PREFIX = "[ChildProfileChangeRequestController]";

    private final ChildProfileChangeRequestService changeRequestService;

    @PostMapping("/{childId}/profile-change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<ChildProfileChangeResponse>> submitProfileChange(
            @PathVariable Long childId,
            @Valid @RequestBody ChildProfileChangeCreateRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|submitProfileChange|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ChildProfileChangeResponse created = changeRequestService.submit(currentUser, childId, request);
        log.info("{}|requestId={}|END", logPrefix, created.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Submit child profile change success", created);
    }

    @GetMapping("/{childId}/profile-change-requests/latest")
    public ResponseEntity<ResponseJson<List<ChildProfileChangeResponse>>> getLatestProfileChanges(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getLatestProfileChanges|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        List<ChildProfileChangeResponse> latest = changeRequestService.latestOfChild(currentUser, childId);
        log.info("{}|count={}|END", logPrefix, latest.size());
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get latest child profile changes success", latest);
    }

    @GetMapping("/profile-change-requests")
    public ResponseEntity<ResponseJson<List<ChildProfileChangeResponse>>> listProfileChangeRequests(
            @RequestParam(required = false) ProfileChangeStatus status, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|listProfileChangeRequests|userId=" + currentUser.userId() + "|status=" + status;
        log.info("{}|START", logPrefix);
        List<ChildProfileChangeResponse> requests = changeRequestService.list(currentUser, status);
        log.info("{}|count={}|END", logPrefix, requests.size());
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List child profile changes success", requests);
    }

    @GetMapping("/profile-change-requests/{requestId}")
    public ResponseEntity<ResponseJson<ChildProfileChangeResponse>> getProfileChangeRequest(
            @PathVariable Long requestId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getProfileChangeRequest|userId=" + currentUser.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        ChildProfileChangeResponse request = changeRequestService.get(currentUser, requestId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child profile change success", request);
    }

    @PostMapping("/profile-change-requests/{requestId}/approve")
    public ResponseEntity<ResponseJson<ChildProfileChangeResponse>> approveProfileChange(
            @PathVariable Long requestId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|approveProfileChange|userId=" + currentUser.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        ChildProfileChangeResponse approved = changeRequestService.approve(currentUser, requestId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Approve child profile change success", approved);
    }

    @PostMapping("/profile-change-requests/{requestId}/reject")
    public ResponseEntity<ResponseJson<ChildProfileChangeResponse>> rejectProfileChange(
            @PathVariable Long requestId,
            @RequestBody(required = false) ChildProfileChangeDecisionRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|rejectProfileChange|userId=" + currentUser.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        ChildProfileChangeResponse rejected =
                changeRequestService.reject(currentUser, requestId, request == null ? null : request.note());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Reject child profile change success", rejected);
    }
}
