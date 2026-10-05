package com.carenest.controller.account;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.exception.GlobalException;
import com.carenest.security.CareNestRole;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.account.AccountService;
import com.carenest.utils.ApiCode;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        AccountService.class})
class AccountControllerTest {

    private static final String MY_ACCOUNT = "/api/accounts/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    void shouldReturnProfileAndRolesOfCurrentUser() throws Exception {
        given(currentUserService.getCurrentUser()).willReturn(new CurrentUser(10L, "kc-1", "teacher.a",
                "Giáo viên Demo", null, Set.of(CareNestRole.TEACHER, CareNestRole.PARENT)));

        mockMvc.perform(get(MY_ACCOUNT).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.username").value("teacher.a"))
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.roles[1]").value("PARENT"));
    }

    @Test
    void shouldReturn403WhenAccountIsNotProvisioned() throws Exception {
        given(currentUserService.getCurrentUser())
                .willThrow(new GlobalException(ApiCode.FORBIDDEN, "Account is not provisioned"));

        mockMvc.perform(get(MY_ACCOUNT).with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.desc").value("Account is not provisioned"));
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(MY_ACCOUNT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
