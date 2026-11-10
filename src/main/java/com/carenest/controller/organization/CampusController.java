package com.carenest.controller.organization;

import com.carenest.dto.organization.CampusDetailResponse;
import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.CampusStatusRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.CampusService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cấu hình trường › Điểm trường. Trường chỉ có vài điểm trường nên danh sách không phân trang. */
@RestController
@RequestMapping("/campuses")
@RequiredArgsConstructor
@Slf4j
public class CampusController {
    private static final String LOG_PREFIX = "[CampusController]";

    private final CampusService campusService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<CampusResponse>>> getCampuses(
            @RequestParam(name = "schoolYearId", required = false) Long schoolYearIdParam,
            @RequestParam(required = false) Long schoolYear,
            CurrentUser currentUser) {
        // FE cũ gửi tham số schoolYear; nhận cả hai tên
        Long schoolYearId = schoolYearIdParam != null ? schoolYearIdParam : schoolYear;
        String logPrefix = LOG_PREFIX + "|getCampuses|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        List<CampusResponse> campuses = campusService.getCampuses(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get campuses success", campuses);
    }

    @GetMapping("/{campusId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<CampusDetailResponse>> getCampusDetail(
            @PathVariable Long campusId,
            @RequestParam(name = "schoolYearId", required = false) Long schoolYearIdParam,
            @RequestParam(required = false) Long schoolYear,
            CurrentUser currentUser) {
        // FE cũ gửi tham số schoolYear; nhận cả hai tên
        Long schoolYearId = schoolYearIdParam != null ? schoolYearIdParam : schoolYear;
        String logPrefix = LOG_PREFIX + "|getCampusDetail|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        CampusDetailResponse campus = campusService.getCampusDetail(currentUser, campusId, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get campus success", campus);
    }

    @PostMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    // Để OpenAPI ghi 201 khớp ApiCode.CREATED
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<CampusResponse>> createCampus(
            @Valid @RequestBody CampusRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createCampus|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        CampusResponse campus = campusService.createCampus(currentUser, request);
        log.info("{}|campusId={}|END", logPrefix, campus.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create campus success", campus);
    }

    @PutMapping("/{campusId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<CampusResponse>> updateCampus(
            @PathVariable Long campusId, @Valid @RequestBody CampusRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateCampus|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        CampusResponse campus = campusService.updateCampus(currentUser, campusId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update campus success", campus);
    }

    @DeleteMapping("/{campusId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<Void>> deleteCampus(@PathVariable Long campusId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteCampus|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        campusService.deleteCampus(currentUser, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete campus success");
    }

    @PatchMapping("/{campusId}/status")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<CampusResponse>> changeCampusStatus(
            @PathVariable Long campusId, @Valid @RequestBody CampusStatusRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|changeCampusStatus|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        CampusResponse campus = campusService.changeCampusStatus(currentUser, campusId, request.status());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Change campus status success", campus);
    }
}
