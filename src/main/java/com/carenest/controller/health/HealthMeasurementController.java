package com.carenest.controller.health;

import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sổ sức khỏe và số đo của trẻ trong kỳ cân đo: GV phụ trách lớp nhập, sửa; BGH, GV xem. Phạm vi kiểm ở service. */
@RestController
@RequestMapping("/children/{childId}/health")
@RequiredArgsConstructor
@Slf4j
public class HealthMeasurementController {
    private static final String LOG_PREFIX = "[HealthMeasurementController]";

    private final HealthMeasurementService healthMeasurementService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ChildHealthRecordResponse>> getHealthRecord(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getHealthRecord|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ChildHealthRecordResponse record = healthMeasurementService.getHealthRecord(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get health record success", record);
    }

    @PostMapping("/measurements")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<HealthMeasurementResponse>> createMeasurement(
            @PathVariable Long childId, @Valid @RequestBody HealthMeasurementRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createMeasurement|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        HealthMeasurementResponse created = healthMeasurementService.createMeasurement(currentUser, childId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create measurement success", created);
    }

    @PutMapping("/measurements/{measurementId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<HealthMeasurementResponse>> updateMeasurement(
            @PathVariable Long childId,
            @PathVariable Long measurementId,
            @Valid @RequestBody HealthMeasurementRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateMeasurement|userId=" + currentUser.userId() + "|childId=" + childId
                + "|measurementId=" + measurementId;
        log.info("{}|START", logPrefix);
        HealthMeasurementResponse updated =
                healthMeasurementService.updateMeasurement(currentUser, childId, measurementId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update measurement success", updated);
    }
}
