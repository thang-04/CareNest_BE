package com.carenest.controller.meals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.meals.AgeGroupMealConfigService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;
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

/** Lớp 1 của cấu hình nhóm tuổi: ghi cần MEAL_CONFIG_MANAGE (chỉ HT, NUT-17), đọc cho Ban giám hiệu. */
@WebMvcTest(AgeGroupMealConfigController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AgeGroupMealConfigControllerTest {

    private static final String CONFIGS = "/api/nutrition/age-group-configs";
    private static final String BATCH_BODY = """
            {"items":[{"ageGroupId":1,"effectiveFrom":"2026-12-01","mealPrice":32000}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private AgeGroupMealConfigService configService;

    @Test
    @DisplayName("NUT-17: PHT có NUTRITION_MANAGE vẫn chỉ xem, không lưu cấu hình nhóm tuổi")
    void vicePrincipalWithNutritionManageCannotSaveConfigs() throws Exception {
        authenticate(CareNestRole.VICE_PRINCIPAL, Set.of(Permission.NUTRITION_MANAGE));
        given(configService.getEffectiveConfigs(null)).willReturn(List.of());

        mockMvc.perform(get(CONFIGS).with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post(CONFIGS + "/batch")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BATCH_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(configService, never()).saveConfigs(any(), any());
    }

    @Test
    @DisplayName("NUT-17: HT có MEAL_CONFIG_MANAGE lưu được cấu hình nhóm tuổi")
    void principalSavesConfigs() throws Exception {
        CurrentUser user = authenticate(CareNestRole.PRINCIPAL, Permission.fixedPermissionsOf(CareNestRole.PRINCIPAL));
        given(configService.saveConfigs(eq(user), any())).willReturn(List.of());

        mockMvc.perform(post(CONFIGS + "/batch")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BATCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private CurrentUser authenticate(CareNestRole role, Set<Permission> permissions) {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo.user", "Demo", null, Set.of(role), permissions);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
