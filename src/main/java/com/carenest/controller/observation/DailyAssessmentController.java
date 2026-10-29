package com.carenest.controller.observation;

import com.carenest.dto.observation.DailySheetResponse;
import com.carenest.dto.observation.SaveDailyGeneralRequest;
import com.carenest.dto.observation.SaveDailyResponse;
import com.carenest.dto.observation.SaveDailySkillsRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.DailyAssessmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đánh giá hằng ngày theo lớp: Hoạt động chung và Kiến thức kỹ năng dùng chung một bảng ngày. Phạm vi kiểm ở service. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class DailyAssessmentController {
    private static final String LOG_PREFIX = "[DailyAssessmentController]";

    private final DailyAssessmentService dailyAssessmentService;

    @GetMapping("/classes/{classId}/daily-assessment")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<DailySheetResponse>> getDailySheet(
            @PathVariable Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDailySheet|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        DailySheetResponse sheet = dailyAssessmentService.getDailySheet(currentUser, classId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get daily assessment success", sheet);
    }

    @PutMapping("/classes/{classId}/daily-assessment/general")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<SaveDailyResponse>> saveGeneral(
            @PathVariable Long classId, @Valid @RequestBody SaveDailyGeneralRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveGeneral|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        SaveDailyResponse saved = dailyAssessmentService.saveGeneral(currentUser, classId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save daily general assessment success", saved);
    }

    @PutMapping("/classes/{classId}/daily-assessment/skills")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<SaveDailyResponse>> saveSkills(
            @PathVariable Long classId, @Valid @RequestBody SaveDailySkillsRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveSkills|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        SaveDailyResponse saved = dailyAssessmentService.saveSkills(currentUser, classId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save daily skills assessment success", saved);
    }
}
