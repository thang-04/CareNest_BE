package com.carenest.service.notification.impl;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.notification.NotificationRuleRequest;
import com.carenest.dto.notification.NotificationRuleResponse;
import com.carenest.entity.notification.NotificationRule;
import com.carenest.exception.GlobalException;
import com.carenest.repository.notification.NotificationRuleRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.notification.NotificationRuleService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.NotificationType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationRuleServiceImpl implements NotificationRuleService {
    private static final String LOG_PREFIX = "[NotificationRuleServiceImpl]";

    private final NotificationRuleRepository ruleRepository;
    private final StaffAccessPolicy staffAccessPolicy;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationRuleResponse> listRules(CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|listRules|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            Map<NotificationType, NotificationRule> saved = ruleRepository.findAll().stream()
                    .collect(Collectors.toMap(NotificationRule::getType, Function.identity()));
            // Loại sửa được lên trước, theo thứ tự khai báo trong danh mục
            List<NotificationRuleResponse> list = Arrays.stream(NotificationType.values())
                    .map(t -> toView(NotificationRuleCatalog.of(t), saved.get(t)))
                    .sorted((a, b) -> Boolean.compare(b.editable(), a.editable()))
                    .toList();
            log.info("{}|count={}|END", logPrefix, list.size());
            return list;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public NotificationRuleResponse updateRule(
            CurrentUser user, NotificationType type, NotificationRuleRequest request) {
        String logPrefix = LOG_PREFIX + "|updateRule|userId=" + user.userId() + "|type=" + type;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            NotificationRuleCatalog.Rule rule = NotificationRuleCatalog.of(type);
            if (!rule.editable()) {
                throw GlobalException.of(ApiCode.NOTIFICATION_RULE_READ_ONLY);
            }
            boolean enabled = request == null || request.enabled() == null || request.enabled();
            if (!enabled && rule.mandatory()) {
                throw GlobalException.of(ApiCode.NOTIFICATION_RULE_MANDATORY);
            }
            String title = blankToNull(request == null ? null : request.titleTemplate());
            String body = blankToNull(request == null ? null : request.bodyTemplate());
            checkTemplates(rule, title, body);
            NotificationRule saved = ruleRepository.findById(type).orElseGet(() -> NotificationRule.of(type));
            saved.update(enabled, title, body, user.userId(), clock.instant());
            ruleRepository.save(saved);
            log.info("{}|enabled={}|END", logPrefix, enabled);
            return toView(rule, saved);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void checkPrincipal(CurrentUser user) {
        if (!staffAccessPolicy.managesWholeSchool(user)) {
            throw GlobalException.of(ApiCode.FORBIDDEN);
        }
    }

    /** Câu chữ chỉ dùng biến được phép (FR-019); độ dài theo cột lưu. */
    private static void checkTemplates(NotificationRuleCatalog.Rule rule, String title, String body) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        List<String> unknown = new ArrayList<>(NotificationRuleCatalog.unknownVariables(title, rule.variables()));
        unknown.addAll(NotificationRuleCatalog.unknownVariables(body, rule.variables()));
        if (!unknown.isEmpty()) {
            throw GlobalException.withFieldErrors(
                    ApiCode.NOTIFICATION_RULE_UNKNOWN_VARIABLE,
                    unknown.stream()
                            .distinct()
                            .map(v -> new FieldErrorResponse("template", "Biến không được phép: {" + v + "}"))
                            .toList());
        }
        if (title != null && title.length() > 150) {
            errors.add(new FieldErrorResponse("titleTemplate", "Tiêu đề tối đa 150 ký tự"));
        }
        if (body != null && body.length() > 500) {
            errors.add(new FieldErrorResponse("bodyTemplate", "Nội dung tối đa 500 ký tự"));
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
    }

    private static NotificationRuleResponse toView(NotificationRuleCatalog.Rule rule, NotificationRule saved) {
        String title =
                saved != null && saved.getTitleTemplate() != null ? saved.getTitleTemplate() : rule.defaultTitle();
        String body = saved != null && saved.getBodyTemplate() != null ? saved.getBodyTemplate() : rule.defaultBody();
        return new NotificationRuleResponse(
                rule.type(),
                rule.ruleCode(),
                rule.label(),
                rule.mandatory(),
                rule.editable(),
                rule.mandatory() || saved == null || saved.isEnabled(),
                title,
                body,
                rule.variables(),
                saved != null && (saved.getTitleTemplate() != null || saved.getBodyTemplate() != null));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
