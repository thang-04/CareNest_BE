package com.carenest.controller.health;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.health.HealthTrendAnalysisResponse;
import com.carenest.dto.health.PublishedHealthResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.health.HealthExamService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.service.health.HealthTrendService;
import com.carenest.utils.enums.CareNestRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Hồ sơ sức khỏe phía phụ huynh (PAR-03) và giải thích xu hướng (HLT-05): quyền theo vai trò ở controller. */
@WebMvcTest({HealthMeasurementController.class, HealthTrendController.class})
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class HealthRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private HealthMeasurementService healthMeasurementService;

    @MockitoBean
    private HealthExamService healthExamService;

    @MockitoBean
    private HealthTrendService healthTrendService;

    @Test
    @DisplayName("PAR-03: phụ huynh gọi kết quả đã công bố ⇒ 200 (phạm vi con kiểm ở service)")
    void parentReadsPublishedHealth() throws Exception {
        CurrentUser parent = authenticate(CareNestRole.PARENT);
        given(healthMeasurementService.getPublishedHealth(parent, 5L))
                .willReturn(new PublishedHealthResponse(5L, List.of(), List.of(), null, null, null));

        mockMvc.perform(get("/api/children/{childId}/health/published", 5).with(token(CareNestRole.PARENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desc").value("Get published health success"))
                .andExpect(jsonPath("$.data.childId").value(5));
    }

    @Test
    @DisplayName("HLT-01: phụ huynh không gọi được hồ sơ sức khỏe đầy đủ của nhân sự ⇒ 403")
    void parentCannotReadStaffHealthRecord() throws Exception {
        authenticate(CareNestRole.PARENT);

        mockMvc.perform(get("/api/children/{childId}/health", 5).with(token(CareNestRole.PARENT)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(healthMeasurementService);
    }

    @Test
    @DisplayName("HLT-05: GV phân tích xu hướng ⇒ 200 trả bản nháp")
    void teacherAnalyzesTrend() throws Exception {
        CurrentUser teacher = authenticate(CareNestRole.TEACHER);
        given(healthTrendService.analyze(teacher, 5L)).willReturn(draft());

        mockMvc.perform(post("/api/children/{childId}/health/trend-analysis", 5).with(token(CareNestRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desc").value("Analyze health trend success"))
                .andExpect(jsonPath("$.data.status").value("OK"))
                .andExpect(jsonPath("$.data.draft").value(true));
    }

    @Test
    @DisplayName("HLT-05: duyệt không gửi body ⇒ giữ nguyên câu chữ AI (request null)")
    void approveWithoutBody() throws Exception {
        CurrentUser teacher = authenticate(CareNestRole.TEACHER);
        given(healthTrendService.approve(eq(teacher), eq(5L), eq(90L), isNull()))
                .willReturn(draft());

        mockMvc.perform(post("/api/children/{childId}/health/trend-analysis/{id}/approve", 5, 90)
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desc").value("Approve health trend success"));

        verify(healthTrendService).approve(eq(teacher), eq(5L), eq(90L), isNull());
    }

    @Test
    @DisplayName("AI-07: phụ huynh không gọi được phân tích xu hướng ⇒ 403, không gọi AI")
    void parentCannotAnalyzeTrend() throws Exception {
        authenticate(CareNestRole.PARENT);

        mockMvc.perform(post("/api/children/{childId}/health/trend-analysis", 5).with(token(CareNestRole.PARENT)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(healthTrendService);
    }

    @Test
    @DisplayName("AUTH-04: chưa đăng nhập ⇒ 401")
    void anonymousRejected() throws Exception {
        mockMvc.perform(get("/api/children/{childId}/health/published", 5)).andExpect(status().isUnauthorized());

        verifyNoInteractions(healthMeasurementService, healthTrendService, healthExamService);
    }

    private static HealthTrendAnalysisResponse draft() {
        return new HealthTrendAnalysisResponse(
                90L,
                HealthTrendAnalysisResponse.OK,
                true,
                Instant.parse("2026-11-10T03:00:00Z"),
                "Chiều cao, cân nặng tăng đều.",
                null,
                List.of(),
                "AI",
                "model-x",
                null,
                null,
                null);
    }

    private CurrentUser authenticate(CareNestRole role) {
        CurrentUser user = TestPermissions.userWithDefaults(4L, role);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
