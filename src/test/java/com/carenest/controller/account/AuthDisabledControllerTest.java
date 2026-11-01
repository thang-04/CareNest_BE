package com.carenest.controller.account;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.account.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class, properties = "carenest.auth.enabled=false")
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuthDisabledControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService service;

    @MockitoBean
    private CurrentUserService currentUsers;

    @Test
    void disabledGatewayDoesNotPublishAnonymousRoutesOrCookies() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("XSRF-TOKEN"));
        mvc.perform(post("/api/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("CARENEST_SESSION"));
        verifyNoInteractions(service);
    }
}
