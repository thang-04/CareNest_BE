package com.carenest.controller.health;

import com.carenest.dto.health.HealthExamRequest;
import com.carenest.dto.health.HealthExamResponse;
import com.carenest.dto.health.HealthExamReviewResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthExamService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phiếu khám Phụ lục II của trẻ trong đợt khám: BGH, GV xem; GV lớp lưu nháp, kết luận. Phạm vi kiểm ở service. */
@RestController
@RequestMapping("/health-checks/{campaignId}/children/{childId}/exam")
@RequiredArgsConstructor
@Slf4j
public class HealthExamController {
    private static final String LOG_PREFIX = "[HealthExamController]";

    private final HealthExamService healthExamService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<HealthExamResponse>> getExam(
            @PathVariable Long campaignId, @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getExam|userId=" + currentUser.userId() + "|campaignId=" + campaignId
                + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        HealthExamResponse exam = healthExamService.getExam(currentUser, campaignId, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get health exam success", exam);
    }

    @PutMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<HealthExamResponse>> saveExam(
            @PathVariable Long campaignId,
            @PathVariable Long childId,
            @RequestBody HealthExamRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveExam|userId=" + currentUser.userId() + "|campaignId=" + campaignId
                + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        HealthExamResponse exam = healthExamService.saveExam(currentUser, campaignId, childId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save health exam success", exam);
    }

    @PostMapping("/review-preview")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<HealthExamReviewResponse>> reviewPreview(
            @PathVariable Long campaignId,
            @PathVariable Long childId,
            @RequestBody HealthExamRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|reviewPreview|userId=" + currentUser.userId() + "|campaignId=" + campaignId
                + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        HealthExamReviewResponse review = healthExamService.reviewPreview(currentUser, campaignId, childId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Preview health exam review success", review);
    }
}
