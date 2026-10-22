package com.carenest.controller.organization;

import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.ClassroomResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Chỉ đọc, phục vụ form cấp tài khoản/phân công; lọc theo phạm vi người xem ở service
@RestController
@RequiredArgsConstructor
@Slf4j
public class SchoolStructureController {
    private static final String LOG_PREFIX = "[SchoolStructureController]";

    private final SchoolStructureService schoolStructureService;

    @GetMapping("/campuses")
    @PreAuthorize("@permissionGuard.hasAny('ACCOUNT_MANAGE', 'STAFF_ASSIGNMENT_MANAGE')")
    public ResponseEntity<ResponseJson<List<CampusResponse>>> listCampuses(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listCampuses|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<CampusResponse> campuses = schoolStructureService.listCampuses(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List campuses success", campuses);
    }

    @GetMapping("/classrooms")
    @PreAuthorize("@permissionGuard.hasAny('ACCOUNT_MANAGE', 'STAFF_ASSIGNMENT_MANAGE')")
    public ResponseEntity<ResponseJson<List<ClassroomResponse>>> listClassrooms(
            @RequestParam Long campusId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listClassrooms|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<ClassroomResponse> classrooms = schoolStructureService.listCurrentClassrooms(currentUser, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List classrooms success", classrooms);
    }
}
