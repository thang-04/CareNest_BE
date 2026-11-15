package com.carenest.controller.child;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.HealthDeclarationResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.ChildPhotoService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Gender;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChildPhotoController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChildPhotoControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ChildPhotoService photos;

    @MockitoBean
    private CurrentUserService users;

    @MockitoBean
    private ChildRecordService records;

    @Test
    @DisplayName("CHD-02: ảnh trả byte với MIME, private/no-store và nosniff")
    void returnsPrivateImage() throws Exception {
        CurrentUser teacher = TestPermissions.userWithDefaults(7L, CareNestRole.TEACHER);
        given(users.getCurrentUser()).willReturn(teacher);
        given(photos.getGuardianPhoto(teacher, 1L, 11L))
                .willReturn(new ImageContent(new byte[] {1, 2, 3}, "image/png"));
        mvc.perform(get("/api/children/1/guardian-photos/11")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("CHD-06: hồ sơ trẻ trả { child, declaration } — khai báo sức khỏe hiện tại kèm lịch sử")
    void profileIncludesHealthDeclaration() throws Exception {
        CurrentUser teacher = TestPermissions.userWithDefaults(7L, CareNestRole.TEACHER);
        given(users.getCurrentUser()).willReturn(teacher);
        given(photos.getProfile(teacher, 1L)).willReturn(view());
        given(records.findDeclaration(1L))
                .willReturn(
                        new HealthDeclarationResponse(1L, null, null, null, null, 2L, "Hiệu trưởng", null, List.of()));
        mvc.perform(get("/api/children/1/profile").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.child.id").value(1))
                .andExpect(jsonPath("$.data.declaration.childId").value(1))
                .andExpect(jsonPath("$.data.declaration.declaredBy").value(2));
    }

    @Test
    @DisplayName("AUTH-04: chưa đăng nhập không tải được ảnh trẻ")
    void rejectsAnonymous() throws Exception {
        mvc.perform(get("/api/children/1/photo")).andExpect(status().isUnauthorized());
        verifyNoInteractions(photos);
    }

    private static ChildViewResponse view() {
        return new ChildViewResponse(
                1L,
                "HS26001",
                "Bé An",
                LocalDate.of(2022, 3, 1),
                Gender.FEMALE,
                ChildStatus.ACTIVE,
                11L,
                21L,
                "Lớp kiểm thử",
                31L,
                51L,
                LocalDate.of(2026, 9, 1),
                null,
                null,
                null,
                List.of(),
                true,
                null,
                List.of(),
                List.of(),
                null);
    }
}
