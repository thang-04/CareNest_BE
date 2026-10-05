package com.carenest.controller.account;

import com.carenest.dto.account.MyAccountResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<MyAccountResponse>> getMyAccount(CurrentUser currentUser) {
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get my account success",
                accountService.getMyAccount(currentUser));
    }
}
