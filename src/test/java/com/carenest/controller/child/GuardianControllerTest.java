package com.carenest.controller.child;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.child.AddGuardianResponse;
import com.carenest.dto.child.ChildGuardianOverviewResponse;
import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.GuardianCandidateResponse;
import com.carenest.dto.child.GuardianResponse;
import com.carenest.dto.child.ParentPasswordResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.GuardianAccountService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.UserStatus;
import java.util.List;
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

@WebMvcTest(GuardianController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class GuardianControllerTest {

    private static final String ADD_JSON = """
            {"phone":"0912 345 678","fullName":"Phụ huynh Demo","relationship":"MOTHER"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private GuardianAccountService guardianAccountService;

    @Test
    @DisplayName("AUTH-18: PHT mặc định có PARENT_ACCOUNT_MANAGE ⇒ tạo phụ huynh 201, mật khẩu tạm trả 1 lần")
    void vicePrincipalAddsGuardian() throws Exception {
        CurrentUser vice = authenticate(CareNestRole.VICE_PRINCIPAL);
        given(guardianAccountService.addGuardian(eq(vice), eq(101L), any()))
                .willReturn(new AddGuardianResponse(
                        50L,
                        "0912345678",
                        "Phụ huynh Demo",
                        "me@example.test",
                        GuardianRelationship.MOTHER,
                        UserStatus.ACTIVE,
                        true,
                        "Gen3rated7k"));

        mockMvc.perform(post("/api/children/101/guardians")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADD_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("0912345678"))
                .andExpect(jsonPath("$.data.email").value("me@example.test"))
                .andExpect(jsonPath("$.data.newAccount").value(true))
                .andExpect(jsonPath("$.data.temporaryPassword").value("Gen3rated7k"));
    }

    @Test
    @DisplayName("AUTH-18: tra SĐT trả tài khoản có sẵn với họ tên đã che")
    void vicePrincipalLooksUpGuardianCandidate() throws Exception {
        CurrentUser vice = authenticate(CareNestRole.VICE_PRINCIPAL);
        given(guardianAccountService.findGuardianCandidate(vice, 101L, "0912 345 678"))
                .willReturn(new GuardianCandidateResponse(true, 61L, "N***** T** L**", true, false, UserStatus.ACTIVE));

        mockMvc.perform(post("/api/children/101/guardians/lookup")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912 345 678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.userId").value(61))
                .andExpect(jsonPath("$.data.maskedFullName").value("N***** T** L**"))
                .andExpect(jsonPath("$.data.staffAccount").value(true));
    }

    @Test
    void teacherCannotManageParents() throws Exception {
        authenticate(CareNestRole.TEACHER);

        mockMvc.perform(post("/api/children/101/guardians")
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADD_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/children/101/guardians").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/children/101/guardians/lookup")
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912 345 678\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/children/101/guardians/50").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/parent-accounts/50/temporary-password").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(guardianAccountService);
    }

    @Test
    void invalidRelationshipOrMissingPhoneIs400() throws Exception {
        authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(post("/api/children/101/guardians")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"fullName\":\"PH\",\"relationship\":\"UNCLE\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/children/101/guardians")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"PH\",\"relationship\":\"MOTHER\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(guardianAccountService);
    }

    @Test
    void invalidOrTooLongEmailIs400() throws Exception {
        authenticate(CareNestRole.PRINCIPAL);
        String tooLong = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".vn";

        for (String email : new String[] {"not-an-email", tooLong}) {
            mockMvc.perform(post("/api/children/101/guardians")
                            .with(token(CareNestRole.PRINCIPAL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"0912345678\",\"fullName\":\"PH\",\"relationship\":\"MOTHER\","
                                    + "\"email\":\"" + email + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(guardianAccountService);
    }

    @Test
    void principalResetsParentPassword() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(guardianAccountService.resetParentPassword(principal, 50L))
                .willReturn(new ParentPasswordResponse(50L, "0912345678", "N3wTempPass"));

        mockMvc.perform(post("/api/parent-accounts/50/temporary-password").with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").value("N3wTempPass"));
    }

    @Test
    @DisplayName("Màn Tài khoản phụ huynh: một lần gọi cho cả trang, bộ lọc như GET /children")
    void principalReadsGuardianOverview() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(guardianAccountService.guardianOverview(
                        principal, new ChildListFilter(null, 7L, null, null, null, 1, 100)))
                .willReturn(new PageResponse<>(
                        List.of(new ChildGuardianOverviewResponse(
                                null,
                                new GuardianResponse(
                                        50L,
                                        "0912345678",
                                        "Phụ huynh Demo",
                                        null,
                                        GuardianRelationship.MOTHER,
                                        UserStatus.ACTIVE,
                                        null))),
                        1,
                        100,
                        101,
                        2));

        mockMvc.perform(get("/api/children/guardian-overview")
                        .param("campusId", "7")
                        .param("page", "1")
                        .param("size", "100")
                        .with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].account.userId").value(50))
                .andExpect(jsonPath("$.data.items[0].account.username").value("0912345678"))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    @DisplayName("Màn Tài khoản phụ huynh cần PARENT_ACCOUNT_MANAGE: GV ⇒ 403")
    void teacherCannotReadGuardianOverview() throws Exception {
        authenticate(CareNestRole.TEACHER);

        mockMvc.perform(get("/api/children/guardian-overview").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(guardianAccountService);
    }

    private CurrentUser authenticate(CareNestRole role) {
        CurrentUser user = TestPermissions.userWithDefaults(role == CareNestRole.PRINCIPAL ? 2L : 3L, role);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
