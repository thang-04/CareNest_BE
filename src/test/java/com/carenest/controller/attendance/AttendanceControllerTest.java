package com.carenest.controller.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.attendance.SaveAttendanceResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.utils.enums.CareNestRole;
import java.time.Instant;
import java.time.LocalDate;
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

/** Lớp 1 (role) + envelope {code, desc, data} + validation của API điểm danh. */
@WebMvcTest(AttendanceController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AttendanceControllerTest {

    private static final String SAVE = "/api/classes/3/attendance";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private AttendanceService attendanceService;

    @MockitoBean
    private AttendanceDayUnlockService attendanceDayUnlockService;

    @Test
    void teacherSavesClassAttendance() throws Exception {
        CurrentUser teacher = authenticateAs(CareNestRole.TEACHER);
        given(attendanceService.saveClassAttendance(eq(teacher), eq(3L), any()))
                .willReturn(new SaveAttendanceResponse(Instant.parse("2026-10-21T01:00:00Z"), 1, 1));

        mockMvc.perform(put(SAVE)
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-10-21\",\"entries\":[{\"childId\":5,\"status\":\"PRESENT\","
                                + "\"meals\":{\"LUNCH\":true}}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.changed").value(1));
    }

    @Test
    void vicePrincipalCannotSave() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(put(SAVE)
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-10-21\",\"entries\":[{\"childId\":5,\"status\":\"PRESENT\"}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(attendanceService, never()).saveClassAttendance(any(), any(), any());
    }

    @Test
    void emptyEntriesAreRejected() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(put(SAVE)
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-10-21\",\"entries\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void teacherCannotReadMultiClassSummary() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(get("/api/attendance/summary")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-21")
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        verify(attendanceService, never()).getSummary(any(), any(), any(), any(LocalDate.class), any(LocalDate.class));
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
