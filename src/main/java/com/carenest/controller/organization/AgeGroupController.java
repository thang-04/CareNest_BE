package com.carenest.controller.organization;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.AgeGroupRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AgeGroupService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cấu hình trường › Nhóm tuổi. Danh mục nhỏ, không phân trang. */
@RestController
@RequestMapping("/age-groups")
@RequiredArgsConstructor
@Slf4j
public class AgeGroupController {
    private static final String LOG_PREFIX = "[AgeGroupController]";

    private final AgeGroupService ageGroupService;

    // Bếp cần bữa ăn theo nhóm tuổi; danh mục không chứa dữ liệu trẻ
    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'KITCHEN_STAFF')")
    public ResponseEntity<ResponseJson<List<AgeGroupDetailResponse>>> getAgeGroups(
            @RequestParam(required = false) Long schoolYearId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getAgeGroups|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        // Có năm học: nhóm tuổi của năm đó; không có: cả danh mục (bếp, kế hoạch dùng mã chung)
        List<AgeGroupDetailResponse> ageGroups = schoolYearId == null
                ? ageGroupService.getAgeGroups()
                : ageGroupService.getYearAgeGroups(schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get age groups success", ageGroups);
    }

    @PostMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    // Để OpenAPI ghi 201 khớp ApiCode.CREATED
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<AgeGroupDetailResponse>> createAgeGroup(
            @Valid @RequestBody AgeGroupRequest request,
            @RequestParam(required = false) Long schoolYearId,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|createAgeGroup|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        AgeGroupDetailResponse ageGroup = schoolYearId == null
                ? ageGroupService.createAgeGroup(currentUser, request)
                : ageGroupService.createAgeGroup(currentUser, request, schoolYearId);
        log.info("{}|ageGroupId={}|END", logPrefix, ageGroup.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create age group success", ageGroup);
    }

    @PutMapping("/{ageGroupId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<AgeGroupDetailResponse>> updateAgeGroup(
            @PathVariable Long ageGroupId, @Valid @RequestBody AgeGroupRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateAgeGroup|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        AgeGroupDetailResponse ageGroup = ageGroupService.updateAgeGroup(currentUser, ageGroupId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update age group success", ageGroup);
    }

    @DeleteMapping("/{ageGroupId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<Void>> deleteAgeGroup(
            @PathVariable Long ageGroupId, @RequestParam(required = false) Long schoolYearId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteAgeGroup|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId
                + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        // Có năm học: chỉ bỏ nhóm khỏi năm đó (Cần chốt 22/10/2026)
        if (schoolYearId == null) {
            ageGroupService.deleteAgeGroup(currentUser, ageGroupId);
        } else {
            ageGroupService.deleteAgeGroup(currentUser, ageGroupId, schoolYearId);
        }
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete age group success");
    }
}
