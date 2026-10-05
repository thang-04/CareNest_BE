package com.carenest.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Cho controller khai báo tham số {@link CurrentUser}. Lỗi (chưa cấp tài khoản, bị khóa) ném ra trong
 * DispatcherServlet nên GlobalExceptionHandler trả ResponseJson như lỗi nghiệp vụ khác.
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
        return currentUserService.getObject().getCurrentUser();
    }
}
