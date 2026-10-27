package com.carenest.controller.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Lớp 1 (role) + envelope {code, desc, data} + validation của API năm học. */
@WebMvcTest(SchoolYearController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SchoolYearControllerTest {

    private static final String YEARS = "/api/school-years";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private SchoolYearService schoolYearService;

    @Test
    void teacherCanListYears() throws Exception {
        CurrentUser teacher = authenticateAs(CareNestRole.TEACHER);
        given(schoolYearService.getSchoolYears(eq(teacher), eq(SchoolYearStatus.ACTIVE)))
                .willReturn(List.of(year(SchoolYearStatus.ACTIVE)));

        mockMvc.perform(get(YEARS).param("status", "ACTIVE").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("2026-2027"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void kitchenStaffIsRejectedByRole() throws Exception {
        authenticateAs(CareNestRole.KITCHEN_STAFF);

        mockMvc.perform(get(YEARS).with(token(CareNestRole.KITCHEN_STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(schoolYearService, never()).getSchoolYears(any(), any());
    }

    @Test
    void vicePrincipalCannotCreate() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(post(YEARS)
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2027-2028")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(schoolYearService, never()).createSchoolYear(any(), any());
    }

    @Test
    void createReturns201() throws Exception {
        CurrentUser principal = authenticateAs(CareNestRole.PRINCIPAL);
        given(schoolYearService.createSchoolYear(eq(principal), any())).willReturn(year(SchoolYearStatus.PLANNED));

        mockMvc.perform(post(YEARS)
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-2027")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.status").value("PLANNED"));
    }

    @Test
    void badNameFormatIs400() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(post(YEARS)
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Năm 2027")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data[0].field").value("name"));
        verify(schoolYearService, never()).createSchoolYear(any(), any());
    }

    @Test
    void serviceConflictKeepsEnvelope() throws Exception {
        CurrentUser principal = authenticateAs(CareNestRole.PRINCIPAL);
        willThrow(GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_DELETABLE))
                .given(schoolYearService)
                .deleteSchoolYear(principal, 5L);

        mockMvc.perform(delete(YEARS + "/5").with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.desc").value(ApiCode.SCHOOL_YEAR_NOT_DELETABLE.getDefaultDesc()));
    }

    private static String body(String name) {
        return "{\"name\":\"" + name + "\",\"startDate\":\"2027-09-05\",\"endDate\":\"2028-05-31\"}";
    }

    private static SchoolYearDetailResponse year(SchoolYearStatus status) {
        return new SchoolYearDetailResponse(
                5L,
                "2026-2027",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 5, 31),
                status,
                2,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private CurrentUser authenticateAs(CareNestRole role) {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo.user", "Demo", null, Set.of(role));
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
