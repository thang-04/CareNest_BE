package com.carenest.controller.organization;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.organization.AppearanceResponse;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.organization.SchoolAppearanceService;
import com.carenest.utils.enums.AppearanceMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SchoolAppearanceController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SchoolAppearanceControllerTest {
    private static final String BODY = "{\"mode\":\"AUTO\",\"enabledSceneIds\":[\"T0\"]}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SchoolAppearanceService service;

    @MockitoBean
    private CurrentUserService currentUsers;

    @Test
    @DisplayName("Màn đăng nhập đọc được tranh nền của trường không cần token")
    void readableWithoutToken() throws Exception {
        given(service.getAppearance(false))
                .willReturn(new AppearanceResponse(AppearanceMode.FIXED, "T3", List.of("T3"), null, null));

        mvc.perform(get("/api/school/appearance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("FIXED"))
                .andExpect(jsonPath("$.data.pinnedSceneId").value("T3"));
    }

    @Test
    @DisplayName("Đổi tranh nền vẫn cần đăng nhập: không token ⇒ 401, không gọi service")
    void saveWithoutTokenIsRejected() throws Exception {
        mvc.perform(put("/api/school/appearance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Đổi tranh nền: giáo viên bị từ chối 403, không gọi service")
    void saveByTeacherIsForbidden() throws Exception {
        mvc.perform(put("/api/school/appearance")
                        .with(jwt().authorities(() -> "ROLE_TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
