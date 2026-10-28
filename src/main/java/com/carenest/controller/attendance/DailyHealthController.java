package com.carenest.controller.attendance;

import com.carenest.dto.attendance.DailyHealthEntryResponse;
import com.carenest.dto.attendance.DailyHealthRequest;
import com.carenest.dto.attendance.DailyHealthResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sức khỏe trong ngày: GV lớp ghi nhận ở màn điểm danh; Ban giám hiệu xem tổng hợp. Phạm vi kiểm ở service. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class DailyHealthController {
    private static final String LOG_PREFIX = "[DailyHealthController]";

    private final DailyHealthService dailyHealthService;

    @GetMapping("/classes/{classId}/children/{childId}/daily-health")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<DailyHealthResponse.ChildDay>> getClassChildHealth(
            @PathVariable Long classId,
            @PathVariable Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getClassChildHealth|userId=" + currentUser.userId() + "|classId=" + classId
                + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        DailyHealthResponse.ChildDay day = dailyHealthService.getClassChildHealth(currentUser, classId, childId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get daily health success", day);
    }

    @PostMapping("/daily-health-entries")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<DailyHealthEntryResponse>> addEntry(
            @Valid @RequestBody DailyHealthRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|addEntry|userId=" + currentUser.userId() + "|classId=" + request.classId()
                + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        DailyHealthEntryResponse entry = dailyHealthService.addEntry(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Add daily health success", entry);
    }

    @GetMapping("/daily-health/overview")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DailyHealthResponse.Overview>> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) Long ageGroupId,
            @RequestParam(required = false) Long classId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getOverview|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        DailyHealthResponse.Overview overview =
                dailyHealthService.getOverview(currentUser, date, schoolYearId, campusId, ageGroupId, classId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get daily health overview success", overview);
    }

    @GetMapping("/children/{childId}/daily-health")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DailyHealthResponse.ChildDay>> getChildHistory(
            @PathVariable Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getChildHistory|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        DailyHealthResponse.ChildDay day = dailyHealthService.getChildHistory(currentUser, childId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child daily health success", day);
    }
}
