package com.carenest.controller.observation;

import com.carenest.dto.observation.ParentAssessmentDtos.ParentDailyAssessment;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentEvaluationsResponse;
import com.carenest.dto.observation.ParentAssessmentDtos.ParentRewardsResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ParentAssessmentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** App phụ huynh: đánh giá ngày, đánh giá đã chốt theo kỳ, khen thưởng của con (AUTH-04, OBS-36). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ParentAssessmentController {
    private static final String LOG_PREFIX = "[ParentAssessmentController]";

    private final ParentAssessmentService parentAssessmentService;

    @GetMapping("/children/{childId}/daily-assessments")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<List<ParentDailyAssessment>>> getDailyAssessments(
            @PathVariable Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDailyAssessments|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        List<ParentDailyAssessment> days = parentAssessmentService.getDailyAssessments(currentUser, childId, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child daily assessments success", days);
    }

    @GetMapping("/children/{childId}/evaluations")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<ParentEvaluationsResponse>> getEvaluations(
            @PathVariable Long childId, @RequestParam(required = false) Long schoolYearId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getEvaluations|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ParentEvaluationsResponse evaluations =
                parentAssessmentService.getEvaluations(currentUser, childId, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child evaluations success", evaluations);
    }

    @GetMapping("/children/{childId}/rewards")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<ParentRewardsResponse>> getRewards(
            @PathVariable Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getRewards|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ParentRewardsResponse rewards = parentAssessmentService.getRewards(currentUser, childId, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child rewards success", rewards);
    }
}
