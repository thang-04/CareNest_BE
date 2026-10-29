package com.carenest.controller.observation;

import com.carenest.dto.observation.ClassAssessmentDetailResponse;
import com.carenest.dto.observation.ClassAssessmentOverviewResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ClassAssessmentOverviewService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tổng quan đánh giá các lớp theo ngày/tuần/tháng cho PHT (điểm trường mình) và HT (toàn trường). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClassAssessmentOverviewController {
    private static final String LOG_PREFIX = "[ClassAssessmentOverviewController]";

    private final ClassAssessmentOverviewService classAssessmentOverviewService;

    @GetMapping("/assessment/overview")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<ClassAssessmentOverviewResponse>> getOverview(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) Long ageGroupId,
            @RequestParam(required = false) Long classId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getOverview|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        ClassAssessmentOverviewResponse overview = classAssessmentOverviewService.getOverview(
                currentUser, period, schoolYearId, campusId, ageGroupId, classId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get assessment overview success", overview);
    }

    @GetMapping("/assessment/overview/classes/{classId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<ClassAssessmentDetailResponse>> getClassDetail(
            @PathVariable Long classId, @RequestParam(required = false) String period, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getClassDetail|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        ClassAssessmentDetailResponse detail =
                classAssessmentOverviewService.getClassDetail(currentUser, classId, period);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class assessment detail success", detail);
    }
}
