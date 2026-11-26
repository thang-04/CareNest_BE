package com.carenest.controller.meals;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.meals.AllergenResponse;
import com.carenest.dto.meals.ParentMenuResponse;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuDay;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuDish;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuMeal;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuPhoto;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.MealPhotoService;
import com.carenest.service.meals.ParentMenuService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ParentMenuController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ParentMenuControllerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ParentMenuService parentMenuService;

    @Test
    @DisplayName("PAR-14: trả thực đơn tuần; món và ảnh không kèm trường dành cho nhân sự")
    void parentReadsWeekWithoutStaffFields() throws Exception {
        CurrentUser parent = authenticate(CareNestRole.PARENT);
        AllergenResponse fish = new AllergenResponse(4L, "FISH", "Cá");
        given(parentMenuService.getWeek(eq(parent), eq(9L), eq(MONDAY)))
                .willReturn(new ParentMenuResponse(
                        9L,
                        MONDAY,
                        true,
                        false,
                        List.of("Dâu tây"),
                        List.of(new ParentMenuDay(
                                MONDAY,
                                true,
                                false,
                                List.of(new ParentMenuMeal(
                                        MealSession.LUNCH,
                                        List.of(new ParentMenuDish("Cá kho", List.of(fish), List.of(fish))),
                                        new ParentMenuPhoto(10L, Instant.parse("2026-11-09T04:05:00Z"))))))));

        mockMvc.perform(get("/api/children/9/menu?weekStart=2026-11-09").with(token(CareNestRole.PARENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", aMapWithSize(6)))
                .andExpect(jsonPath("$.data.unmatchedAllergies[0]").value("Dâu tây"))
                .andExpect(jsonPath("$.data.days[0]", aMapWithSize(4)))
                .andExpect(jsonPath("$.data.days[0].meals[0]", aMapWithSize(3)))
                .andExpect(jsonPath("$.data.days[0].meals[0].dishes[0]", aMapWithSize(3)))
                .andExpect(jsonPath("$.data.days[0].meals[0].dishes[0].childAllergens[0].code")
                        .value("FISH"))
                .andExpect(jsonPath("$.data.days[0].meals[0].photo", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.days[0].meals[0].photo.photoId").value(10));
    }

    @Test
    @DisplayName("PAR-01: bỏ weekStart được; AUTH-04: trẻ không phải con mình ⇒ 4108")
    void weekStartIsOptionalAndOtherChildIsForbidden() throws Exception {
        CurrentUser parent = authenticate(CareNestRole.PARENT);
        given(parentMenuService.getWeek(eq(parent), eq(9L), isNull()))
                .willReturn(new ParentMenuResponse(9L, MONDAY, false, false, List.of(), List.of()));
        given(parentMenuService.getWeek(eq(parent), eq(8L), isNull()))
                .willThrow(GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN));

        mockMvc.perform(get("/api/children/9/menu").with(token(CareNestRole.PARENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(false));
        mockMvc.perform(get("/api/children/8/menu").with(token(CareNestRole.PARENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(4108));
    }

    @Test
    @DisplayName("AUTH-04: nhân sự không gọi được API thực đơn của phụ huynh; không đăng nhập ⇒ 401")
    void staffAndAnonymousAreRejected() throws Exception {
        authenticate(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(get("/api/children/9/menu").with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/children/9/menu")).andExpect(status().isUnauthorized());
        verifyNoInteractions(parentMenuService);
    }

    @Test
    @DisplayName("NUT-16: liên kết ảnh có envelope; nội dung ảnh không lưu cache và không đoán kiểu")
    void parentLoadsMealPhoto() throws Exception {
        CurrentUser parent = authenticate(CareNestRole.PARENT);
        given(parentMenuService.getPhotoUrl(eq(parent), eq(9L), eq(10L)))
                .willReturn(new FileUrlResponse("https://storage.test/anh", Instant.parse("2026-11-09T04:20:00Z")));
        given(parentMenuService.getPhotoContent(eq(parent), eq(9L), eq(10L)))
                .willReturn(new MealPhotoService.Content(new ByteArrayResource(new byte[] {1, 2, 3}), "image/jpeg"));

        mockMvc.perform(get("/api/children/9/meal-photos/10/url").with(token(CareNestRole.PARENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://storage.test/anh"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-11-09T04:20:00Z"));
        mockMvc.perform(get("/api/children/9/meal-photos/10/content").with(token(CareNestRole.PARENT)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(
                        header().string("Cache-Control", allOf(containsString("no-store"), containsString("private"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("PAR-14: ảnh không hiện cho con ⇒ 404 6068; nhân sự không gọi được API ảnh của phụ huynh")
    void hiddenPhotoIsNotFoundAndStaffAreRejected() throws Exception {
        CurrentUser parent = authenticate(CareNestRole.PARENT);
        given(parentMenuService.getPhotoContent(eq(parent), eq(9L), eq(11L)))
                .willThrow(GlobalException.of(ApiCode.MEAL_PHOTO_NOT_FOUND));
        mockMvc.perform(get("/api/children/9/meal-photos/11/content").with(token(CareNestRole.PARENT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(6068));

        authenticate(CareNestRole.KITCHEN_STAFF);
        mockMvc.perform(get("/api/children/9/meal-photos/10/url").with(token(CareNestRole.KITCHEN_STAFF)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/children/9/meal-photos/10/content").with(token(CareNestRole.KITCHEN_STAFF)))
                .andExpect(status().isForbidden());
    }

    private CurrentUser authenticate(CareNestRole role) {
        CurrentUser user = TestPermissions.userWithDefaults(5L, role);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
