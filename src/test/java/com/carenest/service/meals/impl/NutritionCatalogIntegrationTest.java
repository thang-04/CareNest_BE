package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.meals.DishCopyRequest;
import com.carenest.dto.meals.DishDetailResponse;
import com.carenest.dto.meals.DishRequest;
import com.carenest.dto.meals.FoodImportRequest;
import com.carenest.dto.meals.FoodImportResponse;
import com.carenest.dto.meals.FoodRequest;
import com.carenest.dto.meals.FoodResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.DishService;
import com.carenest.service.meals.FoodService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import com.carenest.utils.enums.Permission;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Danh mục bán trú trên PostgreSQL thật: V54, V55, mapping JPA, cách tính dinh dưỡng/giá, món tham khảo. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class NutritionCatalogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private FoodService foodService;

    @Autowired
    private DishService dishService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser manager;
    private long nursery;
    private long kindergarten;

    @BeforeEach
    void setUp() {
        Long userId = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                        + "VALUES ('kc-nutri', 'nutri.vp', 'Demo') RETURNING id",
                Long.class);
        manager = new CurrentUser(
                userId,
                "kc-nutri",
                "nutri.vp",
                "Demo",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.NUTRITION_MANAGE));
        nursery = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-2'", Long.class);
        kindergarten = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-5'", Long.class);
    }

    @Test
    @DisplayName("NUT-18: dinh dưỡng và giá một suất tính riêng từng nhóm tuổi; chất dị ứng suy từ thực phẩm")
    void dishNutritionIsComputedPerAgeGroup() {
        Long peanutId = jdbc.queryForObject("SELECT id FROM allergens WHERE code = 'PEANUT'", Long.class);
        // 100 g gạo = 344 kcal; 18.000đ/kg, không thải bỏ
        FoodResponse rice = foodService.createFood(manager, food("TP001", "Gạo tẻ", "344", "18000", "0", List.of()));
        // 100 g đậu phộng = 567 kcal; 60.000đ/kg, thải bỏ 10%
        FoodResponse peanut =
                foodService.createFood(manager, food("TP002", "Đậu phộng", "567", "60000", "10", List.of(peanutId)));

        DishDetailResponse dish = dishService.createDish(
                manager,
                new DishRequest(
                        "M001",
                        "Xôi đậu phộng",
                        DishType.CARB,
                        null,
                        List.of(
                                new DishRequest.Ingredient(rice.id(), nursery, new BigDecimal("50")),
                                new DishRequest.Ingredient(peanut.id(), nursery, new BigDecimal("10")),
                                new DishRequest.Ingredient(rice.id(), kindergarten, new BigDecimal("80")))));

        DishDetailResponse.AgeGroupNutrition small = nutritionOf(dish, nursery);
        DishDetailResponse.AgeGroupNutrition big = nutritionOf(dish, kindergarten);
        // 50 × 3,44 + 10 × 5,67 = 228,70 kcal; giá 50 × 18 + 10 ÷ 0,9 × 60 = 1.566,67đ (lượng mua = ăn ÷ (1 − thải bỏ))
        assertThat(small.kcal()).isEqualByComparingTo("228.70");
        assertThat(small.cost()).isEqualByComparingTo("1566.67");
        assertThat(big.kcal()).isEqualByComparingTo("275.20");
        assertThat(dish.allergens()).extracting("code").containsExactly("PEANUT");
    }

    @Test
    @DisplayName("NUT-24: đạm, béo tách theo nguồn gốc thực phẩm; tổng = động vật + thực vật")
    void proteinAndLipidSplitByFoodOrigin() {
        // helper: đạm 10 g, béo 1 g trên 100 g
        FoodResponse rice = foodService.createFood(manager, food("TP001", "Gạo tẻ", "344", "18000", "0", List.of()));
        FoodResponse pork = foodService.createFood(
                manager, food("TP002", "Thịt lợn", "139", "140000", "0", List.of(), FoodOrigin.ANIMAL));
        DishDetailResponse dish = dishService.createDish(
                manager,
                new DishRequest(
                        null,
                        "Cơm thịt",
                        DishType.MAIN,
                        null,
                        List.of(
                                new DishRequest.Ingredient(rice.id(), nursery, new BigDecimal("50")),
                                new DishRequest.Ingredient(pork.id(), nursery, new BigDecimal("30")))));

        DishDetailResponse.AgeGroupNutrition nutrition = nutritionOf(dish, nursery);
        assertThat(nutrition.animalProtein()).isEqualByComparingTo("3.00");
        assertThat(nutrition.plantProtein()).isEqualByComparingTo("5.00");
        assertThat(nutrition.protein()).isEqualByComparingTo("8.00");
        assertThat(nutrition.animalLipid()).isEqualByComparingTo("0.30");
        assertThat(nutrition.plantLipid()).isEqualByComparingTo("0.50");
        assertThat(nutrition.lipid()).isEqualByComparingTo("0.80");
        assertThat(pork.origin()).isEqualTo(FoodOrigin.ANIMAL);
    }

    @Test
    @DisplayName("NUT-05: nhập/sửa hàng loạt một lần; một dòng lỗi thì không dòng nào được lưu")
    void bulkImportUpdatesAndCreatesAtomically() {
        FoodResponse rice = foodService.createFood(manager, food("TP001", "Gạo tẻ", "344", "18000", "0", List.of()));
        FoodResponse beans = foodService.createFood(manager, food("TP002", "Đậu xanh", "328", "40000", "0", List.of()));

        assertThatThrownBy(() -> foodService.importFoods(
                        manager,
                        new FoodImportRequest(List.of(
                                new FoodImportRequest.Item(
                                        rice.id(), food(null, "Gạo tẻ thơm", "344", "19000", "0", List.of())),
                                new FoodImportRequest.Item(
                                        null, food("TP002", "Trùng mã", "100", "1000", "0", List.of()))))))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("dòng 2 trùng mã TP002");
        assertThat(foodService.getFood(rice.id()).name()).isEqualTo("Gạo tẻ");

        FoodImportResponse result = foodService.importFoods(
                manager,
                new FoodImportRequest(List.of(
                        new FoodImportRequest.Item(
                                rice.id(), food(null, "Gạo tẻ thơm", "344", "19000", "0", List.of())),
                        new FoodImportRequest.Item(
                                beans.id(), food("TP002", "Đậu xanh cà", "328", "40000", "0", List.of())),
                        new FoodImportRequest.Item(null, food(null, "Gạo nếp", "346", "26000", "0", List.of())))));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(2);
        assertThat(foodService.getFood(rice.id()).name()).isEqualTo("Gạo tẻ thơm");
        assertThat(foodService.getFood(rice.id()).code()).isEqualTo("TP001");
    }

    @Test
    void dishListReturnsIngredientsAndNutrition() {
        FoodResponse rice = foodService.createFood(manager, food("TP001", "Gạo tẻ", "344", "18000", "0", List.of()));
        dishService.createDish(
                manager,
                new DishRequest(
                        null,
                        "Cơm",
                        DishType.CARB,
                        null,
                        List.of(new DishRequest.Ingredient(rice.id(), nursery, new BigDecimal("50")))));

        assertThat(dishService.getDishes(null, null, false, null))
                .singleElement()
                .satisfies(dish -> {
                    assertThat(dish.ingredients()).hasSize(1);
                    assertThat(dish.nutritionByAgeGroup())
                            .singleElement()
                            .satisfies(n -> assertThat(n.kcal()).isEqualByComparingTo("172.00"));
                });
    }

    @Test
    void blankCodeIsGeneratedAndKeptOnUpdate() {
        FoodResponse manual = foodService.createFood(manager, food("TP001", "Gạo tẻ", "344", "18000", "0", List.of()));
        FoodResponse generated = foodService.createFood(manager, food(null, "Gạo nếp", "346", "26000", "0", List.of()));
        FoodResponse renamed = foodService.updateFood(
                manager, generated.id(), food(" ", "Gạo nếp cái", "346", "26000", "0", List.of()));
        DishDetailResponse dish = dishService.createDish(
                manager,
                new DishRequest(
                        null,
                        "Xôi",
                        DishType.CARB,
                        null,
                        List.of(new DishRequest.Ingredient(manual.id(), nursery, new BigDecimal("50")))));

        assertThat(generated.code()).isEqualTo("TP002");
        assertThat(renamed.code()).isEqualTo("TP002");
        assertThat(dish.code()).isEqualTo("MA001");
    }

    @Test
    void updateReplacesWholeIngredientGrid() {
        FoodResponse rice = foodService.createFood(manager, food("TP001", "Gạo tẻ", "344", "18000", "0", List.of()));
        DishDetailResponse dish = dishService.createDish(
                manager,
                new DishRequest(
                        "M001",
                        "Cơm trắng",
                        DishType.CARB,
                        null,
                        List.of(new DishRequest.Ingredient(rice.id(), nursery, new BigDecimal("50")))));

        // Cùng ô (món, thực phẩm, nhóm tuổi) với giá trị mới: không vướng unique khi thay lưới
        DishDetailResponse updated = dishService.updateDish(
                manager,
                dish.id(),
                new DishRequest(
                        "M001",
                        "Cơm trắng",
                        DishType.CARB,
                        null,
                        List.of(new DishRequest.Ingredient(rice.id(), nursery, new BigDecimal("60")))));

        assertThat(updated.ingredients())
                .singleElement()
                .satisfies(item -> assertThat(item.gramsPerChild()).isEqualByComparingTo("60"));
    }

    @Test
    @DisplayName("NUT-18: món tham khảo chỉ đọc; sao chép thành món của trường thì sửa được")
    void referenceDishIsReadOnlyButCopyable() {
        FoodResponse rice = foodService.createFood(manager, food("TP001", "Gạo tẻ", "344", "18000", "0", List.of()));
        Long referenceId = jdbc.queryForObject(
                "INSERT INTO dishes (code, name, dish_type, reference) VALUES ('REF01', 'Cơm mẫu', 'CARB', TRUE) "
                        + "RETURNING id",
                Long.class);
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, 70)",
                referenceId,
                rice.id(),
                nursery);
        DishRequest edit = new DishRequest(
                "REF01",
                "Cơm sửa",
                DishType.CARB,
                null,
                List.of(new DishRequest.Ingredient(rice.id(), nursery, new BigDecimal("40"))));

        assertThatThrownBy(() -> dishService.updateDish(manager, referenceId, edit))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.DISH_REFERENCE_READ_ONLY);

        DishDetailResponse copy = dishService.copyDish(manager, referenceId, new DishCopyRequest("M002", "Cơm trường"));
        assertThat(copy.reference()).isFalse();
        assertThat(copy.ingredients())
                .singleElement()
                .satisfies(item -> assertThat(item.gramsPerChild()).isEqualByComparingTo("70"));
    }

    private static DishDetailResponse.AgeGroupNutrition nutritionOf(DishDetailResponse dish, long ageGroupId) {
        return dish.nutritionByAgeGroup().stream()
                .filter(item -> item.ageGroupId() == ageGroupId)
                .findFirst()
                .orElseThrow();
    }

    private static FoodRequest food(
            String code, String name, String kcal, String price, String wastePct, List<Long> allergenIds) {
        return food(code, name, kcal, price, wastePct, allergenIds, FoodOrigin.PLANT);
    }

    private static FoodRequest food(
            String code,
            String name,
            String kcal,
            String price,
            String wastePct,
            List<Long> allergenIds,
            FoodOrigin origin) {
        return new FoodRequest(
                code,
                name,
                FoodGroup.GRAIN,
                FoodUnit.g,
                "kg",
                new BigDecimal("1000"),
                new BigDecimal(price),
                new BigDecimal(kcal),
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "Bảng thành phần thực phẩm Việt Nam",
                "2017",
                new BigDecimal(wastePct),
                FoodStorage.DRY,
                origin,
                null,
                allergenIds);
    }
}
