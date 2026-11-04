package com.carenest.controller.observation;

import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationRequest;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationResponse;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeItem;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ThemeEvaluationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đánh giá cuối chủ đề của lớp mẫu giáo (OBS-27): GV lớp sửa, xác nhận; BGH, tổ trưởng xem. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ThemeEvaluationController {
    private static final String LOG_PREFIX = "[ThemeEvaluationController]";

    private final ThemeEvaluationService themeEvaluationService;

    @GetMapping("/assessment/theme-evaluations/themes")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<ThemeItem>>> getThemes(
            @RequestParam Long classId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getThemes|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        List<ThemeItem> response = themeEvaluationService.listThemes(currentUser, classId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get themes success", response);
    }

    @GetMapping("/assessment/theme-evaluations")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ThemeEvaluationResponse>> getEvaluation(
            @RequestParam Long classId, @RequestParam Long themeId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getEvaluation|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        ThemeEvaluationResponse response = themeEvaluationService.get(currentUser, classId, themeId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get theme evaluation success", response);
    }

    @PutMapping("/assessment/theme-evaluations")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<ThemeEvaluationResponse>> saveEvaluation(
            @Valid @RequestBody ThemeEvaluationRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|saveEvaluation|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        ThemeEvaluationResponse response = themeEvaluationService.save(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save theme evaluation success", response);
    }

    @PostMapping("/assessment/theme-evaluations/confirm")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<ThemeEvaluationResponse>> confirmEvaluation(
            @Valid @RequestBody ThemeEvaluationRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|confirmEvaluation|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        ThemeEvaluationResponse response = themeEvaluationService.confirm(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Confirm theme evaluation success", response);
    }
}
