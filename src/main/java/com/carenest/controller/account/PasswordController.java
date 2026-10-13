package com.carenest.controller.account;

import com.carenest.dto.account.AuthRequests;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AuthService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class PasswordController {
    private final AuthService service;

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<Void>> changePassword(
            CurrentUser currentUser, @Valid @RequestBody AuthRequests.ChangePassword request) {
        service.changePassword(currentUser, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ResponseJson.of(ApiCode.SUCCESSFUL, "Password updated; sign in again", null));
    }
}
