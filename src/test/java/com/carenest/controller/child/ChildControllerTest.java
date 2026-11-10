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
import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.dto.child.ParentAccountRowResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.ChildService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.GuardianAccountStatus;
import com.carenest.utils.enums.GuardianRelationship;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
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

    @MockitoBean
    private com.carenest.service.child.ChildListService childListService;

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
    @DisplayName("AUTH-10: giáo viên bị thu hồi CHILD_PROFILE_VIEW bị chặn ngay ở lớp 1")
    void teacherWithRevokedChildViewIsRejectedBeforeReachingService() throws Exception {
        CurrentUser revokedTeacher =
                new CurrentUser(7L, "kc-7", "demo.user", "Demo", null, Set.of(CareNestRole.TEACHER), Set.of());
        given(currentUserService.getCurrentUser()).willReturn(revokedTeacher);

        mockMvc.perform(get(CHILD_URL, 101).with(tokenWithRole(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(childService, never()).getChildProfile(any(), any());
    }

    @Test
    void teacherOutsideScopeGets403FromService() throws Exception {
        CurrentUser teacher = authenticateAs(CareNestRole.TEACHER);
        given(childService.getChildProfile(teacher, 201L)).willThrow(GlobalException.of(ApiCode.CHILD_ACCESS_DENIED));

        mockMvc.perform(get(CHILD_URL, 201).with(tokenWithRole(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.desc").value(ApiCode.CHILD_ACCESS_DENIED.getDefaultDesc()));
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
    @DisplayName(
            "D-4: GV xem danh sách; tên cũ classroomId, q của màn Tài khoản phụ huynh vẫn nhận; status nhiều giá trị")
    void teacherListsChildrenWithLegacyParams() throws Exception {
        CurrentUser teacher = authenticateAs(CareNestRole.TEACHER);
        given(childListService.searchChildren(eq(teacher), any()))
                .willReturn(new com.carenest.dto.common.PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/children")
                        .param("classroomId", "11")
                        .param("q", "An")
                        .param("status", "ACTIVE", "LEFT")
                        .param("schoolYear", "5")
                        .with(tokenWithRole(CareNestRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desc").value("Search children success"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalPages").value(0));

        org.mockito.ArgumentCaptor<com.carenest.dto.child.ChildListFilter> filter =
                org.mockito.ArgumentCaptor.forClass(com.carenest.dto.child.ChildListFilter.class);
        verify(childListService).searchChildren(eq(teacher), filter.capture());
        org.assertj.core.api.Assertions.assertThat(filter.getValue())
                .isEqualTo(new com.carenest.dto.child.ChildListFilter(
                        5L,
                        null,
                        11L,
                        Set.of(com.carenest.utils.enums.ChildStatus.ACTIVE, com.carenest.utils.enums.ChildStatus.LEFT),
                        "An",
                        0,
                        20));
    }

    @Test
    @DisplayName("AUTH-05: bếp không có CHILD_PROFILE_VIEW / PARENT_ACCOUNT_MANAGE ⇒ 403 ở lớp 1, không tới service")
    void kitchenCannotListChildren() throws Exception {
        authenticateAs(CareNestRole.KITCHEN_STAFF);

        mockMvc.perform(get("/api/children").with(tokenWithRole(CareNestRole.KITCHEN_STAFF)))
                .andExpect(status().isForbidden());
        verify(childListService, never()).searchChildren(any(), any());
    }

    @Test
    @DisplayName("Mục 4.8: HT đọc phụ huynh theo trạng thái tài khoản, từ khóa; route không bị hiểu là /children/{id}")
    void principalListsParentAccounts() throws Exception {
        CurrentUser principal = authenticateAs(CareNestRole.PRINCIPAL);
        given(childListService.listParentAccounts(principal, GuardianAccountStatus.NOT_ACTIVATED, "an"))
                .willReturn(List.of(new ParentAccountRowResponse(
                        11L,
                        "TR001",
                        "Trẻ Demo",
                        1L,
                        "Chồi A",
                        0,
                        new ChildGuardianResponse(
                                "Phụ huynh demo",
                                GuardianRelationship.MOTHER,
                                "0900000001",
                                null,
                                null,
                                GuardianAccountStatus.NOT_ACTIVATED,
                                null,
                                null,
                                null),
                        null)));

        mockMvc.perform(get("/api/children/parent-accounts")
                        .param("status", "NOT_ACTIVATED")
                        .param("keyword", "an")
                        .with(tokenWithRole(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].childId").value(11))
                .andExpect(jsonPath("$.data[0].guardianIndex").value(0))
                .andExpect(jsonPath("$.data[0].guardian.accountStatus").value("NOT_ACTIVATED"))
                .andExpect(jsonPath("$.data[0].username").doesNotExist());
        verify(childService, never()).getChildProfile(any(), any());
    }

    @Test
    @DisplayName("Mục 4.8: GV không có PARENT_ACCOUNT_MANAGE ⇒ 403 ở lớp 1")
    void teacherCannotListParentAccounts() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(get("/api/children/parent-accounts").with(tokenWithRole(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        verify(childListService, never()).listParentAccounts(any(), any(), any());
    }

    @Test
    void parentAccountsRejectsUnknownStatus() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(get("/api/children/parent-accounts")
                        .param("status", "PENDING")
                        .with(tokenWithRole(CareNestRole.PRINCIPAL)))
                .andExpect(status().isBadRequest());
        verify(childListService, never()).listParentAccounts(any(), any(), any());
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(get(CHILD_URL, "abc").with(tokenWithRole(CareNestRole.TEACHER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private CurrentUser authenticateAs(CareNestRole role) {
        CurrentUser user = TestPermissions.userWithDefaults(7L, role);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor tokenWithRole(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
