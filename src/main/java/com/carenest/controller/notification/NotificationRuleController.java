package com.carenest.controller.notification;

import com.carenest.dto.notification.NotificationRuleRequest;
import com.carenest.dto.notification.NotificationRuleResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.notification.NotificationRuleService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.NotificationType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quy tắc thông báo tự động theo sự kiện (PAR-09): chỉ HT. */
@RestController
@RequestMapping("/notification-rules")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('PRINCIPAL')")
public class NotificationRuleController {
    private static final String LOG_PREFIX = "[NotificationRuleController]";

    private final NotificationRuleService ruleService;

    @GetMapping
    public ResponseEntity<ResponseJson<List<NotificationRuleResponse>>> listRules(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listRules|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<NotificationRuleResponse> list = ruleService.listRules(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get notification rules success", list);
    }

    @PutMapping("/{type}")
    public ResponseEntity<ResponseJson<NotificationRuleResponse>> updateRule(
            @PathVariable NotificationType type,
            @RequestBody NotificationRuleRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateRule|userId=" + currentUser.userId() + "|type=" + type;
        log.info("{}|START", logPrefix);
        NotificationRuleResponse rule = ruleService.updateRule(currentUser, type, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update notification rule success", rule);
    }
}
