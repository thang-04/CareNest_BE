package com.carenest.controller.account;

import static org.mockito.ArgumentMatchers.any;
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
import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.GuardianPhoneChangeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.PhoneChangeStatus;
import com.carenest.utils.enums.SmsStatus;
import java.time.Instant;
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

@WebMvcTest(GuardianPhoneChangeController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class GuardianPhoneChangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private GuardianPhoneChangeService guardianPhoneChangeService;

    @Test
    @DisplayName("G94-180/AUTH-21: PHT có quyền quản lý tài khoản phụ huynh xem yêu cầu chờ duyệt")
    void vicePrincipalListsPendingRequests() throws Exception {
        CurrentUser vice = authenticate(CareNestRole.VICE_PRINCIPAL);
        given(guardianPhoneChangeService.list(vice, PhoneChangeStatus.PENDING)).willReturn(List.of(response()));

        mockMvc.perform(get("/api/parent-accounts/phone-change-requests")
                        .param("status", "PENDING")
                        .with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(70))
                .andExpect(jsonPath("$.data[0].newPhone").value("0987654321"));
    }

    @Test
    @DisplayName("G94-180/AUTH-21: duyệt ⇒ 200 kèm trạng thái SMS; người gửi tự duyệt ⇒ 403")
    void approveAndSelfApproval() throws Exception {
        CurrentUser vice = authenticate(CareNestRole.VICE_PRINCIPAL);
        given(guardianPhoneChangeService.approve(vice, 70L)).willReturn(response());
        given(guardianPhoneChangeService.approve(vice, 71L))
                .willThrow(GlobalException.of(ApiCode.PHONE_CHANGE_SELF_DECISION));

        mockMvc.perform(post("/api/parent-accounts/phone-change-requests/70/approve")
                        .with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smsStatus").value("SENT"));
        mockMvc.perform(post("/api/parent-accounts/phone-change-requests/71/approve")
                        .with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.PHONE_CHANGE_SELF_DECISION.getErrorCode()));
    }

    @Test
    @DisplayName("G94-180/AUTH-21: từ chối nhận ghi chú hoặc không có body")
    void rejectWithOrWithoutNote() throws Exception {
        CurrentUser vice = authenticate(CareNestRole.VICE_PRINCIPAL);
        given(guardianPhoneChangeService.reject(eq(vice), eq(70L), any())).willReturn(response());
        given(guardianPhoneChangeService.reject(eq(vice), eq(70L), isNull())).willReturn(response());

        mockMvc.perform(post("/api/parent-accounts/phone-change-requests/70/reject")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Chưa xác minh\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/parent-accounts/phone-change-requests/70/reject")
                        .with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isOk());
        verify(guardianPhoneChangeService).reject(eq(vice), eq(70L), isNull());
    }

    @Test
    @DisplayName("Giáo viên không có quyền quản lý tài khoản phụ huynh ⇒ 403, không gọi service")
    void teacherForbidden() throws Exception {
        authenticate(CareNestRole.TEACHER);

        mockMvc.perform(post("/api/parent-accounts/phone-change-requests/70/approve")
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(guardianPhoneChangeService);
    }

    private static GuardianPhoneChangeResponse response() {
        return new GuardianPhoneChangeResponse(
                70L,
                1L,
                "HS26001",
                "Bé An",
                2L,
                31L,
                50L,
                "Mẹ An",
                "0912345678",
                "0987654321",
                PhoneChangeStatus.APPROVED,
                "Phụ huynh đổi số",
                4L,
                "PHT A",
                Instant.parse("2026-11-10T03:00:00Z"),
                3L,
                "PHT B",
                Instant.parse("2026-11-10T04:00:00Z"),
                null,
                SmsStatus.SENT,
                false);
    }

    private CurrentUser authenticate(CareNestRole role) {
        CurrentUser user = TestPermissions.userWithDefaults(3L, role);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
