package com.carenest.controller.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.attendance.LeaveRequestService;
import com.carenest.utils.enums.CareNestRole;
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

@WebMvcTest(LeaveRequestController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class LeaveRequestControllerTest {

    private static final String BODY = """
            {"childId":9,"fromDate":"2026-10-22","toDate":"2026-10-23","reason":"Bé ốm"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private LeaveRequestService leaveRequestService;

    @Test
    @DisplayName("ATT-05: phụ huynh gửi đơn nghỉ; GV không gửi thay")
    void onlyParentCreatesLeave() throws Exception {
        CurrentUser parent = authenticate(TestPermissions.userWithDefaults(5L, CareNestRole.PARENT));
        mockMvc.perform(post("/api/leave-requests")
                        .with(token(CareNestRole.PARENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
        verify(leaveRequestService).createLeaveRequest(eq(parent), any());

        authenticate(TestPermissions.userWithDefaults(4L, CareNestRole.TEACHER));
        mockMvc.perform(post("/api/leave-requests")
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Thiếu lý do ⇒ 400 theo ô; phụ huynh không xem danh sách của trường")
    void validationAndStaffListGuard() throws Exception {
        authenticate(TestPermissions.userWithDefaults(5L, CareNestRole.PARENT));
        mockMvc.perform(
                        post("/api/leave-requests")
                                .with(token(CareNestRole.PARENT))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"childId\":9,\"fromDate\":\"2026-10-22\",\"toDate\":\"2026-10-23\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("reason"));
        mockMvc.perform(get("/api/leave-requests").with(token(CareNestRole.PARENT)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(leaveRequestService);
    }

    private CurrentUser authenticate(CurrentUser user) {
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
