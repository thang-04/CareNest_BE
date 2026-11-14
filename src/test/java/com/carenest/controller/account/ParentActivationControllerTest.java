package com.carenest.controller.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.account.ParentActivationResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.account.ParentActivationService;
import com.carenest.utils.ApiCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ParentActivationController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ParentActivationControllerTest {
    private static final String BODY =
            "{\"phone\":\"0912345678\",\"code\":\"123456\",\"newPassword\":\"Pass-word-1\",\"confirmPassword\":\"Pass-word-1\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ParentActivationService service;

    @MockitoBean
    private CurrentUserService currentUsers;

    @Test
    @DisplayName("AUTH-18: kích hoạt không cần token, trả tên đăng nhập")
    void activatesWithoutToken() throws Exception {
        given(service.activate(any())).willReturn(new ParentActivationResponse("0912345678"));

        mvc.perform(post("/api/parent-activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("0912345678"));
    }

    @Test
    @DisplayName("AUTH-18: mã sai trả lỗi chung 1006")
    void invalidCodeReturnsGenericError() throws Exception {
        given(service.activate(any())).willThrow(GlobalException.of(ApiCode.OTP_INVALID_OR_EXPIRED));

        mvc.perform(post("/api/parent-activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(1006));
    }

    @Test
    @DisplayName("AUTH-18: mã không đủ 6 số ⇒ 400, không gọi service")
    void malformedCodeRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/parent-activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("123456", "12ab")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
