package com.carenest.controller.organization;

import com.carenest.dto.organization.CutoffRequest;
import com.carenest.dto.organization.CutoffResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AttendanceCutoffService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cấu hình trường › Giờ chốt điểm danh của một năm học. */
@RestController
@RequestMapping("/school-years/{schoolYearId}/cutoff")
@RequiredArgsConstructor
@Slf4j
public class AttendanceCutoffController {
    private static final String LOG_PREFIX = "[AttendanceCutoffController]";

    private final AttendanceCutoffService attendanceCutoffService;

    // Điểm danh, sổ báo ăn, bếp đều cần biết giờ chốt
    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'KITCHEN_STAFF')")
    public ResponseEntity<ResponseJson<CutoffResponse>> getCutoff(
            @PathVariable Long schoolYearId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getCutoff|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        CutoffResponse cutoff = attendanceCutoffService.getCutoff(schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get cutoff success", cutoff);
    }

    @PutMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<CutoffResponse>> saveCutoff(
            @PathVariable Long schoolYearId, @Valid @RequestBody CutoffRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveCutoff|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        CutoffResponse cutoff = attendanceCutoffService.saveCutoff(currentUser, schoolYearId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save cutoff success", cutoff);
    }
}
