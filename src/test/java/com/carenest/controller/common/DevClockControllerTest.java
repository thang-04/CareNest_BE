package com.carenest.controller.common;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.ClockConfig;
import com.carenest.config.DevClockFilter;
import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.utils.enums.CareNestRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DevClockController.class)
@Import({
    WebMvcConfig.class,
    SecurityConfig.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    ClockConfig.class
})
@TestPropertySource(properties = "carenest.dev.clock.enabled=true")
class DevClockControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CurrentUserService users;

    @BeforeEach
    void signedInTeacher() {
        given(users.getCurrentUser()).willReturn(TestPermissions.userWithDefaults(30L, CareNestRole.TEACHER));
    }

    @Test
    @DisplayName("Có header X-Dev-Now: server tính nghiệp vụ theo giờ giả lập")
    void returnsSimulatedTime() throws Exception {
        mvc.perform(get("/api/dev/clock")
                        .header(DevClockFilter.HEADER, "2026-12-01T08:30:00+07:00")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simulated").value(true))
                .andExpect(jsonPath("$.data.effectiveNow").value("2026-12-01T08:30:00+07:00"));
    }

    @Test
    @DisplayName("Không có header: dùng giờ thật")
    void returnsRealTimeWithoutHeader() throws Exception {
        mvc.perform(get("/api/dev/clock").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simulated").value(false));
    }

    @Test
    @DisplayName("Chưa đăng nhập: 401")
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/dev/clock")).andExpect(status().isUnauthorized());
    }
}
