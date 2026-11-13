package com.carenest.controller.account;

import com.carenest.dto.account.ParentActivationRequest;
import com.carenest.dto.account.ParentActivationResponse;
import com.carenest.service.account.ParentActivationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ADR-0024: phụ huynh chưa có mật khẩu nên chưa có token; App gọi trực tiếp, không cookie/CSRF
@RestController
@RequestMapping("/parent-activation")
@PreAuthorize("permitAll()")
@RequiredArgsConstructor
@Slf4j
public class ParentActivationController {
    private static final String LOG_PREFIX = "[ParentActivationController]";

    private final ParentActivationService parentActivationService;

    @PostMapping
    public ResponseEntity<ResponseJson<ParentActivationResponse>> activateParentAccount(
            @Valid @RequestBody ParentActivationRequest request) {
        String logPrefix = LOG_PREFIX + "|activateParentAccount";
        log.info("{}|START", logPrefix);
        ParentActivationResponse activated = parentActivationService.activate(request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Activate parent account success", activated);
    }
}
