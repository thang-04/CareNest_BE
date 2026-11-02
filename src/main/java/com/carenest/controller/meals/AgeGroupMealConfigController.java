package com.carenest.controller.meals;

import com.carenest.dto.meals.AgeGroupMealConfigBatchRequest;
import com.carenest.dto.meals.AgeGroupMealConfigRequest;
import com.carenest.dto.meals.AgeGroupMealConfigResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.AgeGroupMealConfigService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Danh mục & thiết lập › Cấu hình nhóm tuổi: giá suất, chuẩn dinh dưỡng có ngày hiệu lực (NUT-17, NUT-19). */
@RestController
@RequestMapping("/nutrition/age-group-configs")
@RequiredArgsConstructor
@Slf4j
public class AgeGroupMealConfigController {
    private static final String LOG_PREFIX = "[AgeGroupMealConfigController]";

    private final AgeGroupMealConfigService configService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<AgeGroupMealConfigResponse>>> getEffectiveConfigs(
            @RequestParam(required = false) LocalDate date, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getEffectiveConfigs|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<AgeGroupMealConfigResponse> configs = configService.getEffectiveConfigs(date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get age group meal configs success", configs);
    }

    @GetMapping("/{ageGroupId}/versions")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<AgeGroupMealConfigResponse>>> getConfigVersions(
            @PathVariable Long ageGroupId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getConfigVersions|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        List<AgeGroupMealConfigResponse> versions = configService.getConfigVersions(ageGroupId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get age group meal config versions success", versions);
    }

    @PostMapping
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<AgeGroupMealConfigResponse>> createConfig(
            @Valid @RequestBody AgeGroupMealConfigRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createConfig|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AgeGroupMealConfigResponse config = configService.createConfig(currentUser, request);
        log.info("{}|configId={}|END", logPrefix, config.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create age group meal config success", config);
    }

    @PostMapping("/batch")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    public ResponseEntity<ResponseJson<List<AgeGroupMealConfigResponse>>> saveConfigs(
            @Valid @RequestBody AgeGroupMealConfigBatchRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveConfigs|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<AgeGroupMealConfigResponse> configs = configService.saveConfigs(currentUser, request.items());
        log.info("{}|items={}|END", logPrefix, configs.size());
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save age group meal configs success", configs);
    }

    @PutMapping("/{configId}")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    public ResponseEntity<ResponseJson<AgeGroupMealConfigResponse>> updateConfig(
            @PathVariable Long configId,
            @Valid @RequestBody AgeGroupMealConfigRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateConfig|userId=" + currentUser.userId() + "|configId=" + configId;
        log.info("{}|START", logPrefix);
        AgeGroupMealConfigResponse config = configService.updateConfig(currentUser, configId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update age group meal config success", config);
    }
}
