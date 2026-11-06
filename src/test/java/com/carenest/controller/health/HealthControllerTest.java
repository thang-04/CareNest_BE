package com.carenest.controller.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.health.HealthExamRequest;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.health.HealthExamService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.utils.enums.CareNestRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Client cũ còn gửi ngày đo/ngày khám, vòng đầu của kỳ cân đo (bỏ 2026-11-04): API vẫn nhận, bỏ qua các trường đó. */
@WebMvcTest({HealthMeasurementController.class, HealthExamController.class})
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class HealthControllerTest {

    private static final String LEGACY_MEASUREMENT_JSON = """
            {
              "campaignId": 7,
              "date": "2026-11-01",
              "heightCm": 98.5,
              "weightKg": 15.2,
              "headCircumferenceCm": 48.5,
              "note": "Cân đo tại lớp (giả)",
              "reason": "Đo lại (giả)"
            }
            """;

    private static final String LEGACY_EXAM_JSON = """
            {
              "date": "2026-11-01",
              "heightCm": 98.5,
              "weightKg": 15.2,
              "headCircumferenceCm": 48.5,
              "exam": null,
              "conclude": false,
              "reason": "",
              "conclusionMode": null
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private HealthMeasurementService healthMeasurementService;

    @MockitoBean
    private HealthExamService healthExamService;

    @Test
    @DisplayName("HLT-01: tạo số đo kèm date, headCircumferenceCm của client cũ ⇒ 201, service chỉ nhận số đo của kỳ")
    void createMeasurementIgnoresLegacyFields() throws Exception {
        CurrentUser teacher = authenticate();

        mockMvc.perform(post("/api/children/{childId}/health/measurements", 5)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LEGACY_MEASUREMENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.desc").value("Create measurement success"));

        ArgumentCaptor<HealthMeasurementRequest> request = ArgumentCaptor.forClass(HealthMeasurementRequest.class);
        verify(healthMeasurementService).createMeasurement(eq(teacher), eq(5L), request.capture());
        assertMeasurement(request.getValue());
    }

    @Test
    @DisplayName(
            "HLT-13: sửa số đo kèm date, headCircumferenceCm của client cũ ⇒ 200, ngày đo và vòng đầu không tới service")
    void updateMeasurementIgnoresLegacyFields() throws Exception {
        CurrentUser teacher = authenticate();

        mockMvc.perform(put("/api/children/{childId}/health/measurements/{measurementId}", 5, 9)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LEGACY_MEASUREMENT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desc").value("Update measurement success"));

        ArgumentCaptor<HealthMeasurementRequest> request = ArgumentCaptor.forClass(HealthMeasurementRequest.class);
        verify(healthMeasurementService).updateMeasurement(eq(teacher), eq(5L), eq(9L), request.capture());
        assertMeasurement(request.getValue());
    }

    @Test
    @DisplayName("HLT-08: lưu phiếu khám kèm date của client cũ ⇒ 200, ngày bị bỏ qua, vòng đầu của phiếu vẫn nhận")
    void saveExamIgnoresLegacyDate() throws Exception {
        CurrentUser teacher = authenticate();

        mockMvc.perform(put("/api/health-checks/{campaignId}/children/{childId}/exam", 3, 5)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LEGACY_EXAM_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desc").value("Save health exam success"));

        ArgumentCaptor<HealthExamRequest> request = ArgumentCaptor.forClass(HealthExamRequest.class);
        verify(healthExamService).saveExam(eq(teacher), eq(3L), eq(5L), request.capture());
        assertThat(request.getValue().heightCm()).isEqualByComparingTo("98.5");
        assertThat(request.getValue().headCircumferenceCm()).isEqualByComparingTo("48.5");
        assertThat(request.getValue().conclude()).isFalse();
    }

    private static void assertMeasurement(HealthMeasurementRequest request) {
        assertThat(request.campaignId()).isEqualTo(7L);
        assertThat(request.heightCm()).isEqualByComparingTo("98.5");
        assertThat(request.weightKg()).isEqualByComparingTo("15.2");
        assertThat(request.note()).isEqualTo("Cân đo tại lớp (giả)");
        assertThat(request.reason()).isEqualTo("Đo lại (giả)");
    }

    private CurrentUser authenticate() {
        CurrentUser user = TestPermissions.userWithDefaults(4L, CareNestRole.TEACHER);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token() {
        return jwt().authorities(new SimpleGrantedAuthority(CareNestRole.TEACHER.authority()));
    }
}
