package com.carenest.controller.account;

import com.carenest.dto.account.AuthRequests;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AuthService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/password")
@RequiredArgsConstructor
@Slf4j
public class PasswordController {
    private static final String LOG_PREFIX = "[PasswordController]";

    private final AuthService service;

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<Void>> changePassword(
            CurrentUser currentUser, @Valid @RequestBody AuthRequests.ChangePassword request) {
        String logPrefix = LOG_PREFIX + "|changePassword|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        service.changePassword(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ResponseJson.of(ApiCode.SUCCESSFUL, "Password updated; sign in again", null));
    }
}
