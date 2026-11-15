package com.carenest.controller.account;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.account.AccountPhotoResponse;
import com.carenest.dto.account.AccountPhotoUrlResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.AccountPhotoService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountPhotoController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AccountPhotoControllerTest {
    private static final Instant AT = Instant.parse("2026-11-14T03:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountPhotoService photos;

    @MockitoBean
    private CurrentUserService users;

    private CurrentUser asTeacher() {
        CurrentUser gv = TestPermissions.userWithDefaults(30L, CareNestRole.TEACHER);
        given(users.getCurrentUser()).willReturn(gv);
        return gv;
    }

    private static SimpleGrantedAuthority teacherRole() {
        return new SimpleGrantedAuthority("ROLE_TEACHER");
    }

    @Test
    @DisplayName("AUTH-27: GV tải ảnh đại diện của mình")
    void teacherUploadsOwnPhoto() throws Exception {
        CurrentUser gv = asTeacher();
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});
        given(photos.updateMyPhoto(gv, file)).willReturn(new AccountPhotoResponse(true, AT, false));

        mvc.perform(multipart(HttpMethod.PUT, "/api/accounts/me/photo")
                        .file(file)
                        .with(jwt().authorities(teacherRole())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUploaded").value(true));
    }

    @Test
    @DisplayName("AUTH-27: GV xóa ảnh của mình; phụ huynh bị chặn 403")
    void deletesOwnPhotoAndParentBlocked() throws Exception {
        CurrentUser gv = asTeacher();
        given(photos.deleteMyPhoto(gv)).willReturn(new AccountPhotoResponse(false, null, false));

        mvc.perform(delete("/api/accounts/me/photo").with(jwt().authorities(teacherRole())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUploaded").value(false));

        CurrentUser parent = TestPermissions.userWithDefaults(70L, CareNestRole.PARENT);
        given(users.getCurrentUser()).willReturn(parent);
        given(photos.deleteMyPhoto(parent)).willThrow(GlobalException.of(ApiCode.ACCOUNT_PHOTO_REQUIRED));

        mvc.perform(delete("/api/accounts/me/photo").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PARENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(1145));
    }

    @Test
    @DisplayName("AUTH-27: lấy link ảnh hàng loạt trả danh sách service lọc")
    void returnsPhotoUrls() throws Exception {
        CurrentUser gv = asTeacher();
        given(photos.findPhotoUrls(gv, List.of(30L, 31L)))
                .willReturn(List.of(new AccountPhotoUrlResponse(30L, "https://s3/a", AT)));

        mvc.perform(post("/api/accounts/photos/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[30,31]}")
                        .with(jwt().authorities(teacherRole())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(30))
                .andExpect(jsonPath("$.data[0].url").value("https://s3/a"));
    }

    @Test
    @DisplayName("AUTH-27: danh sách rỗng hoặc quá 100 người bị 400")
    void rejectsEmptyOrTooManyIds() throws Exception {
        asTeacher();
        String tooMany = LongStream.rangeClosed(1, 101)
                .mapToObj(Long::toString)
                .collect(Collectors.joining(",", "{\"userIds\":[", "]}"));

        for (String body : List.of("{\"userIds\":[]}", tooMany, "{\"userIds\":[0]}")) {
            mvc.perform(post("/api/accounts/photos/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(jwt().authorities(teacherRole())))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(photos);
    }

    @Test
    @DisplayName("AUTH-27: chưa đăng nhập bị 401")
    void anonymousRejected() throws Exception {
        mvc.perform(post("/api/accounts/photos/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[1]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(photos);
    }
}
