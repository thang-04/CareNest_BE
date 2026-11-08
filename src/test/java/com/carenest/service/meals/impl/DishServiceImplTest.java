package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.dto.meals.DishDetailResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.Food;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DishServiceImplTest {

    private static final long NURSERY = 2L;
    private static final long KINDERGARTEN = 5L;
    private static final long DISH_ID = 9L;

    private final DishRepository dishRepository = mock(DishRepository.class);
    private final DishServiceImpl service = new DishServiceImpl(
            dishRepository, mock(FoodRepository.class), mock(AgeGroupService.class), new NutritionCatalogMapper());

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

    private void stubDish(DishIngredient... ingredients) {
        Dish dish = Dish.create(1L);
        dish.changeInfo("M001", "Món thử", DishType.CARB, "", 1L);
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
