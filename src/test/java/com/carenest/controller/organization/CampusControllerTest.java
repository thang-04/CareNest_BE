package com.carenest.controller.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.organization.CampusService;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Lớp 1 (role) + envelope {code, desc, data} + validation của API điểm trường. */
@WebMvcTest(CampusController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class CampusControllerTest {

    private static final String CAMPUSES = "/api/campuses";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private CampusService campusService;

    @Test
    void vicePrincipalCanList() throws Exception {
        CurrentUser vicePrincipal = authenticateAs(CareNestRole.VICE_PRINCIPAL);
        given(campusService.getCampuses(eq(vicePrincipal), eq(null))).willReturn(List.of(campus()));

        mockMvc.perform(get(CAMPUSES).with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].isMain").value(true))
                .andExpect(jsonPath("$.data[0].vicePrincipals").isArray());
    }

    @Test
    void teacherIsRejectedByRole() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(get(CAMPUSES).with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(campusService, never()).getCampuses(any(), any());
    }

    @Test
    void createReturns201() throws Exception {
        CurrentUser principal = authenticateAs(CareNestRole.PRINCIPAL);
        given(campusService.createCampus(eq(principal), any())).willReturn(campus());

        mockMvc.perform(post(CAMPUSES)
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CS-C\",\"name\":\"Cơ sở C\",\"shortName\":\"CS C\","
                                + "\"address\":\"Hà Nội\",\"phone\":\"+84 912 345 678\",\"isMain\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201));
    }

    @ParameterizedTest
    // Chữ, số cố định cũ 10 số, di động sai đầu số, thiếu số
    @ValueSource(strings = {"abc", "0241234567", "0112345678", "091234567"})
    void invalidPhoneIs400(String phone) throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(post(CAMPUSES)
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CS-C\",\"name\":\"Cơ sở C\",\"shortName\":\"CS C\","
                                + "\"address\":\"Hà Nội\",\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("phone"));
        verify(campusService, never()).createCampus(any(), any());
    }

    private static CampusResponse campus() {
        return new CampusResponse(10L, "CS-A", "Cơ sở A", "CS A", "Hà Nội", null, true, 2, 30, List.of());
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
