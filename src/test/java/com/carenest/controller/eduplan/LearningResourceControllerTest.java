package com.carenest.controller.eduplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.eduplan.LearningResourceResponse;
import com.carenest.security.*;
import com.carenest.service.eduplan.LearningResourceService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LearningResourceController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class LearningResourceControllerTest {
    private static final String KEY = "dd_1_1_2026-09-07";
    private static final String CONFIRM = "/api/education-plans/class-docs/day/" + KEY + "/learning-resources/confirm";
    private static final String BODY = """
            {"uploadId":"up/abc.pdf","fileName":"demo.pdf","activityKey":"2026-09-07#hoc","activityLabel":"Hoạt động học"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CurrentUserService currentUserService;

    @MockitoBean
    LearningResourceService service;

    private CurrentUser teacher() {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo", "Demo", null, Set.of(CareNestRole.TEACHER));
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    @Test
    void confirmTakesIdempotencyKeyAndIgnoresProxyRequestId() throws Exception {
        CurrentUser user = teacher();
        UUID requestId = UUID.randomUUID();
        var result = new LearningResourceResponse(
                8L,
                KEY,
                "2026-09-07#hoc",
                "Hoạt động học",
                "demo.pdf",
                "application/pdf",
                8,
                7L,
                Instant.EPOCH,
                true,
                3L,
                "FILE",
                "demo.pdf",
                null);
        given(service.confirmUpload(eq(user), eq(KEY), any(), eq(requestId))).willReturn(result);
        mvc.perform(post(CONFIRM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        // proxy hosting đặt X-Request-Id dạng ULID; khóa chống gửi trùng phải lấy từ Idempotency-Key
                        .header("X-Request-Id", "01M6C4Y401Q2TBAJP49WXFHSVT")
                        .header("Idempotency-Key", requestId.toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityKey").value("2026-09-07#hoc"));
        verify(service).confirmUpload(eq(user), eq(KEY), any(), eq(requestId));
    }

    @Test
    void confirmWithoutIdempotencyKeyReturns400() throws Exception {
        teacher();
        mvc.perform(post(CONFIRM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.REQUIRED_FIELD_MISSING.getErrorCode()));
        verifyNoInteractions(service);
    }

    @Test
    void confirmWithoutActivityLabelReturns400() throws Exception {
        teacher();
        mvc.perform(post(CONFIRM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"uploadId\":\"up/abc.pdf\",\"fileName\":\"demo.pdf\",\"activityKey\":\"2026-09-07#hoc\"}")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEACHER"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void confirmIsForbiddenForKitchenStaff() throws Exception {
        mvc.perform(post(CONFIRM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_KITCHEN_STAFF"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
