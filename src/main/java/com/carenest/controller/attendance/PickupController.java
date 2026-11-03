package com.carenest.controller.attendance;

import com.carenest.dto.attendance.PickupBoardResponse;
import com.carenest.dto.attendance.PickupChildResponse;
import com.carenest.dto.attendance.PickupRecordResponse;
import com.carenest.dto.attendance.PickupRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.PickupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đón trẻ: GV đang phụ trách lớp xem bảng đón, trả trẻ hôm nay (PCK-01..04). Phạm vi kiểm ở service. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PickupController {
    private static final String LOG_PREFIX = "[PickupController]";

    private final PickupService pickupService;

    @GetMapping("/classes/{classId}/pickups")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<PickupBoardResponse>> getBoard(
            @PathVariable Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getBoard|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        PickupBoardResponse board = pickupService.getBoard(currentUser, classId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get pickup board success", board);
    }

    @GetMapping("/pickups/children/{childId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<PickupChildResponse>> getChild(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getChild|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        PickupChildResponse child = pickupService.getChild(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get pickup child success", child);
    }

    @PostMapping("/pickups")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<PickupRecordResponse>> recordResult(
            @RequestBody PickupRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|recordResult|userId=" + currentUser.userId() + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        PickupRecordResponse record = pickupService.recordResult(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Record pickup result success", record);
    }

    @GetMapping("/pickups")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<List<PickupRecordResponse>>> listResults(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listResults|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<PickupRecordResponse> results = pickupService.listResults(currentUser, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get pickup results success", results);
    }
}
