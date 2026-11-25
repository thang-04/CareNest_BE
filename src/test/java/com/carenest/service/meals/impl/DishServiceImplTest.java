package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.meals.DishCopyRequest;
import com.carenest.dto.meals.DishDetailResponse;
import com.carenest.dto.meals.DishRequest;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.entity.meals.MenuTemplateSet;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.repository.meals.MenuTemplateSetRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.FoodPriceService;
import com.carenest.service.meals.FoodPriceService.FoodPriceBook;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DishServiceImplTest {

    private static final long NURSERY = 2L;
    private static final long KINDERGARTEN = 5L;
    private static final long DISH_ID = 9L;
    private static final CurrentUser USER =
            new CurrentUser(3L, "kc-3", "viceprincipal.a", "Demo", null, Set.of(CareNestRole.VICE_PRINCIPAL));

    private final DishRepository dishRepository = mock(DishRepository.class);
    private final FoodPriceService foodPriceService = mock(FoodPriceService.class);
    private final MenuTemplateSetRepository menuTemplateSetRepository = mock(MenuTemplateSetRepository.class);
    private final MealPlanWeekRepository mealPlanWeekRepository = mock(MealPlanWeekRepository.class);
    private final DishServiceImpl service = new DishServiceImpl(
            dishRepository,
            menuTemplateSetRepository,
            mealPlanWeekRepository,
            mock(FoodRepository.class),
            mock(AgeGroupService.class),
            new NutritionCatalogMapper(),
            foodPriceService,
            Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

    @BeforeEach
    void emptyPriceBook() {
        given(foodPriceService.book(any()))
                .willReturn(new FoodPriceBook(Map.of(), Map.of(), LocalDate.of(2026, 11, 18)));
    }

    // Cơm: canxi 30, photpho 104, sắt 1,3, A 0, B1 0,10, B2 0,03, PP 1,6, C 0 /100 g
    private static final Food.Micronutrients RICE = new Food.Micronutrients(
            bd("30"), bd("104"), bd("1.3"), bd("0"), bd("0.10"), bd("0.03"), bd("1.6"), bd("0"));
    // Cà rốt: canxi 43, photpho 39, sắt 0,8, A 840, B1 0,06, B2 0,02, PP 0,4, C 8 /100 g
    private static final Food.Micronutrients CARROT = new Food.Micronutrients(
            bd("43"), bd("39"), bd("0.8"), bd("840"), bd("0.06"), bd("0.02"), bd("0.4"), bd("8"));

    @Test
    @DisplayName("NUT-26: 8 vi chất một suất = Σ lượng ăn được × giá trị/100, tính riêng từng nhóm tuổi")
    void micronutrientsAreSummedPerAgeGroup() {
        Food rice = food("Gạo tẻ", RICE);
        Food carrot = food("Cà rốt", CARROT);
        stubDish(
                DishIngredient.create(rice, NURSERY, bd("50")),
                DishIngredient.create(carrot, NURSERY, bd("30")),
                DishIngredient.create(rice, KINDERGARTEN, bd("80")));

        DishDetailResponse dish = service.getDish(DISH_ID);

        DishDetailResponse.AgeGroupNutrition small = nutritionOf(dish, NURSERY);
        // 50 g × 30/100 + 30 g × 43/100 = 27,9 mg canxi; B1 0,05 + 0,018 = 0,068 làm tròn 0,07
        assertThat(small.calcium()).isEqualByComparingTo("27.90");
        assertThat(small.phosphorus()).isEqualByComparingTo("63.70");
        assertThat(small.iron()).isEqualByComparingTo("0.89");
        assertThat(small.vitaminA()).isEqualByComparingTo("252.00");
        assertThat(small.vitaminB1()).isEqualByComparingTo("0.07");
        assertThat(small.vitaminB2()).isEqualByComparingTo("0.02");
        assertThat(small.vitaminPp()).isEqualByComparingTo("0.92");
        assertThat(small.vitaminC()).isEqualByComparingTo("2.40");
        assertThat(nutritionOf(dish, KINDERGARTEN).calcium()).isEqualByComparingTo("24.00");
    }

    @Test
    @DisplayName("NUT-26: một nguyên liệu thiếu số liệu vi chất nào thì tổng vi chất đó null, vi chất khác vẫn tính")
    void ingredientMissingMicronutrientMakesThatTotalNull() {
        Food rice = food("Gạo tẻ", RICE);
        Food mushroom = food(
                "Nấm hương khô",
                new Food.Micronutrients(bd("10"), null, bd("1"), bd("0"), bd("0.1"), bd("0.1"), bd("1"), null));
        stubDish(
                DishIngredient.create(rice, NURSERY, bd("50")),
                DishIngredient.create(mushroom, NURSERY, bd("10")),
                DishIngredient.create(rice, KINDERGARTEN, bd("80")));

        DishDetailResponse dish = service.getDish(DISH_ID);

        DishDetailResponse.AgeGroupNutrition small = nutritionOf(dish, NURSERY);
        assertThat(small.phosphorus()).isNull();
        assertThat(small.vitaminC()).isNull();
        assertThat(small.calcium()).isEqualByComparingTo("16.00");
        // Nhóm tuổi khác không có nguyên liệu thiếu số liệu thì vẫn đủ
        assertThat(nutritionOf(dish, KINDERGARTEN).phosphorus()).isEqualByComparingTo("83.20");
        assertThat(nutritionOf(dish, KINDERGARTEN).vitaminC()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("NUT-26: thực phẩm chưa có số liệu nào ⇒ cả 8 vi chất null, kcal/đạm/béo/bột đường vẫn tính")
    void foodWithoutAnyMicronutrientDoesNotBlockMacros() {
        stubDish(DishIngredient.create(
                food("Dầu ăn", new Food.Micronutrients(null, null, null, null, null, null, null, null)),
                NURSERY,
                bd("5")));

        DishDetailResponse.AgeGroupNutrition nutrition = nutritionOf(service.getDish(DISH_ID), NURSERY);

        assertThat(nutrition.kcal()).isEqualByComparingTo("0.17");
        assertThat(nutrition.calcium()).isNull();
        assertThat(nutrition.phosphorus()).isNull();
        assertThat(nutrition.iron()).isNull();
        assertThat(nutrition.vitaminA()).isNull();
        assertThat(nutrition.vitaminB1()).isNull();
        assertThat(nutrition.vitaminB2()).isNull();
        assertThat(nutrition.vitaminPp()).isNull();
        assertThat(nutrition.vitaminC()).isNull();
    }

    @Test
    @DisplayName("NUT-18: tạo món trùng tên (bỏ dấu, hoa thường, khoảng trắng) ⇒ 6109, kể cả món đã ngừng")
    void createRejectsDuplicatedName() {
        Dish existing = namedDish("Cơm trắng", 3L);
        existing.changeStatus(CatalogStatus.INACTIVE, 1L);
        given(dishRepository.findAllByOrderByNameAsc()).willReturn(List.of(existing));

        assertThatThrownBy(() -> service.createDish(USER, dishRequest("  COM   TRANG ")))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.DISH_NAME_DUPLICATED);
        verify(dishRepository, never()).save(any());
    }

    @Test
    @DisplayName("NUT-18: sửa món giữ nguyên tên của chính nó thì được, đổi sang tên món khác ⇒ 6109")
    void updateIgnoresItselfButRejectsOtherDish() {
        Dish self = namedDish("Cơm trắng", DISH_ID);
        Dish other = namedDish("Canh bí", 4L);
        given(dishRepository.findWithIngredientsById(DISH_ID)).willReturn(Optional.of(self));
        given(dishRepository.findAllByOrderByNameAsc()).willReturn(List.of(other, self));

        assertThatThrownBy(() -> service.updateDish(USER, DISH_ID, dishRequest("canh bí")))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.DISH_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("NUT-18: sao chép món sang tên đã có ⇒ 6109")
    void copyRejectsDuplicatedName() {
        Dish source = namedDish("Cơm trắng", DISH_ID);
        given(dishRepository.findWithIngredientsById(DISH_ID)).willReturn(Optional.of(source));
        given(dishRepository.findAllByOrderByNameAsc()).willReturn(List.of(source));

        assertThatThrownBy(() -> service.copyDish(USER, DISH_ID, new DishCopyRequest(null, "Cơm trắng")))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.DISH_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("Chi tiết món liệt kê thực đơn mẫu đang dùng và tuần chưa qua có món")
    void detailListsWhereDishIsUsed() {
        stubDish(DishIngredient.create(food("Gạo tẻ", RICE), NURSERY, bd("50")));
        MenuTemplateSet template = MenuTemplateSet.create(1L);
        template.changeInfo("Mẫu mùa đông", "Đông", MenuBlock.NURSERY, 1L);
        given(menuTemplateSetRepository.findUsingDish(DISH_ID, CatalogStatus.ACTIVE))
                .willReturn(List.of(template));
        given(mealPlanWeekRepository.findFromWeekUsingDish(DISH_ID, LocalDate.of(2026, 11, 16)))
                .willReturn(List.of(MealPlanWeek.draft(LocalDate.of(2026, 11, 30), 1L)));

        DishDetailResponse dish = service.getDish(DISH_ID);

        assertThat(dish.usedInTemplates())
                .extracting(DishDetailResponse.TemplateUsage::name)
                .containsExactly("Mẫu mùa đông");
        assertThat(dish.usedInWeeks())
                .extracting(DishDetailResponse.WeekUsage::weekStart)
                .containsExactly(LocalDate.of(2026, 11, 30));
    }

    private static Dish namedDish(String name, Long id) {
        Dish dish = Dish.create(1L);
        dish.changeInfo("M" + id, name, DishType.CARB, "", 1L);
        ReflectionTestUtils.setField(dish, "id", id);
        return dish;
    }

    private static DishRequest dishRequest(String name) {
        return new DishRequest(
                null, name, DishType.CARB, null, List.of(new DishRequest.Ingredient(1L, NURSERY, bd("10"))));
    }

    private void stubDish(DishIngredient... ingredients) {
        Dish dish = Dish.create(1L);
        dish.changeInfo("M001", "Món thử", DishType.CARB, "", 1L);
        // id phải khớp stub findWithIngredientsById để usageOf tra đúng nơi đang dùng
        ReflectionTestUtils.setField(dish, "id", DISH_ID);
        dish.replaceIngredients(List.of(ingredients));
        given(dishRepository.findWithIngredientsById(DISH_ID)).willReturn(Optional.of(dish));
    }

    private static Food food(String name, Food.Micronutrients micro) {
        Food food = Food.create(1L);
        food.changeInfo(
                new Food.Info(
                        "TP001",
                        name,
                        FoodGroup.GRAIN,
                        FoodUnit.g,
                        "kg",
                        bd("1000"),
                        bd("18000"),
                        BigDecimal.ZERO,
                        FoodStorage.DRY,
                        FoodOrigin.PLANT,
                        ""),
                new Food.Nutrients(bd("3.44"), bd("7.9"), bd("1"), bd("76.2"), "Viện Dinh dưỡng 2017", null),
                micro,
                Set.of(),
                1L);
        return food;
    }

    private static DishDetailResponse.AgeGroupNutrition nutritionOf(DishDetailResponse dish, long ageGroupId) {
        return dish.nutritionByAgeGroup().stream()
                .filter(n -> n.ageGroupId() == ageGroupId)
                .findFirst()
                .orElseThrow();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
