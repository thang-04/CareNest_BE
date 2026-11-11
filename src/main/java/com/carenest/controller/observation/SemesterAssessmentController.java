package com.carenest.controller.observation;

import com.carenest.dto.observation.SemesterAssessmentDtos.*;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.SemesterAssessmentService;
import com.carenest.utils.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SemesterAssessmentController {
    private static final String LOG_PREFIX = "[SemesterAssessmentController]";
    private final SemesterAssessmentService service;

    @GetMapping("/assessment/semester-evaluations")
    @PreAuthorize("hasAnyRole('PRINCIPAL','VICE_PRINCIPAL','TEACHER')")
    public ResponseEntity<ResponseJson<Response>> getSemesterAssessment(
            @RequestParam Long classId, @RequestParam int semester, CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|get|classId=" + classId;
        log.info("{}|START", logPrefix);
        var response = service.get(user, classId, semester);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get semester assessment success", response);
    }

    @PutMapping("/assessment/semester-evaluations")
    @PreAuthorize("hasRole('TEACHER') and @permissionGuard.has('ASSESSMENT_RECORD')")
    public ResponseEntity<ResponseJson<Response>> saveSemesterAssessment(
            @Valid @RequestBody Request request, CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|save|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        var response = service.save(user, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Semester assessment success", response);
    }

    @PostMapping("/assessment/semester-evaluations/confirm")
    @PreAuthorize("hasRole('TEACHER') and @permissionGuard.has('ASSESSMENT_CONFIRM')")
    public ResponseEntity<ResponseJson<Response>> confirmSemesterAssessment(
            @Valid @RequestBody Request request, CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|confirm|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        var response = service.confirm(user, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Semester assessment success", response);
    }
}
