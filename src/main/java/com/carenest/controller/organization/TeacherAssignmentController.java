package com.carenest.controller.organization;

import com.carenest.dto.organization.ClassTeachersRequest;
import com.carenest.dto.organization.ClassTeachersResponse;
import com.carenest.dto.organization.TeacherAssignmentResponse;
import com.carenest.dto.organization.TeamLeaderRequest;
import com.carenest.dto.organization.TeamLeaderResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.TeacherAssignmentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cấu hình trường › Phân công giáo viên và tổ trưởng nhóm tuổi. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TeacherAssignmentController {
    private static final String LOG_PREFIX = "[TeacherAssignmentController]";

    private final TeacherAssignmentService teacherAssignmentService;

    @GetMapping("/teacher-assignments")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<TeacherAssignmentResponse>> getTeacherAssignments(
            @RequestParam(name = "schoolYearId", required = false) Long schoolYearIdParam,
            @RequestParam(required = false) Long schoolYear,
            @RequestParam(required = false) String campusId,
            CurrentUser currentUser) {
        // FE cũ gửi tham số schoolYear; nhận cả hai tên
        Long schoolYearId = schoolYearIdParam != null ? schoolYearIdParam : schoolYear;
        String logPrefix = LOG_PREFIX + "|getTeacherAssignments|userId=" + currentUser.userId() + "|schoolYearId="
                + schoolYearId + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        TeacherAssignmentResponse assignments =
                teacherAssignmentService.getTeacherAssignments(currentUser, schoolYearId, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get teacher assignments success", assignments);
    }

    @PutMapping("/classes/{classId}/teachers")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<ClassTeachersResponse>> assignClassTeachers(
            @PathVariable Long classId, @Valid @RequestBody ClassTeachersRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|assignClassTeachers|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        ClassTeachersResponse classTeachers =
                teacherAssignmentService.assignClassTeachers(currentUser, classId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Assign class teachers success", classTeachers);
    }

    @GetMapping("/team-leader-assignments")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<TeamLeaderResponse>> getTeamLeaders(
            @RequestParam(name = "schoolYearId", required = false) Long schoolYearIdParam,
            @RequestParam(required = false) Long schoolYear,
            CurrentUser currentUser) {
        // FE cũ gửi tham số schoolYear; nhận cả hai tên
        Long schoolYearId = schoolYearIdParam != null ? schoolYearIdParam : schoolYear;
        String logPrefix =
                LOG_PREFIX + "|getTeamLeaders|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        TeamLeaderResponse leaders = teacherAssignmentService.getTeamLeaders(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get team leaders success", leaders);
    }

    @PutMapping("/team-leader-assignments")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<TeamLeaderResponse>> assignTeamLeader(
            @Valid @RequestBody TeamLeaderRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|assignTeamLeader|userId=" + currentUser.userId() + "|ageGroupId=" + request.ageGroupId();
        log.info("{}|START", logPrefix);
        TeamLeaderResponse leaders = teacherAssignmentService.assignTeamLeader(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Assign team leader success", leaders);
    }
}
