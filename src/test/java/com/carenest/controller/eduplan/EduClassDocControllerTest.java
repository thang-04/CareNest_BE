package com.carenest.controller.eduplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.eduplan.BranchFilePreviewResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.utils.enums.CareNestRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EduClassDocController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class EduClassDocControllerTest {
    private static final String PREVIEW = "/api/education-plans/class-docs/branch/file-preview";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private EduClassDocService service;

    @Test
    @DisplayName("EDU-47: multipart preview trả tuần hệ thống trong envelope hiện hành")
    void previewReturnsSystemWeeks() throws Exception {
        given(currentUserService.getCurrentUser())
                .willReturn(new CurrentUser(7L, "kc-7", "teacher", "Demo", null, Set.of(CareNestRole.TEACHER)));
        given(service.previewBranchFile(any(), eq(1L), eq(2L), any()))
                .willReturn(new BranchFilePreviewResponse(
                        List.of(new BranchFilePreviewResponse.Match(
                                2, 10L, "Chủ đề", LocalDate.parse("2026-09-07"), "Tên nhánh")),
                        List.of()));
        mvc.perform(multipart(PREVIEW)
                        .file(new MockMultipartFile("file", "x.csv", "text/csv", new byte[] {1}))
                        .param("classId", "1")
                        .param("yearPlanId", "2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matches[0].weekStart").value("2026-09-07"));
    }

    @Test
    @DisplayName("EDU-12/47: nhân viên bếp không được gọi preview")
    void kitchenCannotPreview() throws Exception {
        given(currentUserService.getCurrentUser())
                .willReturn(new CurrentUser(8L, "kc-8", "vp", "Demo", null, Set.of(CareNestRole.KITCHEN_STAFF)));
        mvc.perform(multipart(PREVIEW)
                        .file(new MockMultipartFile("file", "x.csv", "text/csv", new byte[] {1}))
                        .param("classId", "1")
                        .param("yearPlanId", "2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_KITCHEN_STAFF"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("EDU-47: thiếu part file bị chặn trước service")
    void missingFileIsRejected() throws Exception {
        given(currentUserService.getCurrentUser())
                .willReturn(new CurrentUser(7L, "kc-7", "teacher", "Demo", null, Set.of(CareNestRole.TEACHER)));
        mvc.perform(multipart(PREVIEW)
                        .param("classId", "1")
                        .param("yearPlanId", "2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
