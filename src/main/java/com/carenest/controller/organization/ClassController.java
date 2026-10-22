package com.carenest.controller.organization;

import com.carenest.dto.organization.ClassRequest;
import com.carenest.dto.organization.ClassResponse;
import com.carenest.dto.organization.CopyClassesRequest;
import com.carenest.dto.organization.CopyClassesResponse;
import com.carenest.dto.organization.SchoolStructureResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.ClassManagementService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cấu hình trường › Nhóm tuổi & lớp: lớp theo năm học, cấu trúc năm học, sao chép lớp. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClassController {
    private static final String LOG_PREFIX = "[ClassController]";

    private final ClassManagementService classManagementService;

    // GV xem lớp mình dạy; PHT xem điểm trường mình; service lọc theo phạm vi
    @GetMapping("/school-structure")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<SchoolStructureResponse>> getSchoolStructure(
            @RequestParam(name = "schoolYearId", required = false) Long schoolYearIdParam,
            @RequestParam(required = false) Long schoolYear,
            @RequestParam(required = false) Long campusId,
            CurrentUser currentUser) {
        // FE cũ gửi tham số schoolYear; nhận cả hai tên
        Long schoolYearId = schoolYearIdParam != null ? schoolYearIdParam : schoolYear;
        String logPrefix = LOG_PREFIX + "|getSchoolStructure|userId=" + currentUser.userId() + "|schoolYearId="
                + schoolYearId + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        SchoolStructureResponse structure =
                classManagementService.getSchoolStructure(currentUser, schoolYearId, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get school structure success", structure);
    }

    @PostMapping("/classes")
    @PreAuthorize("hasRole('PRINCIPAL')")
    // Để OpenAPI ghi 201 khớp ApiCode.CREATED
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<ClassResponse>> createClass(
            @Valid @RequestBody ClassRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createClass|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        ClassResponse classResponse = classManagementService.createClass(currentUser, request);
        log.info("{}|classId={}|END", logPrefix, classResponse.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create class success", classResponse);
    }

    @PutMapping("/classes/{classId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<ClassResponse>> updateClass(
            @PathVariable Long classId, @Valid @RequestBody ClassRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateClass|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        ClassResponse classResponse = classManagementService.updateClass(currentUser, classId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update class success", classResponse);
    }

    @DeleteMapping("/classes/{classId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<Void>> deleteClass(@PathVariable Long classId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteClass|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        classManagementService.deleteClass(currentUser, classId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete class success");
    }

    @PostMapping("/school-years/{schoolYearId}/classes/copy")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<CopyClassesResponse>> copyClasses(
            @PathVariable Long schoolYearId, @Valid @RequestBody CopyClassesRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|copyClasses|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        CopyClassesResponse result = classManagementService.copyClasses(currentUser, schoolYearId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Copy classes success", result);
    }
}
