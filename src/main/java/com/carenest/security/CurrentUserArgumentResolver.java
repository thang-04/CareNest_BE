package com.carenest.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Cho controller khai báo tham số {@link CurrentUser}; luôn lấy từ token, không bao giờ bind từ request param.
 * Dùng lại kết quả ActiveAccountInterceptor đã tính cho request; lỗi (chưa cấp, bị khóa) do GlobalExceptionHandler trả.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    // Lấy lazy: controller nào không dùng CurrentUser thì không cần bean này (vd. WebMvcTest)
    private final ObjectProvider<CurrentUserService> currentUserService;

    public CurrentUserArgumentResolver(ObjectProvider<CurrentUserService> currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object resolved = webRequest.getAttribute(ActiveAccountInterceptor.CURRENT_USER_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        return resolved instanceof CurrentUser currentUser ? currentUser : currentUserService.getObject().getCurrentUser();
    }
}
