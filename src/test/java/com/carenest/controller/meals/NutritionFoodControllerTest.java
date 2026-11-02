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
import com.carenest.dto.meals.FoodResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.meals.FoodService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import com.carenest.utils.enums.Permission;
import java.math.BigDecimal;
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

/** Lớp 1 (role + mã quyền) + envelope của API thực phẩm bán trú. */
@WebMvcTest(NutritionFoodController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class NutritionFoodControllerTest {

    private static final String FOODS = "/api/nutrition/foods";
    private static final String BODY = """
            {"code":"TP001","name":"Gạo tẻ","foodGroup":"GRAIN","unit":"g","purchaseUnit":"kg",
             "purchaseUnitSize":1000,"unitPrice":18000,"kcal":344,"protein":7.9,"lipid":1,"glucid":76.2,
             "nutrientSource":"Bảng thành phần thực phẩm Việt Nam","wastePct":0,"storage":"DRY","allergenIds":[]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private FoodService foodService;

    @Test
    @DisplayName("AUTH-20: PHT không có NUTRITION_MANAGE xem được nhưng không tạo được thực phẩm")
    void vicePrincipalWithoutPermissionCanReadButNotCreate() throws Exception {
        authenticate(Set.of());
        given(foodService.getFoods(null, null, null, null)).willReturn(List.of(food()));

        mockMvc.perform(get(FOODS).with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].code").value("TP001"));
        mockMvc.perform(post(FOODS)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(foodService, never()).createFood(any(), any());
    }

    @Test
    @DisplayName("AUTH-20: PHT được HT cấp NUTRITION_MANAGE thì tạo được, trả 201")
    void vicePrincipalWithPermissionCreates() throws Exception {
        CurrentUser user = authenticate(Set.of(Permission.NUTRITION_MANAGE));
        given(foodService.createFood(eq(user), any())).willReturn(food());

        mockMvc.perform(post(FOODS)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.storage").value("DRY"));
    }

    private CurrentUser authenticate(Set<Permission> permissions) {
        CurrentUser user = new CurrentUser(
                7L, "kc-7", "demo.user", "Demo", null, Set.of(CareNestRole.VICE_PRINCIPAL), permissions);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token() {
        return jwt().authorities(new SimpleGrantedAuthority(CareNestRole.VICE_PRINCIPAL.authority()));
    }

    private static FoodResponse food() {
        return new FoodResponse(
                1L,
                "TP001",
                "Gạo tẻ",
                FoodGroup.GRAIN,
                FoodUnit.g,
                "kg",
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(18000),
                BigDecimal.valueOf(344),
                BigDecimal.valueOf(7.9),
                BigDecimal.ONE,
                BigDecimal.valueOf(76.2),
                "Bảng thành phần thực phẩm Việt Nam",
                null,
                BigDecimal.ZERO,
                FoodStorage.DRY,
                "",
                CatalogStatus.ACTIVE,
                List.of());
    }
}
