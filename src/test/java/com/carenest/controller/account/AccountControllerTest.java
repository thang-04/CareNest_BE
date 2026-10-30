package com.carenest.controller.account;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.dto.account.MyAccountResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.AccountProvisioningService;
import com.carenest.service.account.AccountQueryService;
import com.carenest.service.account.AccountService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
import com.carenest.utils.enums.UserStatus;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AccountController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AccountControllerTest {

    private static final String MY_ACCOUNT = "/api/accounts/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private AccountProvisioningService accountProvisioningService;

    @MockitoBean
    private AccountQueryService accountQueryService;

    private static final String CREATE_TEACHER_JSON = """
            {"username":"teacher.c","fullName":"Giáo viên C","email":"teacher.c@example.test",
             "role":"TEACHER","temporaryPassword":"Temp-Pass-1"}""";

    @Test
    void shouldReturnProfileRolesAndScopeSummaryOfCurrentUser() throws Exception {
        CurrentUser currentUser = new CurrentUser(
                10L, "kc-1", "teacher.a", "Giáo viên Demo", null, Set.of(CareNestRole.TEACHER, CareNestRole.PARENT));
        given(currentUserService.getCurrentUser()).willReturn(currentUser);
        given(accountService.getMyAccount(any()))
                .willReturn(new MyAccountResponse(
                        10L,
                        "teacher.a",
                        "Giáo viên Demo",
                        null,
                        List.of(CareNestRole.TEACHER, CareNestRole.PARENT),
                        List.of(Permission.CHILD_PROFILE_VIEW),
                        List.of(new StaffAssignmentSummaryResponse(
                                StaffRole.TEACHER, null, 11L, null, LocalDate.of(2026, 9, 1), null)),
                        List.of(101L)));

        mockMvc.perform(get(MY_ACCOUNT).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.permissions[0]").value("CHILD_PROFILE_VIEW"))
                .andExpect(jsonPath("$.data.assignments[0].staffRole").value("TEACHER"))
                .andExpect(jsonPath("$.data.assignments[0].classroomId").value(11))
                .andExpect(jsonPath("$.data.assignments[0].validFrom").value("2026-09-01"))
                .andExpect(jsonPath("$.data.childIds[0]").value(101));
    }

    @Test
    void shouldReturn403WhenAccountIsNotProvisioned() throws Exception {
        given(currentUserService.getCurrentUser()).willThrow(GlobalException.of(ApiCode.ACCOUNT_NOT_PROVISIONED));

        mockMvc.perform(get(MY_ACCOUNT).with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.desc").value(ApiCode.ACCOUNT_NOT_PROVISIONED.getDefaultDesc()));
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(MY_ACCOUNT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void adminCreatesAccount() throws Exception {
        CurrentUser admin = authenticateAs(CareNestRole.ADMIN);
        given(accountProvisioningService.createAccount(eq(admin), any()))
                .willReturn(new CreateAccountResponse(
                        42L,
                        "teacher.c",
                        "Giáo viên C",
                        "teacher.c@example.test",
                        CareNestRole.TEACHER,
                        UserStatus.ACTIVE,
                        false,
                        null));

        mockMvc.perform(post("/api/accounts")
                        .with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_TEACHER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.setupEmailSent").value(false))
                .andExpect(content().string(not(containsString("Temp-Pass-1"))));
    }

    @Test
    @DisplayName("AUTH-06: không có ACCOUNT_MANAGE (giáo viên) thì không tạo được tài khoản")
    void withoutAccountManageCannotCreateAccount() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(post("/api/accounts")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_TEACHER_JSON))
                .andExpect(status().isForbidden());
        verify(accountProvisioningService, never()).createAccount(any(), any());
    }

    @Test
    void invalidCreateRequestReturnsFieldErrors() throws Exception {
        authenticateAs(CareNestRole.ADMIN);

        mockMvc.perform(post("/api/accounts")
                        .with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bad name!\",\"fullName\":\"\",\"temporaryPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.data[?(@.field == 'fullName')]").exists())
                .andExpect(jsonPath("$.data[?(@.field == 'role')]").exists())
                .andExpect(jsonPath("$.data[?(@.field == 'temporaryPassword')]").exists());
        verify(accountProvisioningService, never()).createAccount(any(), any());
    }

    @Test
    void adminDeactivatesAccount() throws Exception {
        CurrentUser admin = authenticateAs(CareNestRole.ADMIN);
        given(accountProvisioningService.updateAccountStatus(admin, 42L, UserStatus.INACTIVE))
                .willReturn(new AccountResponse(42L, "teacher.c", "Giáo viên C", null, UserStatus.INACTIVE));

        mockMvc.perform(patch("/api/accounts/42/status")
                        .with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void unknownStatusValueIsRejected() throws Exception {
        authenticateAs(CareNestRole.ADMIN);

        mockMvc.perform(patch("/api/accounts/42/status")
                        .with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isBadRequest());
    }

    private CurrentUser authenticateAs(CareNestRole role) {
        CurrentUser user = TestPermissions.userWithDefaults(1L, role);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor adminToken() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("AUTH-17: không có ACCOUNT_STATUS_MANAGE (giáo viên) thì không khóa được tài khoản")
    void withoutStatusManageCannotChangeAccountStatus() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(patch("/api/accounts/42/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
        verify(accountProvisioningService, never()).updateAccountStatus(any(), any(), any());
    }

    @Test
    void currentUserCannotBeSpoofedThroughRequestParameters() throws Exception {
        CurrentUser fromToken = authenticateAs(CareNestRole.TEACHER);
        given(accountService.getMyAccount(eq(fromToken)))
                .willReturn(new MyAccountResponse(
                        1L, "demo.user", "Demo", null, List.of(CareNestRole.TEACHER), List.of(), List.of(), List.of()));

        mockMvc.perform(get(MY_ACCOUNT + "?userId=999&keycloakUserId=kc-admin&roles=ADMIN")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1));
        verify(accountService).getMyAccount(eq(fromToken));
    }

    @Test
    @DisplayName("AUTH-08: giáo viên không có quyền quản lý nhân sự thì không xem danh sách tài khoản")
    void teacherCannotListAccounts() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(get("/api/accounts").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/accounts/42").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void principalListsAccountsWithPaging() throws Exception {
        CurrentUser principal = authenticateAs(CareNestRole.PRINCIPAL);
        given(accountQueryService.searchAccounts(
                        eq(principal), eq(CareNestRole.TEACHER), any(), any(), eq("lan"), eq(0), eq(20)))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/accounts?role=TEACHER&q=lan")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/accounts?size=500")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PRINCIPAL"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AUTH-17: HT cấp lại mật khẩu tạm; mật khẩu quá ngắn ⇒ 400")
    void principalResetsTemporaryPassword() throws Exception {
        CurrentUser principal = authenticateAs(CareNestRole.PRINCIPAL);
        given(accountProvisioningService.resetTemporaryPassword(principal, 42L, "New-Temp-1"))
                .willReturn(new AccountResponse(42L, "teacher.a", "GV A", null, UserStatus.ACTIVE));

        mockMvc.perform(post("/api/accounts/42/temporary-password")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PRINCIPAL")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"New-Temp-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(42));
        mockMvc.perform(post("/api/accounts/42/temporary-password")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PRINCIPAL")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
}
