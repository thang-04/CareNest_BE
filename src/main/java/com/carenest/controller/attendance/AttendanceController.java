package com.carenest.controller.attendance;

import com.carenest.dto.attendance.AttendanceClassResponse;
import com.carenest.dto.attendance.AttendanceSummaryResponse;
import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.DayUnlockResponse;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
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

/** Điểm danh & báo ăn theo lớp/ngày, mở lại ngày đã khóa, tổng hợp. Phạm vi lớp/điểm trường kiểm ở service. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AttendanceController {
    private static final String LOG_PREFIX = "[AttendanceController]";

    private final AttendanceService attendanceService;
    private final AttendanceDayUnlockService attendanceDayUnlockService;

    @GetMapping("/attendance/classes")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<AttendanceClassResponse>>> getClasses(
            @RequestParam(required = false) Long schoolYearId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getClasses|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<AttendanceClassResponse> classes = attendanceService.getClasses(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get attendance classes success", classes);
    }

    @GetMapping("/attendance/day-info")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<DayInfoResponse>> getDayInfo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long classId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDayInfo|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        DayInfoResponse info = attendanceService.getDayInfo(currentUser, date, classId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get attendance day info success", info);
    }

    @GetMapping("/classes/{classId}/attendance")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ClassAttendanceResponse>> getClassAttendance(
            @PathVariable Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getClassAttendance|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        ClassAttendanceResponse sheet = attendanceService.getClassAttendance(currentUser, classId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class attendance success", sheet);
    }

    @PutMapping("/classes/{classId}/attendance")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<SaveAttendanceResponse>> saveClassAttendance(
            @PathVariable Long classId, @Valid @RequestBody SaveAttendanceRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveClassAttendance|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        SaveAttendanceResponse saved = attendanceService.saveClassAttendance(currentUser, classId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save class attendance success", saved);
    }

    @GetMapping("/classes/{classId}/attendance/summary")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<AttendanceSummaryResponse.ClassSummary>> getClassSummary(
            @PathVariable Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getClassSummary|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        AttendanceSummaryResponse.ClassSummary summary =
                attendanceService.getClassSummary(currentUser, classId, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class attendance summary success", summary);
    }

    @GetMapping("/attendance/summary")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<AttendanceSummaryResponse.MultiSummary>> getSummary(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSummary|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        AttendanceSummaryResponse.MultiSummary summary =
                attendanceService.getSummary(currentUser, campusId, schoolYearId, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get attendance summary success", summary);
    }

    @GetMapping("/attendance/day-unlocks")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<DayUnlockResponse>>> getUnlocks(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getUnlocks|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<DayUnlockResponse> unlocks = attendanceDayUnlockService.getUnlocks(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get attendance unlocks success", unlocks);
    }

    @PostMapping("/attendance/day-unlocks")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DayUnlockResponse>> unlockDay(
            @Valid @RequestBody DayUnlockRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|unlockDay|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        DayUnlockResponse unlock = attendanceDayUnlockService.unlockDay(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Unlock attendance day success", unlock);
    }

    @PostMapping("/attendance/day-unlocks/{unlockId}/revoke")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DayUnlockResponse>> revokeUnlock(
            @PathVariable Long unlockId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|revokeUnlock|userId=" + currentUser.userId() + "|unlockId=" + unlockId;
        log.info("{}|START", logPrefix);
        DayUnlockResponse unlock = attendanceDayUnlockService.revokeUnlock(currentUser, unlockId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Revoke attendance unlock success", unlock);
    }
}
