package com.carenest.controller.observation;

import com.carenest.dto.observation.AssessmentDayUnlockRequest;
import com.carenest.dto.observation.AssessmentDayUnlockResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.AssessmentDayUnlockService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Mở lại ngày đánh giá đã khóa: HT mọi lớp, PHT lớp điểm trường mình (kiểm ở service). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AssessmentDayUnlockController {
    private static final String LOG_PREFIX = "[AssessmentDayUnlockController]";

    private final AssessmentDayUnlockService assessmentDayUnlockService;

    @GetMapping("/assessment/day-unlocks")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<AssessmentDayUnlockResponse>>> getUnlocks(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getUnlocks|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<AssessmentDayUnlockResponse> unlocks =
                assessmentDayUnlockService.getUnlocks(currentUser, campusId, classId, active, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get assessment unlocks success", unlocks);
    }

    @PostMapping("/assessment/day-unlocks")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<AssessmentDayUnlockResponse>> unlockDay(
            @Valid @RequestBody AssessmentDayUnlockRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|unlockDay|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        AssessmentDayUnlockResponse unlock = assessmentDayUnlockService.unlockDay(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Unlock assessment day success", unlock);
    }

    @PostMapping("/assessment/day-unlocks/{unlockId}/revoke")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<AssessmentDayUnlockResponse>> revokeUnlock(
            @PathVariable Long unlockId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|revokeUnlock|userId=" + currentUser.userId() + "|unlockId=" + unlockId;
        log.info("{}|START", logPrefix);
        AssessmentDayUnlockResponse unlock = assessmentDayUnlockService.revokeUnlock(currentUser, unlockId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Revoke assessment unlock success", unlock);
    }
}
