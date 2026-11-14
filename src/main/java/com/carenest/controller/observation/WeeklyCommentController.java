package com.carenest.controller.observation;

import com.carenest.dto.observation.WeeklyCommentDtos.ParentWeeklyCommentResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentBoardResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentClassSummary;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentDraftRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSaveRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSendRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentUnlockRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentUnlockResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.WeeklyCommentService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Nhận xét tuần theo trẻ (OBS-29): GV lớp viết và gửi phụ huynh; BGH, tổ trưởng xem; phụ huynh xem bản đã gửi. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WeeklyCommentController {
    private static final String LOG_PREFIX = "[WeeklyCommentController]";

    private final WeeklyCommentService weeklyCommentService;

    @GetMapping("/assessment/weekly-comments")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_VIEW')")
    public ResponseEntity<ResponseJson<WeeklyCommentBoardResponse>> getBoard(
            @RequestParam Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getBoard|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        WeeklyCommentBoardResponse response = weeklyCommentService.getBoard(currentUser, classId, weekStart);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get weekly comments success", response);
    }

    @PostMapping("/assessment/weekly-comments/drafts")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_RECORD')")
    public ResponseEntity<ResponseJson<WeeklyCommentBoardResponse>> writeDrafts(
            @Valid @RequestBody WeeklyCommentDraftRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|writeDrafts|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        WeeklyCommentBoardResponse response = weeklyCommentService.writeDrafts(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Write weekly comment drafts success", response);
    }

    @PutMapping("/assessment/weekly-comments")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_RECORD')")
    public ResponseEntity<ResponseJson<WeeklyCommentBoardResponse>> saveDraft(
            @Valid @RequestBody WeeklyCommentSaveRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveDraft|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        WeeklyCommentBoardResponse response = weeklyCommentService.saveDraft(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save weekly comment success", response);
    }

    @PostMapping("/assessment/weekly-comments/send")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_RECORD')")
    public ResponseEntity<ResponseJson<WeeklyCommentBoardResponse>> send(
            @Valid @RequestBody WeeklyCommentSendRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|send|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        WeeklyCommentBoardResponse response = weeklyCommentService.send(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Send weekly comment success", response);
    }

    /** BGH: tình hình gửi nhận xét tuần của các lớp trong phạm vi (đã gửi / tổng, quá hạn). */
    @GetMapping("/assessment/weekly-comments/overview")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_VIEW')")
    public ResponseEntity<ResponseJson<List<WeeklyCommentClassSummary>>> getOverview(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getOverview|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<WeeklyCommentClassSummary> response = weeklyCommentService.getOverview(currentUser, campusId, weekStart);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get weekly comment overview success", response);
    }

    @GetMapping("/assessment/weekly-comments/unlocks")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_UNLOCK')")
    public ResponseEntity<ResponseJson<List<WeeklyCommentUnlockResponse>>> getUnlocks(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getUnlocks|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<WeeklyCommentUnlockResponse> response =
                weeklyCommentService.getUnlocks(currentUser, campusId, classId, active, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get weekly comment unlocks success", response);
    }

    /** Mở lại lớp – tuần đã khóa (HT / PHT có quyền mở lại ở điểm trường của lớp) tới hạn validUntil. */
    @PostMapping("/assessment/weekly-comments/unlocks")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_UNLOCK')")
    public ResponseEntity<ResponseJson<WeeklyCommentUnlockResponse>> unlock(
            @Valid @RequestBody WeeklyCommentUnlockRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|unlock|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        WeeklyCommentUnlockResponse response = weeklyCommentService.unlock(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Unlock weekly comments success", response);
    }

    @PostMapping("/assessment/weekly-comments/unlocks/{unlockId}/revoke")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_UNLOCK')")
    public ResponseEntity<ResponseJson<WeeklyCommentUnlockResponse>> revokeUnlock(
            @PathVariable Long unlockId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|revokeUnlock|userId=" + currentUser.userId() + "|unlockId=" + unlockId;
        log.info("{}|START", logPrefix);
        WeeklyCommentUnlockResponse response = weeklyCommentService.revokeUnlock(currentUser, unlockId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Revoke weekly comment unlock success", response);
    }

    @GetMapping("/children/{childId}/weekly-comments")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<List<ParentWeeklyCommentResponse>>> listForParent(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listForParent|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        List<ParentWeeklyCommentResponse> response = weeklyCommentService.listForParent(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get weekly comments of child success", response);
    }
}
