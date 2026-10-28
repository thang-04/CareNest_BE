package com.carenest.controller.organization;

import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.SchoolYearRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.SchoolYearStatus;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cấu hình trường › Năm học: danh sách, tạo/sửa/xóa, kết thúc. Kích hoạt ở YearTransitionController (chuyển trẻ). */
@Validated
@RestController
@RequestMapping("/school-years")
@RequiredArgsConstructor
@Slf4j
public class SchoolYearController {
    private static final String LOG_PREFIX = "[SchoolYearController]";

    private final SchoolYearService schoolYearService;

    // GV cần danh sách năm cho bộ chọn năm học; thao tác ghi kiểm tra Hiệu trưởng ở service (lớp 2)
    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    // Danh mục nhỏ (vài năm học), FE cần cả danh sách cho bộ chọn năm ⇒ không phân trang
    public ResponseEntity<ResponseJson<List<SchoolYearDetailResponse>>> getSchoolYears(
            @RequestParam(required = false) SchoolYearStatus status, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSchoolYears|userId=" + currentUser.userId() + "|status=" + status;
        log.info("{}|START", logPrefix);
        List<SchoolYearDetailResponse> schoolYears = schoolYearService.getSchoolYears(currentUser, status);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get school years success", schoolYears);
    }

    @GetMapping("/{schoolYearId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<SchoolYearDetailResponse>> getSchoolYear(
            @PathVariable Long schoolYearId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        SchoolYearDetailResponse schoolYear = schoolYearService.getSchoolYear(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get school year success", schoolYear);
    }

    @PostMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    // Để OpenAPI ghi 201 khớp ApiCode.CREATED
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<SchoolYearDetailResponse>> createSchoolYear(
            @Valid @RequestBody SchoolYearRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createSchoolYear|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        SchoolYearDetailResponse schoolYear = schoolYearService.createSchoolYear(currentUser, request);
        log.info("{}|schoolYearId={}|END", logPrefix, schoolYear.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create school year success", schoolYear);
    }

    @PutMapping("/{schoolYearId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<SchoolYearDetailResponse>> updateSchoolYear(
            @PathVariable Long schoolYearId, @Valid @RequestBody SchoolYearRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|updateSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        SchoolYearDetailResponse schoolYear = schoolYearService.updateSchoolYear(currentUser, schoolYearId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update school year success", schoolYear);
    }

    @DeleteMapping("/{schoolYearId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<Void>> deleteSchoolYear(
            @PathVariable Long schoolYearId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|deleteSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        schoolYearService.deleteSchoolYear(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete school year success");
    }

    @PostMapping("/{schoolYearId}/close")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<SchoolYearDetailResponse>> closeSchoolYear(
            @PathVariable Long schoolYearId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|closeSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        SchoolYearDetailResponse schoolYear = schoolYearService.closeSchoolYear(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Close school year success", schoolYear);
    }
}
