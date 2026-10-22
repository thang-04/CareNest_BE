package com.carenest.controller.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.StaffingService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffAssignmentController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class StaffAssignmentControllerTest {

    private static final String ASSIGN_JSON = """
            {"userId":40,"classroomId":11}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private StaffingService staffingService;

    @Test
    @DisplayName("AUTH-16: PHT mặc định có STAFF_ASSIGNMENT_MANAGE ⇒ tạo phân công 201")
    void vicePrincipalAssignsStaff() throws Exception {
        CurrentUser vice = authenticate(TestPermissions.userWithDefaults(3L, CareNestRole.VICE_PRINCIPAL));
        given(staffingService.assignStaff(eq(vice), any()))
                .willReturn(new StaffAssignmentResponse(
                        5L, 40L, StaffRole.TEACHER, null, 11L, LocalDate.of(2026, 10, 17), null));

        mockMvc.perform(post("/api/staff-assignments")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @DisplayName("AUTH-16: HT tắt STAFF_ASSIGNMENT_MANAGE của PHT ⇒ 403 ngay ở lớp 1")
    void vicePrincipalWithoutPermissionIsRejected() throws Exception {
        authenticate(new CurrentUser(3L, "kc-3", "vp", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of()));

        mockMvc.perform(post("/api/staff-assignments")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/staff-assignments/5/end")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validTo\":\"2026-10-31\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(staffingService);
    }

    @Test
    void teacherCannotAssignAndMissingUserIdIs400() throws Exception {
        authenticate(TestPermissions.userWithDefaults(4L, CareNestRole.TEACHER));
        mockMvc.perform(post("/api/staff-assignments")
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_JSON))
                .andExpect(status().isForbidden());

        authenticate(TestPermissions.userWithDefaults(2L, CareNestRole.PRINCIPAL));
        mockMvc.perform(post("/api/staff-assignments")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classroomId\":11}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(staffingService);
    }

    private CurrentUser authenticate(CurrentUser user) {
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
