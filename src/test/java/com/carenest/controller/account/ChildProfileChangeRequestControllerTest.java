package com.carenest.controller.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.child.ChildProfileChangeResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.account.ChildProfileChangeRequestService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.ProfileChangeKind;
import com.carenest.utils.enums.ProfileChangeStatus;
import java.time.Instant;
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

/** Lớp 1 (mã CHILD_PROFILE_EDIT) + envelope của API yêu cầu sửa hồ sơ trẻ (CHD-11). */
@WebMvcTest(ChildProfileChangeRequestController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChildProfileChangeRequestControllerTest {

    private static final String SUBMIT = "/api/children/1/profile-change-requests";
    private static final String BODY = """
            {"kind":"PROFILE","profile":{"child":null,"reason":"Phụ huynh báo đổi địa chỉ"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ChildProfileChangeRequestService service;

    @Test
    @DisplayName("CHD-11: GV chưa được cấp CHILD_PROFILE_EDIT gửi yêu cầu ⇒ 403, không gọi service")
    void teacherWithoutGrantIsForbidden() throws Exception {
        authenticate(Set.of(Permission.CHILD_PROFILE_VIEW));

        mockMvc.perform(post(SUBMIT)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(service, never()).submit(any(), any(), any());
    }

    @Test
    @DisplayName("CHD-11: GV được cấp gửi yêu cầu ⇒ 201, trả yêu cầu PENDING")
    void teacherWithGrantSubmits() throws Exception {
        CurrentUser user = authenticate(Set.of(Permission.CHILD_PROFILE_VIEW, Permission.CHILD_PROFILE_EDIT));
        given(service.submit(eq(user), eq(1L), any())).willReturn(response());

        mockMvc.perform(post(SUBMIT)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("CHD-11: thiếu kind ⇒ 400, không gọi service")
    void missingKindIsRejected() throws Exception {
        authenticate(Set.of(Permission.CHILD_PROFILE_EDIT));

        mockMvc.perform(post(SUBMIT)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).submit(any(), any(), any());
    }

    private CurrentUser authenticate(Set<Permission> permissions) {
        CurrentUser user =
                new CurrentUser(20L, "kc-20", "gv.lop", "Cô Lan", null, Set.of(CareNestRole.TEACHER), permissions);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token() {
        return jwt().authorities(new SimpleGrantedAuthority(CareNestRole.TEACHER.authority()));
    }

    private static ChildProfileChangeResponse response() {
        return new ChildProfileChangeResponse(
                100L,
                1L,
                "HS26001",
                "Bé An",
                "Lớp Chồi 1",
                10L,
                ProfileChangeKind.PROFILE,
                ProfileChangeStatus.PENDING,
                null,
                null,
                20L,
                "Cô Lan",
                Instant.parse("2026-11-16T02:00:00Z"),
                null,
                null,
                null,
                null,
                false);
    }
}
