package com.carenest.controller.eduplan;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.exception.GlobalException;
import com.carenest.security.*;
import com.carenest.service.eduplan.LearningResourceService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LearningResourceController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class LearningResourceControllerTest {

    @Test
    void multipartAcceptsActivityBindingAsParts() throws Exception {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo", "Demo", null, Set.of(CareNestRole.TEACHER));
        given(currentUserService.getCurrentUser()).willReturn(user);
        java.util.UUID requestId = java.util.UUID.randomUUID();
        var result = new com.carenest.dto.eduplan.LearningResourceResponse(
                8L,
                "dd_1_1_2026-09-07",
                "2026-09-07#hoc",
                "A",
                "demo.pdf",
                "application/pdf",
                8,
                7L,
                java.time.Instant.EPOCH,
                true,
                3L,
                "FILE",
                "demo.pdf",
                null,
                "MINE");
        given(service.upload(eq(user), eq("dd_1_1_2026-09-07"), any(), any(), eq(requestId)))
                .willReturn(result);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(BASE)
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "demo.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .part(new org.springframework.mock.web.MockPart("activityKey", "2026-09-07#hoc".getBytes()))
                        .part(new org.springframework.mock.web.MockPart("activityLabel", "A".getBytes()))
                        // proxy hosting đặt X-Request-Id dạng ULID; khóa chống gửi trùng phải lấy từ Idempotency-Key
                        .header("X-Request-Id", "01M6C4Y401Q2TBAJP49WXFHSVT")
                        .header("Idempotency-Key", requestId.toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityKey").value("2026-09-07#hoc"));
        org.mockito.Mockito.verify(service)
                .upload(
                        eq(user),
                        eq("dd_1_1_2026-09-07"),
                        any(),
                        eq(new com.carenest.dto.eduplan.LearningResourceBinding("2026-09-07#hoc", "A")),
                        eq(requestId));
    }

    @Test
    void uploadMissingPartReturns400WithClearMessage() throws Exception {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo", "Demo", null, Set.of(CareNestRole.TEACHER));
        given(currentUserService.getCurrentUser()).willReturn(user);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(BASE)
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "demo.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .part(new org.springframework.mock.web.MockPart("activityKey", "2026-09-07#hoc".getBytes()))
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.REQUIRED_FIELD_MISSING.getErrorCode()))
                .andExpect(jsonPath("$.desc").value("Thiếu thông tin bắt buộc: activityLabel"));
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CurrentUserService currentUserService;

    @MockitoBean
    LearningResourceService service;

    @TempDir
    Path dir;

    private static final String BASE = "/api/education-plans/class-docs/day/dd_1_1_2026-09-07/learning-resources";

    @Test
    void protectedVideoSupportsRangeAndPrivateHeaders() throws Exception {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo", "Demo", null, Set.of(CareNestRole.TEACHER));
        given(currentUserService.getCurrentUser()).willReturn(user);
        Path file = dir.resolve("demo");
        Files.writeString(file, "0123456789");
        given(service.content(eq(user), eq("dd_1_1_2026-09-07"), eq(8L)))
                .willReturn(
                        new LearningResourceService.Download(new FileSystemResource(file), "demo.mp4", "video/mp4"));
        mvc.perform(get(BASE + "/8/content")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER")))
                        .header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes("2345".getBytes()));
    }

    @Test
    void forbiddenResourceNeverReturnsBinary() throws Exception {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo", "Demo", null, Set.of(CareNestRole.TEACHER));
        given(currentUserService.getCurrentUser()).willReturn(user);
        given(service.content(any(), anyString(), anyLong()))
                .willThrow(GlobalException.of(ApiCode.CLASS_ACCESS_DENIED));
        mvc.perform(get(BASE + "/8/content").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/8/content").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_KITCHEN_STAFF"))))
                .andExpect(status().isForbidden());
    }
}
