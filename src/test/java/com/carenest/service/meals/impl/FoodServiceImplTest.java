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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
                nutrientSource,
                null,
                BigDecimal.ZERO,
                FoodStorage.DRY,
                FoodOrigin.PLANT,
                null,
                List.of());
    }
}
