package com.carenest.controller.health;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.health.HealthGrowthReportResponse;
import com.carenest.dto.health.HealthGrowthReportResponse.HealthGrowthCounts;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.health.HealthCheckService;
import com.carenest.service.health.HealthGrowthReportService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** API báo cáo tăng trưởng (HLT-20): tham số tháng, vai trò. Phạm vi điểm trường kiểm ở service (test tích hợp). */
@WebMvcTest(HealthCheckController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class HealthGrowthReportControllerTest {

    private static final HealthGrowthCounts ZERO = new HealthGrowthCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private HealthCheckService healthCheckService;

    @MockitoBean
    private HealthGrowthReportService healthGrowthReportService;

    @Test
    @DisplayName("HLT-20: HT gọi báo cáo tháng ⇒ 200, chuyển tháng yyyy-MM và điểm trường xuống service")
    void principalGetsReport() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(healthGrowthReportService.getGrowthReport(eq(principal), eq(YearMonth.of(2026, 9)), eq(3L)))
                .willReturn(new HealthGrowthReportResponse("2026-09", List.of(), ZERO, ZERO, ZERO));

        mockMvc.perform(get("/api/health-checks/growth-report")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("month", "2026-09")
                        .param("campusId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.desc").value("Get growth report success"))
                .andExpect(jsonPath("$.data.month").value("2026-09"))
                .andExpect(jsonPath("$.data.total.enrolled").value(0));

        verify(healthGrowthReportService).getGrowthReport(principal, YearMonth.of(2026, 9), 3L);
    }

    @ParameterizedTest(name = "month={0}")
    @ValueSource(strings = {"", "2026-9", "2026-13", "09-2026", "2026-09-01"})
    @DisplayName("HLT-20: tháng thiếu hoặc sai dạng yyyy-MM ⇒ 400 mã 9002, service không chạy")
    void invalidMonthIsRejected(String month) throws Exception {
        authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(get("/api/health-checks/growth-report")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("month", month))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.MALFORMED_REQUEST.getErrorCode()));

        verifyNoInteractions(healthGrowthReportService);
    }

    @Test
    @DisplayName("HLT-20: không có tham số tháng ⇒ 400 mã 9002")
    void missingMonthIsRejected() throws Exception {
        authenticate(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(get("/api/health-checks/growth-report").with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.MALFORMED_REQUEST.getErrorCode()));

        verifyNoInteractions(healthGrowthReportService);
    }

    @Test
    @DisplayName("HLT-20: giáo viên không xem báo cáo ⇒ 403, service không chạy")
    void teacherIsForbidden() throws Exception {
        authenticate(CareNestRole.TEACHER);

        mockMvc.perform(get("/api/health-checks/growth-report")
                        .with(token(CareNestRole.TEACHER))
                        .param("month", "2026-09"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.FORBIDDEN.getErrorCode()));

        verifyNoInteractions(healthGrowthReportService);
    }

    private CurrentUser authenticate(CareNestRole role) {
        CurrentUser user = TestPermissions.userWithDefaults(2L, role);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
