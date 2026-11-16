package com.carenest.dto.notification;

/** HT bật/tắt và sửa câu chữ; câu chữ null hoặc trống = dùng mặc định. */
public record NotificationRuleRequest(Boolean enabled, String titleTemplate, String bodyTemplate) {}
