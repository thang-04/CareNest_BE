package com.carenest.controller.observation;

import com.carenest.dto.observation.ObservationCriteriaResponse;
import com.carenest.dto.observation.ObservationResponse;
import com.carenest.dto.observation.RecordObservationsRequest;
import com.carenest.dto.observation.RecordObservationsResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ObservationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

@RestController
@RequiredArgsConstructor
@Slf4j
public class ObservationController {
    private static final String LOG_PREFIX = "[ObservationController]";

    private final ObservationService observationService;

    @GetMapping("/observation-criteria")
    @PreAuthorize("hasAnyRole('TEACHER', 'VICE_PRINCIPAL', 'PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<ObservationCriteriaResponse>>> listCriteria(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listCriteria|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<ObservationCriteriaResponse> criteria = observationService.listCriteria(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List observation criteria success", criteria);
    }

    // Role chỉ là lớp 1; service kiểm GV có phân công ở đúng lớp này (lớp 2)
    @PostMapping("/classrooms/{classroomId}/observations")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<RecordObservationsResponse>> recordObservations(
            @PathVariable Long classroomId,
            @Valid @RequestBody RecordObservationsRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|recordObservations|userId=" + currentUser.userId() + "|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        RecordObservationsResponse response = observationService.recordObservations(currentUser, classroomId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Record observations success", response);
    }

    @GetMapping("/children/{childId}/observations")
    @PreAuthorize("hasAnyRole('TEACHER', 'VICE_PRINCIPAL', 'PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<ObservationResponse>>> listChildObservations(
            @PathVariable Long childId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listChildObservations|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        List<ObservationResponse> observations =
                observationService.listChildObservations(currentUser, childId, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List observations success", observations);
    }
}
