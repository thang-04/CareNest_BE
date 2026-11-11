package com.carenest.controller.account;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.account.UserSignatureResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.UserSignatureService;
import com.carenest.utils.enums.CareNestRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserSignatureController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class UserSignatureControllerTest {
    private static final Instant AT = Instant.parse("2026-11-10T01:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserSignatureService signatures;

    @MockitoBean
    private CurrentUserService users;

    private CurrentUser asVicePrincipal() {
        CurrentUser pht = TestPermissions.userWithDefaults(7L, CareNestRole.VICE_PRINCIPAL);
        given(users.getCurrentUser()).willReturn(pht);
        return pht;
    }

    @Test
    @DisplayName("SIG-01: PHT xem danh sách chữ ký của mình")
    void listsOwnSignatures() throws Exception {
        CurrentUser pht = asVicePrincipal();
        given(signatures.listMine(pht)).willReturn(List.of(new UserSignatureResponse(3L, true, "image/png", 120, AT)));

        mvc.perform(get("/api/signatures/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    @DisplayName("SIG-01: tải ảnh lên trả 201 và truyền cờ mặc định")
    void uploadsSignature() throws Exception {
        CurrentUser pht = asVicePrincipal();
        MockMultipartFile file = new MockMultipartFile("file", "ky.png", "image/png", new byte[] {1, 2, 3});
        given(signatures.upload(pht, file, true)).willReturn(new UserSignatureResponse(4L, true, "image/png", 3, AT));

        mvc.perform(multipart("/api/signatures/me")
                        .file(file)
                        .param("isDefault", "true")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(4));
    }

    @Test
    @DisplayName("SIG-01: đổi mặc định và xóa chữ ký của mình")
    void changesDefaultAndDeletes() throws Exception {
        CurrentUser pht = asVicePrincipal();
        given(signatures.setDefault(pht, 5L)).willReturn(new UserSignatureResponse(5L, true, "image/png", 3, AT));

        mvc.perform(patch("/api/signatures/me/5/default")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));
        mvc.perform(delete("/api/signatures/me/5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk());
        verify(signatures).delete(pht, 5L);
    }

    @Test
    @DisplayName("SIG-01: ảnh trả byte với MIME, private/no-store và nosniff")
    void returnsPrivateImage() throws Exception {
        CurrentUser pht = asVicePrincipal();
        given(signatures.getContent(pht, 3L)).willReturn(new ImageContent(new byte[] {1, 2, 3}, "image/png"));

        mvc.perform(get("/api/signatures/me/3/content")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("AUTH-21: chưa đăng nhập hoặc phụ huynh không dùng được chữ ký")
    void rejectsAnonymousAndParent() throws Exception {
        mvc.perform(get("/api/signatures/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/signatures/me").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PARENT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(signatures);
    }
}
