package com.carenest.dto.notification;

import java.util.List;

/** Thông báo mới nhất trước và số thông báo chưa đọc (badge của App). */
public record NotificationListResponse(List<NotificationResponse> items, long unreadCount) {}
