package com.carenest.dto.notification;

import com.carenest.utils.enums.NotificationType;
import java.util.List;

/**
 * Một loại thông báo tự động (PAR-09). {@code mandatory}: không tắt được; {@code editable}: sửa được câu chữ bằng
 * {@code variables}; {@code customized}: HT đã sửa câu chữ khác mặc định.
 */
public record NotificationRuleResponse(
        NotificationType type,
        String ruleCode,
        String label,
        boolean mandatory,
        boolean editable,
        boolean enabled,
        String titleTemplate,
        String bodyTemplate,
        List<String> variables,
        boolean customized) {}
