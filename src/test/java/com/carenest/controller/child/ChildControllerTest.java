package com.carenest.controller.child;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CareNestRole;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.child.ChildService;
import com.carenest.utils.ApiCode;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Ma trận lớp 1 (role) + lớp 2 (phạm vi do service/policy quyết định) cho GET /children/{childId}. */
@WebMvcTest(ChildController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChildControllerTest {

    private static final String CHILD_URL = "/api/children/{childId}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ChildService childService;

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(CHILD_URL, 101))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void kitchenStaffIsRejectedByRoleBeforeReachingService() throws Exception {
        authenticateAs(CareNestRole.KITCHEN_STAFF);

        mockMvc.perform(get(CHILD_URL, 101).with(tokenWithRole(CareNestRole.KITCHEN_STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(childService, never()).getChildProfile(any(), any());
    }

    @Test
    void adminIsRejectedByRole() throws Exception {
        authenticateAs(CareNestRole.ADMIN);

        mockMvc.perform(get(CHILD_URL, 101).with(tokenWithRole(CareNestRole.ADMIN)))
                .andExpect(status().isForbidden());
        verify(childService, never()).getChildProfile(any(), any());
    }

    @Test
    void teacherOutsideScopeGets403FromService() throws Exception {
        CurrentUser teacher = authenticateAs(CareNestRole.TEACHER);
        given(childService.getChildProfile(teacher, 201L))
                .willThrow(new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this child"));

        mockMvc.perform(get(CHILD_URL, 201).with(tokenWithRole(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.desc").value("You do not have access to this child"));
    }

    @Test
    void teacherInScopeGetsChildProfile() throws Exception {
        CurrentUser teacher = authenticateAs(CareNestRole.TEACHER);
        given(childService.getChildProfile(teacher, 101L))
                .willReturn(new ChildProfileResponse(101L, "Trẻ Demo A1-01", LocalDate.of(2022, 3, 15), 11L));

        mockMvc.perform(get(CHILD_URL, 101).with(tokenWithRole(CareNestRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.dateOfBirth").value("2022-03-15"))
                .andExpect(jsonPath("$.data.classroomId").value(11));
    }

    @Test
    void parentRoleIsAllowedThroughToScopeCheck() throws Exception {
        CurrentUser parent = authenticateAs(CareNestRole.PARENT);
        given(childService.getChildProfile(eq(parent), eq(101L)))
                .willReturn(new ChildProfileResponse(101L, "Trẻ Demo A1-01", LocalDate.of(2022, 3, 15), 11L));

        mockMvc.perform(get(CHILD_URL, 101).with(tokenWithRole(CareNestRole.PARENT)))
                .andExpect(status().isOk());
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(get(CHILD_URL, "abc").with(tokenWithRole(CareNestRole.TEACHER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private CurrentUser authenticateAs(CareNestRole role) {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo.user", "Demo", null, Set.of(role));
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor tokenWithRole(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
