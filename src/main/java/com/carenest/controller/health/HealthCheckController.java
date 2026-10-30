package com.carenest.controller.health;

import com.carenest.dto.health.HealthCheckClassResponse;
import com.carenest.dto.health.HealthCheckCloseResponse;
import com.carenest.dto.health.HealthCheckDetailResponse;
import com.carenest.dto.health.HealthCheckReopenRequest;
import com.carenest.dto.health.HealthCheckRequest;
import com.carenest.dto.health.HealthCheckResponse;
import com.carenest.dto.health.HealthScheduleWarningResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthCheckService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Đợt khám sức khỏe, kỳ cân đo: HT, PHT quản lý theo điểm trường; giáo viên xem. Phạm vi kiểm ở service. */
@RestController
@RequestMapping("/health-checks")
@RequiredArgsConstructor
@Slf4j
public class HealthCheckController {
    private static final String LOG_PREFIX = "[HealthCheckController]";

    private final HealthCheckService healthCheckService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<HealthCheckResponse>>> listHealthChecks(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) HealthCheckKind kind,
            @RequestParam(required = false) HealthCheckStatus status,
            @RequestParam(required = false) Long schoolYear,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listHealthChecks|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<HealthCheckResponse> campaigns =
                healthCheckService.listHealthChecks(currentUser, campusId, kind, status, schoolYear);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get health checks success", campaigns);
    }

    @GetMapping("/schedule-warnings")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<HealthScheduleWarningResponse>> getScheduleWarnings(
            @RequestParam Long campusId,
            @RequestParam(required = false) HealthCheckKind kind,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) Long excludeId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getScheduleWarnings|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        HealthScheduleWarningResponse warnings =
                healthCheckService.getScheduleWarnings(currentUser, campusId, kind, startDate, excludeId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get schedule warnings success", warnings);
    }

    @GetMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<HealthCheckDetailResponse>> getHealthCheck(
            @PathVariable Long campaignId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        HealthCheckDetailResponse detail = healthCheckService.getHealthCheck(currentUser, campaignId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get health check success", detail);
    }

    @GetMapping("/{campaignId}/classes/{classId}/children")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<HealthCheckClassResponse>> getHealthCheckClass(
            @PathVariable Long campaignId, @PathVariable Long classId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getHealthCheckClass|userId=" + currentUser.userId() + "|campaignId="
                + campaignId + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        HealthCheckClassResponse roster = healthCheckService.getHealthCheckClass(currentUser, campaignId, classId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get health check class success", roster);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<HealthCheckResponse>> createHealthCheck(
            @Valid @RequestBody HealthCheckRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|createHealthCheck|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        HealthCheckResponse created = healthCheckService.createHealthCheck(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create health check success", created);
    }

    @PutMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<HealthCheckResponse>> updateHealthCheck(
            @PathVariable Long campaignId, @Valid @RequestBody HealthCheckRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|updateHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        HealthCheckResponse updated = healthCheckService.updateHealthCheck(currentUser, campaignId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update health check success", updated);
    }

    @PostMapping("/{campaignId}/cancel")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<HealthCheckResponse>> cancelHealthCheck(
            @PathVariable Long campaignId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|cancelHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        HealthCheckResponse cancelled = healthCheckService.cancelHealthCheck(currentUser, campaignId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Cancel health check success", cancelled);
    }

    @PostMapping("/{campaignId}/close")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<HealthCheckCloseResponse>> closeHealthCheck(
            @PathVariable Long campaignId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|closeHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        HealthCheckCloseResponse closed = healthCheckService.closeHealthCheck(currentUser, campaignId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Close health check success", closed);
    }

    @PostMapping("/{campaignId}/reopen")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<HealthCheckResponse>> reopenHealthCheck(
            @PathVariable Long campaignId,
            @Valid @RequestBody HealthCheckReopenRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|reopenHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        HealthCheckResponse reopened = healthCheckService.reopenHealthCheck(currentUser, campaignId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Reopen health check success", reopened);
    }
}
