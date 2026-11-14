package com.carenest.controller.observation;

import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationPeriod;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationUnlockRequest;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationUnlockResponse;
import com.carenest.dto.observation.EvaluationDeadlineDtos.OverdueClassItem;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.EvaluationDeadlineService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.EvaluationLockKind;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Hạn, khóa, mở lại đánh giá cuối chủ đề / học kỳ / cuối năm theo lớp – kỳ; lớp quá hạn cho BGH (OBS-06, 27, 28). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class EvaluationDeadlineController {
    private static final String LOG_PREFIX = "[EvaluationDeadlineController]";

    private final EvaluationDeadlineService evaluationDeadlineService;

    @GetMapping("/assessment/evaluation-periods")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_VIEW')")
    public ResponseEntity<ResponseJson<List<EvaluationPeriod>>> getPeriods(
            @RequestParam Long classId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getPeriods|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        List<EvaluationPeriod> response = evaluationDeadlineService.getPeriods(currentUser, classId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get evaluation periods success", response);
    }

    @GetMapping("/assessment/evaluation-unlocks")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_UNLOCK')")
    public ResponseEntity<ResponseJson<List<EvaluationUnlockResponse>>> getUnlocks(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) EvaluationLockKind kind,
            @RequestParam(required = false) Boolean active,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getUnlocks|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<EvaluationUnlockResponse> response =
                evaluationDeadlineService.getUnlocks(currentUser, campusId, classId, kind, active);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get evaluation unlocks success", response);
    }

    /** Mở lại lớp – kỳ đã khóa (HT / PHT có quyền mở lại ở điểm trường của lớp) tới hạn validUntil. */
    @PostMapping("/assessment/evaluation-unlocks")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_UNLOCK')")
    public ResponseEntity<ResponseJson<EvaluationUnlockResponse>> unlock(
            @Valid @RequestBody EvaluationUnlockRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|unlock|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        EvaluationUnlockResponse response = evaluationDeadlineService.unlock(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Unlock evaluation success", response);
    }

    @PostMapping("/assessment/evaluation-unlocks/{unlockId}/revoke")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_UNLOCK')")
    public ResponseEntity<ResponseJson<EvaluationUnlockResponse>> revokeUnlock(
            @PathVariable Long unlockId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|revokeUnlock|userId=" + currentUser.userId() + "|unlockId=" + unlockId;
        log.info("{}|START", logPrefix);
        EvaluationUnlockResponse response = evaluationDeadlineService.revokeUnlock(currentUser, unlockId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Revoke evaluation unlock success", response);
    }

    /** BGH: lớp quá hạn chưa xong (nhận xét tuần, cuối chủ đề, học kỳ, cuối năm) để nhắc. */
    @GetMapping("/assessment/overdue")
    @PreAuthorize("@permissionGuard.has('ASSESSMENT_VIEW')")
    public ResponseEntity<ResponseJson<List<OverdueClassItem>>> getOverdue(
            @RequestParam(required = false) Long campusId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getOverdue|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<OverdueClassItem> response = evaluationDeadlineService.getOverdue(currentUser, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get overdue classes success", response);
    }
}
