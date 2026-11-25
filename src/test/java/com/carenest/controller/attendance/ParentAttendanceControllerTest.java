package com.carenest.controller.attendance;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentTodayResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.attendance.ParentAttendanceService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ParentAttendanceStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ParentAttendanceController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ParentAttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ParentAttendanceService parentAttendanceService;

    @Test
    @DisplayName("Phụ huynh xem điểm danh hôm nay của con; con người khác ⇒ 4108")
    void parentReadsTodayAndOtherChildIsForbidden() throws Exception {
        CurrentUser parent = authenticate(TestPermissions.userWithDefaults(5L, CareNestRole.PARENT));
        given(parentAttendanceService.getToday(eq(parent), eq(9L)))
                .willReturn(new ParentTodayResponse(
                        LocalDate.of(2026, 10, 21), ParentAttendanceStatus.PRESENT, null, null, 1L, "Lớp A"));
        mockMvc.perform(get("/api/children/9/attendance/today").with(token(CareNestRole.PARENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PRESENT"))
                .andExpect(jsonPath("$.data.className").value("Lớp A"));

        given(parentAttendanceService.getToday(eq(parent), eq(8L)))
                .willThrow(GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN));
        mockMvc.perform(get("/api/children/8/attendance/today").with(token(CareNestRole.PARENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(4108));
        verify(parentAttendanceService).getToday(parent, 8L);
    }

    @Test
    @DisplayName("Nhân viên không gọi được API phụ huynh (API sức khỏe của nhân viên không mở cho phụ huynh)")
    void staffCannotUseParentEndpoints() throws Exception {
        authenticate(TestPermissions.userWithDefaults(4L, CareNestRole.TEACHER));
        mockMvc.perform(get("/api/children/9/attendance?from=2026-10-01&to=2026-10-21")
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/children/9/daily-health/parent?from=2026-10-01&to=2026-10-21")
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(parentAttendanceService);
    }

    private CurrentUser authenticate(CurrentUser user) {
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
