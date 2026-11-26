package com.carenest.controller.organization;

import com.carenest.dto.organization.AppearanceRequest;
import com.carenest.dto.organization.AppearanceResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.SchoolAppearanceService;
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

/** Tranh nền giao diện chung của trường: mọi người dùng đăng nhập đọc được, Hiệu trưởng/Phó hiệu trưởng được đổi. */
@RestController
@RequestMapping("/school/appearance")
@RequiredArgsConstructor
@Slf4j
public class SchoolAppearanceController {
    private static final String LOG_PREFIX = "[SchoolAppearanceController]";

    private final SchoolAppearanceService schoolAppearanceService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<AppearanceResponse>> getAppearance(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getAppearance|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AppearanceResponse appearance = schoolAppearanceService.getAppearance();
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get appearance success", appearance);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<AppearanceResponse>> saveAppearance(
            @Valid @RequestBody AppearanceRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveAppearance|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AppearanceResponse appearance = schoolAppearanceService.saveAppearance(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save appearance success", appearance);
    }
}
