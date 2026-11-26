package com.carenest.controller.attendance;

import com.carenest.dto.attendance.ParentAttendanceDtos.ParentAttendanceResponse;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentAttendanceSchedule;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentDailyHealthResponse;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentTodayResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.ParentAttendanceService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** App phụ huynh: điểm danh hôm nay, điểm danh theo ngày, sức khỏe đầu ngày của con (AUTH-04, ATT-15). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ParentAttendanceController {
    private static final String LOG_PREFIX = "[ParentAttendanceController]";

    private final ParentAttendanceService parentAttendanceService;

    @GetMapping("/parent/attendance-schedule")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<ParentAttendanceSchedule>> getSchedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSchedule|userId=" + currentUser.userId() + "|date=" + date;
        log.info("{}|START", logPrefix);
        ParentAttendanceSchedule schedule = parentAttendanceService.getSchedule(currentUser, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get parent attendance schedule success", schedule);
    }

    @GetMapping("/children/{childId}/attendance/today")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<ParentTodayResponse>> getToday(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getToday|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ParentTodayResponse today = parentAttendanceService.getToday(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child attendance today success", today);
    }

    @GetMapping("/children/{childId}/attendance")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<ParentAttendanceResponse>> getAttendance(
            @PathVariable Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getAttendance|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ParentAttendanceResponse attendance = parentAttendanceService.getAttendance(currentUser, childId, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child attendance success", attendance);
    }

    @GetMapping("/children/{childId}/daily-health/parent")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<List<ParentDailyHealthResponse>>> getDailyHealth(
            @PathVariable Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDailyHealth|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        List<ParentDailyHealthResponse> health = parentAttendanceService.getDailyHealth(currentUser, childId, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child daily health success", health);
    }
}
