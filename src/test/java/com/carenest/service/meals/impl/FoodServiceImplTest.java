package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.meals.FoodImportRequest;
import com.carenest.dto.meals.FoodRequest;
import com.carenest.dto.meals.FoodResponse;
import com.carenest.entity.meals.Food;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.AllergenRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.security.CurrentUser;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
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
import org.mockito.ArgumentCaptor;

class FoodServiceImplTest {

    private static final CurrentUser VICE_PRINCIPAL =
            new CurrentUser(3L, "kc-3", "viceprincipal.a", "Demo", null, Set.of(CareNestRole.VICE_PRINCIPAL));

    private final FoodRepository foodRepository = mock(FoodRepository.class);
    private final AllergenRepository allergenRepository = mock(AllergenRepository.class);
    private final FoodServiceImpl service =
            new FoodServiceImpl(foodRepository, allergenRepository, new NutritionCatalogMapper());

    @Test
    @DisplayName("NUT-05: thực phẩm thiếu nguồn dinh dưỡng thì không lưu")
    void createRejectsMissingNutrientSource() {
        assertThatThrownBy(() -> service.createFood(VICE_PRINCIPAL, food("TP001", null)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.NUTRIENT_SOURCE_REQUIRED);
        verify(foodRepository, never()).save(any());
    }

    @Test
    @DisplayName("NUT-24: thực phẩm thiếu nguồn gốc động vật / thực vật thì không lưu")
    void createRejectsMissingOrigin() {
        FoodRequest valid = food("TP001", "Viện Dinh dưỡng 2017");
        FoodRequest noOrigin = new FoodRequest(
                valid.code(),
                valid.name(),
                valid.foodGroup(),
                valid.unit(),
                valid.purchaseUnit(),
                valid.purchaseUnitSize(),
                valid.unitPrice(),
                valid.kcal(),
                valid.protein(),
                valid.lipid(),
                valid.glucid(),
                valid.calcium(),
                valid.phosphorus(),
                valid.iron(),
                valid.vitaminA(),
                valid.vitaminB1(),
                valid.vitaminB2(),
                valid.vitaminPp(),
                valid.vitaminC(),
                valid.nutrientSource(),
                null,
                valid.wastePct(),
                valid.storage(),
                null,
                null,
                List.of());

        assertThatThrownBy(() -> service.createFood(VICE_PRINCIPAL, noOrigin))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FOOD_ORIGIN_REQUIRED);
        verify(foodRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicatedCode() {
        given(foodRepository.existsByCode("TP001")).willReturn(true);

        assertThatThrownBy(() -> service.createFood(VICE_PRINCIPAL, food("TP001", "Viện Dinh dưỡng 2017")))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FOOD_CODE_DUPLICATED);
    }

    @Test
    @DisplayName("NUT-05: nhập hàng loạt có một dòng lỗi thì không dòng nào được lưu, báo số dòng")
    void importRejectsWholeBatchWhenOneRowInvalid() {
        given(foodRepository.findByCodeIn(anyCollection())).willReturn(List.of());
        given(allergenRepository.findAll()).willReturn(List.of());
        FoodImportRequest request = new FoodImportRequest(List.of(
                new FoodImportRequest.Item(null, food("TP001", "Viện Dinh dưỡng 2017")),
                new FoodImportRequest.Item(null, food("TP002", " ")),
                new FoodImportRequest.Item(null, food("TP001", "Viện Dinh dưỡng"))));

        assertThatThrownBy(() -> service.importFoods(VICE_PRINCIPAL, request))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> {
                    assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.FOOD_IMPORT_INVALID);
                    assertThat(ex.getMessage()).contains("dòng 2 thiếu nguồn dinh dưỡng", "dòng 3 trùng mã TP001");
                });
        verify(foodRepository, never()).saveAll(any());
    }

    @Test
    void importSavesEveryValidRow() {
        given(foodRepository.findByCodeIn(anyCollection())).willReturn(List.of());
        given(allergenRepository.findAll()).willReturn(List.of());

        int created = service.importFoods(
                        VICE_PRINCIPAL,
                        new FoodImportRequest(List.of(
                                new FoodImportRequest.Item(null, food("TP001", "Viện Dinh dưỡng 2017")),
                                new FoodImportRequest.Item(null, food("TP002", "Viện Dinh dưỡng 2017")))))
                .created();

        assertThat(created).isEqualTo(2);
        verify(foodRepository).saveAll(any());
    }

    private static FoodRequest food(String code, String nutrientSource) {
        return food(code, nutrientSource, null, null);
    }

    @Test
    @DisplayName("NUT-26: tạo thực phẩm lưu đủ 8 vi chất; vi chất để trống lưu null, không chặn")
    void createPersistsMicronutrients() {
        given(foodRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        FoodRequest request = new FoodRequest(
                "TP001",
                "Cà rốt",
                FoodGroup.VEGETABLE,
                FoodUnit.g,
                "kg",
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(25000),
                BigDecimal.valueOf(39),
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(0.2),
                BigDecimal.valueOf(7.6),
                new BigDecimal("43"),
                new BigDecimal("39"),
                new BigDecimal("0.8"),
                new BigDecimal("840"),
                new BigDecimal("0.06"),
                new BigDecimal("0.02"),
                new BigDecimal("0.4"),
                null,
                "Viện Dinh dưỡng 2017",
                null,
                BigDecimal.ZERO,
                FoodStorage.FRESH,
                FoodOrigin.PLANT,
                null,
                List.of());

        FoodResponse created = service.createFood(VICE_PRINCIPAL, request);

        assertThat(created.calcium()).isEqualByComparingTo("43");
        assertThat(created.phosphorus()).isEqualByComparingTo("39");
        assertThat(created.iron()).isEqualByComparingTo("0.8");
        assertThat(created.vitaminA()).isEqualByComparingTo("840");
        assertThat(created.vitaminB1()).isEqualByComparingTo("0.06");
        assertThat(created.vitaminB2()).isEqualByComparingTo("0.02");
        assertThat(created.vitaminPp()).isEqualByComparingTo("0.4");
        assertThat(created.vitaminC()).isNull();
    }

    @Test
    @DisplayName("NUT-26: sửa thực phẩm đổi vi chất, gửi null thì xóa số liệu cũ")
    void updateChangesAndClearsMicronutrients() {
        Food existing = Food.create(3L);
        existing.changeInfo(
                new Food.Info(
                        "TP001",
                        "Gạo tẻ",
                        FoodGroup.GRAIN,
                        FoodUnit.g,
                        "kg",
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(18000),
                        BigDecimal.ZERO,
                        FoodStorage.DRY,
                        FoodOrigin.PLANT,
                        ""),
                new Food.Nutrients(
                        BigDecimal.valueOf(344),
                        BigDecimal.valueOf(7.9),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(76.2),
                        "Viện Dinh dưỡng 2017",
                        null),
                new Food.Micronutrients(
                        new BigDecimal("30"), null, new BigDecimal("1.3"), null, null, null, null, new BigDecimal("5")),
                Set.of(),
                3L);
        given(foodRepository.findWithAllergensById(7L)).willReturn(Optional.of(existing));

        FoodResponse updated = service.updateFood(
                VICE_PRINCIPAL, 7L, food("TP001", "Viện Dinh dưỡng 2017", new BigDecimal("32"), null));

        assertThat(updated.calcium()).isEqualByComparingTo("32");
        assertThat(updated.iron()).isNull();
        assertThat(updated.vitaminC()).isNull();
    }

    @Test
    @DisplayName("NUT-26: nhập hàng loạt lưu vi chất của từng dòng")
    void importPersistsMicronutrients() {
        given(foodRepository.findByCodeIn(anyCollection())).willReturn(List.of());
        given(allergenRepository.findAll()).willReturn(List.of());

        service.importFoods(
                VICE_PRINCIPAL,
                new FoodImportRequest(List.of(
                        new FoodImportRequest.Item(
                                null, food("TP001", "Viện Dinh dưỡng 2017", new BigDecimal("30"), new BigDecimal("2"))),
                        new FoodImportRequest.Item(null, food("TP002", "Viện Dinh dưỡng 2017")))));

        ArgumentCaptor<List<Food>> saved = ArgumentCaptor.captor();
        verify(foodRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Food::getCalcium).containsExactly(new BigDecimal("30"), null);
        assertThat(saved.getValue()).extracting(Food::getVitaminC).containsExactly(new BigDecimal("2"), null);
    }

    // Chỉ canxi và vitamin C có số liệu, 6 vi chất còn lại để trống
    private static FoodRequest food(String code, String nutrientSource, BigDecimal calcium, BigDecimal vitaminC) {
        return new FoodRequest(
                code,
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
                calcium,
                null,
                null,
                null,
                null,
                null,
                null,
                vitaminC,
                nutrientSource,
                null,
                BigDecimal.ZERO,
                FoodStorage.DRY,
                FoodOrigin.PLANT,
                null,
                List.of());
    }
}
