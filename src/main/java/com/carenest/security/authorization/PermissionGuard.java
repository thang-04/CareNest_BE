package com.carenest.security.authorization;

import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.utils.Constants.Security;
import com.carenest.utils.enums.Permission;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Lớp 1 của kiểm tra quyền, dùng trong {@code @PreAuthorize("@permissionGuard.has('CODE')")} (ADR-0017).
 * Đọc quyền hiệu lực đã tính một lần cho request (ActiveAccountInterceptor); phạm vi campus/lớp/trẻ vẫn do
 * policy ở service kiểm (lớp 2). Code lạ ⇒ từ chối (fail closed).
 */
@Slf4j
public class PermissionGuard {

    private final ObjectProvider<CurrentUserService> currentUserService;

    public PermissionGuard(ObjectProvider<CurrentUserService> currentUserService) {
        this.currentUserService = currentUserService;
    }

    public boolean has(String code) {
        return hasAny(code);
    }

    public boolean hasAny(String... codes) {
        CurrentUser currentUser = currentUser();
        return Arrays.stream(codes).anyMatch(code -> {
            Optional<Permission> permission = Permission.fromCode(code);
            if (permission.isEmpty()) {
                log.error("[PermissionGuard]|unknownPermission={}", code);
                return false;
            }
            return currentUser.hasPermission(permission.get());
        });
    }

    private CurrentUser currentUser() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object resolved = attributes == null
                ? null
                : attributes.getAttribute(Security.CURRENT_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return resolved instanceof CurrentUser user
                ? user
                : currentUserService.getObject().getCurrentUser();
    }
}
