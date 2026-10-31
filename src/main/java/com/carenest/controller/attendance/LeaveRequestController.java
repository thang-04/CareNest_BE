package com.carenest.controller.attendance;

import com.carenest.dto.attendance.LeaveRequestDtos.CreateLeaveRequest;
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveRequestResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.LeaveRequestService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đơn nghỉ: phụ huynh gửi / hủy qua App (ATT-05); GV, PHT, HT xem theo phạm vi. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class LeaveRequestController {
    private static final String LOG_PREFIX = "[LeaveRequestController]";

    private final LeaveRequestService leaveRequestService;

    @PostMapping("/leave-requests")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<LeaveRequestResponse>> createLeaveRequest(
            @Valid @RequestBody CreateLeaveRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|createLeaveRequest|userId=" + currentUser.userId() + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        LeaveRequestResponse leave = leaveRequestService.createLeaveRequest(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Create leave request success", leave);
    }

    @GetMapping("/leave-requests/me")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<List<LeaveRequestResponse>>> findMyLeaveRequests(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|findMyLeaveRequests|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<LeaveRequestResponse> leaves = leaveRequestService.findMyLeaveRequests(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get my leave requests success", leaves);
    }

    @PatchMapping("/leave-requests/{leaveRequestId}/cancel")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<LeaveRequestResponse>> cancelLeaveRequest(
            @PathVariable Long leaveRequestId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|cancelLeaveRequest|userId=" + currentUser.userId() + "|leaveRequestId=" + leaveRequestId;
        log.info("{}|START", logPrefix);
        LeaveRequestResponse leave = leaveRequestService.cancelLeaveRequest(currentUser, leaveRequestId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Cancel leave request success", leave);
    }

    @GetMapping("/leave-requests")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<LeaveRequestResponse>>> searchLeaveRequests(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|searchLeaveRequests|userId=" + currentUser.userId() + "|classId=" + classId
                + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<LeaveRequestResponse> leaves =
                leaveRequestService.searchLeaveRequests(currentUser, classId, campusId, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Search leave requests success", leaves);
    }
}
