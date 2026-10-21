package com.carenest.security;

import com.carenest.utils.Constants.Security;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Mọi request API đã xác thực phải thuộc tài khoản CareNest đang ACTIVE, kể cả endpoint không khai báo tham số
 * {@link CurrentUser} — không phụ thuộc quy ước viết controller. Kết quả lưu vào request để resolver dùng lại
 * (không query lần hai). Lỗi ném trong DispatcherServlet nên GlobalExceptionHandler trả ResponseJson.
 */
public class ActiveAccountInterceptor implements HandlerInterceptor {

    private final ObjectProvider<CurrentUserService> currentUserService;

    public ActiveAccountInterceptor(ObjectProvider<CurrentUserService> currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean authenticatedWithToken =
                SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken;
        if (handler instanceof HandlerMethod && authenticatedWithToken) {
            request.setAttribute(
                    Security.CURRENT_USER_ATTRIBUTE,
                    currentUserService.getObject().getCurrentUser());
        }
        return true;
    }
}
