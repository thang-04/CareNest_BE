package com.carenest.controller.account;

import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.dto.account.PhoneChangeDecisionRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.GuardianPhoneChangeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.PhoneChangeStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yêu cầu đổi SĐT phụ huynh đã có tài khoản (G94-180/AUTH-21, user chốt 2026-11-10): danh sách, duyệt, từ chối. Quyền
 * ở đây là lớp 1; phạm vi điểm trường của trẻ, người gửi không tự duyệt kiểm ở service. Không log SĐT.
 */
@RestController
@RequestMapping("/parent-accounts/phone-change-requests")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("@permissionGuard.has('PARENT_ACCOUNT_MANAGE')")
public class GuardianPhoneChangeController {
    private static final String LOG_PREFIX = "[GuardianPhoneChangeController]";

    private final GuardianPhoneChangeService guardianPhoneChangeService;

    @GetMapping
    // Tên method = operationId OpenAPI: tránh "list" để không đổi số hậu tố operationId của API khác
    public ResponseEntity<ResponseJson<List<GuardianPhoneChangeResponse>>> listPhoneChangeRequests(
            @RequestParam(required = false) PhoneChangeStatus status, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listPhoneChangeRequests|userId=" + currentUser.userId() + "|status=" + status;
        log.info("{}|START", logPrefix);
        List<GuardianPhoneChangeResponse> requests = guardianPhoneChangeService.list(currentUser, status);
        log.info("{}|count={}|END", logPrefix, requests.size());
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List phone change requests success", requests);
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ResponseJson<GuardianPhoneChangeResponse>> approve(
            @PathVariable Long requestId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|approve|userId=" + currentUser.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        GuardianPhoneChangeResponse approved = guardianPhoneChangeService.approve(currentUser, requestId);
        log.info("{}|sms={}|END", logPrefix, approved.smsStatus());
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Approve phone change success", approved);
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ResponseJson<GuardianPhoneChangeResponse>> reject(
            @PathVariable Long requestId,
            @RequestBody(required = false) PhoneChangeDecisionRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|reject|userId=" + currentUser.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        GuardianPhoneChangeResponse rejected = guardianPhoneChangeService.reject(currentUser, requestId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Reject phone change success", rejected);
    }
}
