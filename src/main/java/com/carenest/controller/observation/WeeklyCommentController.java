package com.carenest.controller.observation;

import com.carenest.dto.observation.WeeklyCommentDtos.ParentWeeklyCommentResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentBoardResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentDraftRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSaveRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSendRequest;
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
