package com.carenest.controller.account;

import com.carenest.dto.account.AuthResponses;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AuthService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUTH-28: Web hỏi định kỳ phiên của mình. Phiên bị thay thế ⇒ bộ lọc JWT trả 401 mã 1016 trước khi tới đây; còn hiệu
 * lực ⇒ trả lượt đăng nhập ở trình duyệt khác đang chờ phiên này quyết định (nếu có).
 */
@RestController
@RequestMapping("/me/session")
@Slf4j
public class SessionController {
    private static final String LOG_PREFIX = "[SessionController]";

    private final AuthService authService;

    public SessionController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<AuthResponses.SessionStatus>> checkSession(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|checkSession|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AuthResponses.SessionStatus status = authService.sessionStatus(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ResponseJson.of(ApiCode.SUCCESSFUL, "Session active", status));
    }

    @PostMapping("/takeovers/{takeoverId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<Void>> rejectTakeover(
            CurrentUser currentUser, @PathVariable String takeoverId) {
        String logPrefix = LOG_PREFIX + "|rejectTakeover|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        authService.rejectTakeover(currentUser, takeoverId);
        log.info("{}|END", logPrefix);
        return ResponseEntity.ok().body(ResponseJson.of(ApiCode.SUCCESSFUL, "Kept session here", null));
    }
}
