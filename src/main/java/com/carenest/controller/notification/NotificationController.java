package com.carenest.controller.notification;

import com.carenest.dto.notification.DeviceTokenRequest;
import com.carenest.dto.notification.NotificationListResponse;
import com.carenest.dto.notification.NotificationResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thông báo của người dùng đang đăng nhập (App phụ huynh) và thiết bị nhận thông báo đẩy. */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {
    private static final String LOG_PREFIX = "[NotificationController]";

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<NotificationListResponse>> listMyNotifications(
            @RequestParam(defaultValue = "50") int limit, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listMyNotifications|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        NotificationListResponse list = notificationService.listMyNotifications(currentUser, limit);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get notifications success", list);
    }

    @PostMapping("/notifications/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<NotificationResponse>> markRead(
            @PathVariable Long notificationId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|markRead|userId=" + currentUser.userId() + "|notificationId=" + notificationId;
        log.info("{}|START", logPrefix);
        NotificationResponse notification = notificationService.markRead(currentUser, notificationId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Mark notification read success", notification);
    }

    @PostMapping("/notifications/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<NotificationListResponse>> markAllRead(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|markAllRead|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        NotificationListResponse list = notificationService.markAllRead(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Mark all notifications read success", list);
    }

    @PutMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<Void>> registerDevice(
            @RequestBody DeviceTokenRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|registerDevice|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        notificationService.registerDevice(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Register device success");
    }

    @DeleteMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<Void>> unregisterDevice(
            @RequestBody DeviceTokenRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|unregisterDevice|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        notificationService.unregisterDevice(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Unregister device success");
    }
}
