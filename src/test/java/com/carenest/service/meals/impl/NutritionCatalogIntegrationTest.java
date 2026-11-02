package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.meals.DishCopyRequest;
import com.carenest.dto.meals.DishDetailResponse;
import com.carenest.dto.meals.DishRequest;
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

/** Danh mục bán trú trên PostgreSQL thật: V41, mapping JPA, cách tính dinh dưỡng/giá, món tham khảo. */
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
        // 50 × 3,44 + 10 × 5,67 = 228,70 kcal; giá 50 × 18 + 10 × 1,1 × 60 = 1.560đ
        assertThat(small.kcal()).isEqualByComparingTo("228.70");
        assertThat(small.cost()).isEqualByComparingTo("1560.00");
        assertThat(big.kcal()).isEqualByComparingTo("275.20");
        assertThat(dish.allergens()).extracting("code").containsExactly("PEANUT");
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
                null,
                allergenIds);
    }
}
