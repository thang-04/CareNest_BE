package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.EduConfigRequest;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dữ liệu tham chiếu của form kế hoạch năm (SD-09); danh mục chung, không chứa dữ liệu trẻ. */
@RestController
@RequestMapping("/education-plans")
@RequiredArgsConstructor
@Slf4j
public class EduConfigController {
    private static final String LOG_PREFIX = "[EduConfigController]";

    private final EduConfigService eduConfigService;

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<EduConfigResponse>> getEduConfig(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getEduConfig|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        EduConfigResponse config = eduConfigService.getEduConfig();
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get education plan config success", config);
    }

    @PutMapping("/config")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<EduConfigResponse>> updateEduConfig(
            @Valid @RequestBody EduConfigRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateEduConfig|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        EduConfigResponse config = eduConfigService.updateEduConfig(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update education plan config success", config);
    }
}
