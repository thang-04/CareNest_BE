package com.carenest.controller.observation;

import com.carenest.dto.observation.EvaluationDtos.DevelopmentChildRow;
import com.carenest.dto.observation.EvaluationDtos.DevelopmentResponse;
import com.carenest.dto.observation.EvaluationDtos.EvaluationContentRequest;
import com.carenest.dto.observation.EvaluationDtos.EvaluationDetailResponse;
import com.carenest.dto.observation.EvaluationDtos.EvaluationRow;
import com.carenest.dto.observation.EvaluationDtos.ProgramCompletionRequest;
import com.carenest.dto.observation.EvaluationDtos.ProgramCompletionResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ChildEvaluationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.EvaluationPeriodType;
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

/** Đánh giá tháng / cuối năm: GV lớp sửa, xác nhận bản nháp; PHT / HT xem bản đã xác nhận. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ChildEvaluationController {
    private static final String LOG_PREFIX = "[ChildEvaluationController]";

    private final ChildEvaluationService childEvaluationService;

    @GetMapping("/assessment/evaluations")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<EvaluationRow>>> list(
            @RequestParam(required = false) EvaluationPeriodType periodType,
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|list|userId=" + currentUser.userId() + "|periodType=" + periodType;
        log.info("{}|START", logPrefix);
        List<EvaluationRow> rows = childEvaluationService.list(currentUser, periodType, schoolYearId, classId, month);
        log.info("{}|rows={}|END", logPrefix, rows.size());
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get evaluations success", rows);
    }

    @GetMapping("/assessment/evaluations/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<EvaluationDetailResponse>> get(@PathVariable Long id, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|get|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        EvaluationDetailResponse detail = childEvaluationService.get(currentUser, id);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get evaluation success", detail);
    }

    @PutMapping("/assessment/evaluations/{id}/content")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<EvaluationRow>> saveContent(
            @PathVariable Long id, @RequestBody EvaluationContentRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveContent|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        EvaluationRow row = childEvaluationService.saveContent(currentUser, id, request.content());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save evaluation success", row);
    }

    @PostMapping("/assessment/evaluations/{id}/confirm")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<EvaluationRow>> confirm(
            @PathVariable Long id, @RequestBody EvaluationContentRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|confirm|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        EvaluationRow row = childEvaluationService.confirm(currentUser, id, request.content());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Confirm evaluation success", row);
    }

    @PostMapping("/assessment/evaluations/{id}/redraft")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<EvaluationRow>> redraft(@PathVariable Long id, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|redraft|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        EvaluationRow row = childEvaluationService.redraft(currentUser, id);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Redraft evaluation success", row);
    }

    @PutMapping("/assessment/evaluations/{id}/program-completion")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<EvaluationRow>> markProgram(
            @PathVariable Long id, @RequestBody ProgramCompletionRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|markProgram|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        EvaluationRow row = childEvaluationService.markProgram(currentUser, id, request.completed());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Mark program completion success", row);
    }

    @GetMapping("/assessment/program-completion")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<ProgramCompletionResponse>> programReport(
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) Long campusId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|programReport|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        ProgramCompletionResponse report = childEvaluationService.programReport(currentUser, schoolYearId, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get program completion success", report);
    }

    @GetMapping("/assessment/children")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<DevelopmentChildRow>>> developmentChildren(
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) Long classId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|developmentChildren|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        List<DevelopmentChildRow> rows = childEvaluationService.developmentChildren(currentUser, schoolYearId, classId);
        log.info("{}|rows={}|END", logPrefix, rows.size());
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get development children success", rows);
    }

    @GetMapping("/assessment/children/{childId}/development")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<DevelopmentResponse>> development(
            @PathVariable Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|development|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        DevelopmentResponse development = childEvaluationService.development(currentUser, childId, start, end);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child development success", development);
    }
}
