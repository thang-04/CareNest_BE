package com.carenest.controller.account;

import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.CreateAccountRequest;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.dto.account.MyAccountResponse;
import com.carenest.dto.account.UpdateAccountStatusRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountProvisioningService;
import com.carenest.service.account.AccountService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountProvisioningService accountProvisioningService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<MyAccountResponse>> getMyAccount(CurrentUser currentUser) {
        return ResponseJson.toJsonWithData(
                ApiCode.SUCCESSFUL, "Get my account success", accountService.getMyAccount(currentUser));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseJson<CreateAccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request, CurrentUser currentUser) {
        return ResponseJson.toJsonWithData(
                ApiCode.CREATED,
                "Create account success",
                accountProvisioningService.createAccount(currentUser, request));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseJson<AccountResponse>> updateAccountStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            CurrentUser currentUser) {
        return ResponseJson.toJsonWithData(
                ApiCode.SUCCESSFUL,
                "Update account status success",
                accountProvisioningService.updateAccountStatus(currentUser, userId, request.status()));
    }
}
