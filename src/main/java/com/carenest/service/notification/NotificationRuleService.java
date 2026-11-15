package com.carenest.service.notification;

import com.carenest.dto.notification.NotificationRuleRequest;
import com.carenest.dto.notification.NotificationRuleResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.NotificationType;
import java.util.List;

/** Quy tắc thông báo tự động theo sự kiện (PAR-09), chỉ HT quản lý. */
public interface NotificationRuleService {

    List<NotificationRuleResponse> listRules(CurrentUser user);

    NotificationRuleResponse updateRule(CurrentUser user, NotificationType type, NotificationRuleRequest request);
}
