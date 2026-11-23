package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.meals.AgeGroupMealConfigRequest;
import com.carenest.dto.meals.AgeGroupMealConfigResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.AgeGroupMealConfigRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AgeGroupMealConfigServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final CurrentUser VICE_PRINCIPAL =
            new CurrentUser(3L, "kc-3", "viceprincipal.a", "Demo", null, Set.of(CareNestRole.VICE_PRINCIPAL));
    private static final long AGE_GROUP = 4L;

    private final AgeGroupMealConfigRepository repository = mock(AgeGroupMealConfigRepository.class);
    private final AgeGroupService ageGroupService = mock(AgeGroupService.class);
    private final AgeGroupMealConfigServiceImpl service =
            new AgeGroupMealConfigServiceImpl(repository, ageGroupService, new NutritionCatalogMapper(), FIXED_CLOCK);

    @Test
    @DisplayName("NUT-17: lưu cả lô — dòng sau gặp bản đã hiệu lực thì báo lỗi, cả lô không lưu")
    void batchStopsOnLockedVersion() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(false);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(repository.findByAgeGroupIdAndEffectiveFrom(AGE_GROUP, TODAY.minusDays(1)))
                .willReturn(Optional.of(config(10L, TODAY.minusDays(1))));

        assertThatThrownBy(() -> service.saveConfigs(
                        VICE_PRINCIPAL,
                        List.of(request(TODAY.plusDays(5), null, null), request(TODAY.minusDays(1), null, null))))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.MEAL_CONFIG_LOCKED);
    }

    @Test
    void batchUpdatesFutureVersionAndCreatesMissing() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(repository.findByAgeGroupIdAndEffectiveFrom(AGE_GROUP, TODAY.plusDays(3)))
                .willReturn(Optional.of(config(12L, TODAY.plusDays(3))));

        List<AgeGroupMealConfigResponse> saved = service.saveConfigs(
                VICE_PRINCIPAL, List.of(request(TODAY.plusDays(3), new BigDecimal("600"), new BigDecimal("700"))));

        assertThat(saved).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(12L);
            assertThat(item.kcalMin()).isEqualByComparingTo("600");
            assertThat(item.editable()).isTrue();
        });
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("NUT-17: bản cấu hình đã tới ngày hiệu lực chỉ đọc")
    void updateRejectsConfigAlreadyInEffect() {
        given(repository.findById(10L)).willReturn(Optional.of(config(10L, TODAY)));

        assertThatThrownBy(() -> service.updateConfig(VICE_PRINCIPAL, 10L, request(TODAY.plusDays(3), null, null)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.MEAL_CONFIG_LOCKED);
    }

    @Test
    @DisplayName("NUT-17: bản mới của nhóm tuổi đã có cấu hình phải hiệu lực từ ngày mai")
    void newVersionMustStartAfterToday() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(true);

        assertThatThrownBy(() -> service.createConfig(VICE_PRINCIPAL, request(TODAY, null, null)))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.MEAL_CONFIG_DATE_INVALID);
        verify(repository, never()).save(any());
    }

    @Test
    void firstVersionMayStartAnyDay() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(false);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        AgeGroupMealConfigResponse created =
                service.createConfig(VICE_PRINCIPAL, request(TODAY.minusDays(30), null, null));

        assertThat(created.editable()).isFalse();
        assertThat(created.mealPrice()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("NUT-19: phí dịch vụ lưu cùng giá suất; để trống thì bằng 0")
    void storesServiceFeeAndDefaultsToZero() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(false);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        AgeGroupMealConfigResponse withFee = service.createConfig(
                VICE_PRINCIPAL,
                feeRequest(TODAY.plusDays(1), null, null, null, null, null, null, new BigDecimal("5000")));
        AgeGroupMealConfigResponse withoutFee =
                service.createConfig(VICE_PRINCIPAL, request(TODAY.plusDays(2), null, null));

        assertThat(withFee.serviceFee()).isEqualByComparingTo("5000");
        assertThat(withoutFee.serviceFee()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("NUT-19: phí dịch vụ lớn hơn giá suất thì từ chối")
    void rejectsServiceFeeAboveMealPrice() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));

        assertThatThrownBy(() -> service.createConfig(
                        VICE_PRINCIPAL,
                        feeRequest(TODAY.plusDays(1), null, null, null, null, null, null, new BigDecimal("30001"))))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.MEAL_SERVICE_FEE_INVALID);
    }

    @Test
    @DisplayName("NUT-18: chuẩn có min lớn hơn max thì từ chối")
    void rejectsInvertedNormRange() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));

        assertThatThrownBy(() -> service.createConfig(
                        VICE_PRINCIPAL, request(TODAY.plusDays(1), new BigDecimal("700"), new BigDecimal("600"))))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.NUTRITION_NORM_RANGE_INVALID);
    }

    @Test
    @DisplayName("NUT-26: vi chất có min lớn hơn max thì từ chối, báo tên vi chất")
    void rejectsInvertedMicronutrientRange() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));

        assertThatThrownBy(() -> service.createConfig(
                        VICE_PRINCIPAL, ironRequest(TODAY.plusDays(1), new BigDecimal("10"), new BigDecimal("6"))))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> {
                    assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.NUTRITION_NORM_RANGE_INVALID);
                    assertThat(ex.getMessage()).contains("sắt");
                });
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("NUT-26: lưu cả lô có vi chất min lớn hơn max thì từ chối, không lưu dòng nào")
    void batchRejectsInvertedMicronutrientRange() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(false);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.saveConfigs(
                        VICE_PRINCIPAL,
                        List.of(
                                ironRequest(TODAY.plusDays(5), new BigDecimal("6"), new BigDecimal("10")),
                                ironRequest(TODAY.plusDays(6), new BigDecimal("10"), new BigDecimal("6")))))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.NUTRITION_NORM_RANGE_INVALID);
    }

    @Test
    @DisplayName("NUT-26: vi chất chỉ có min (max trống) là hợp lệ và được lưu")
    void micronutrientMaxMayBeEmpty() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(false);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        AgeGroupMealConfigResponse created =
                service.createConfig(VICE_PRINCIPAL, ironRequest(TODAY.plusDays(1), new BigDecimal("6"), null));

        assertThat(created.ironMin()).isEqualByComparingTo("6");
        assertThat(created.ironMax()).isNull();
        assertThat(created.calciumMin()).isNull();
    }

    @Test
    @DisplayName("NUT-25: tổng tỷ lệ năng lượng tối thiểu của các bữa vượt 100% thì từ chối")
    void rejectsSessionSharesOver100Percent() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));

        assertThatThrownBy(() -> service.createConfig(
                        VICE_PRINCIPAL,
                        request(TODAY.plusDays(1), null, null, new BigDecimal("70"), new BigDecimal("35"))))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.MEAL_SESSION_KCAL_PCT_INVALID);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("NUT-19: bản áp dụng tại một ngày là bản có ngày hiệu lực lớn nhất không sau ngày đó")
    void effectiveConfigIsLatestNotAfterDate() {
        given(repository.findByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(TODAY))
                .willReturn(List.of(config(11L, TODAY.minusDays(1)), config(10L, TODAY.minusDays(60))));

        assertThat(service.getEffectiveConfigs(TODAY))
                .extracting(AgeGroupMealConfigResponse::id)
                .containsExactly(11L);
    }

    private static AgeGroupMealConfigRequest request(LocalDate from, BigDecimal kcalMin, BigDecimal kcalMax) {
        return request(from, kcalMin, kcalMax, null, null);
    }

    private static AgeGroupMealConfigRequest request(
            LocalDate from,
            BigDecimal kcalMin,
            BigDecimal kcalMax,
            BigDecimal lunchPctMin,
            BigDecimal afternoonPctMin) {
        return request(from, kcalMin, kcalMax, lunchPctMin, afternoonPctMin, null, null);
    }

    private static AgeGroupMealConfigRequest ironRequest(LocalDate from, BigDecimal ironMin, BigDecimal ironMax) {
        return request(from, null, null, null, null, ironMin, ironMax);
    }

    private static AgeGroupMealConfigRequest request(
            LocalDate from,
            BigDecimal kcalMin,
            BigDecimal kcalMax,
            BigDecimal lunchPctMin,
            BigDecimal afternoonPctMin,
            BigDecimal ironMin,
            BigDecimal ironMax) {
        return feeRequest(from, kcalMin, kcalMax, lunchPctMin, afternoonPctMin, ironMin, ironMax, null);
    }

    @Test
    @DisplayName("NUT-24: định mức đạm gam tối thiểu vượt mức cho phép theo năng lượng × tỷ lệ max (số liệu VPS) ⇒ 6110")
    void rejectsGramsExceedingEnergyBudget() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));

        // VPS: năng lượng 615–726, đạm 13–20%, đạm động vật 300–400 g ⇒ 300 g × 4 = 1.200 kcal > 726 × 20%
        assertThatThrownBy(() -> service.createConfig(VICE_PRINCIPAL,
                        gramsRequest(new BigDecimal("726"), new BigDecimal("20"), new BigDecimal("300"))))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> {
                    assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.MEAL_CONFIG_GRAMS_EXCEED_ENERGY);
                    assertThat(ex.getMessage()).contains("đạm", "300", "37");
                });
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("NUT-24: định mức gam hợp lý với năng lượng và cơ cấu (đạm ĐV 10 + TV 8, 726 kcal, đạm 20%) thì lưu được")
    void storesGramsWithinEnergyBudget() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(false);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        AgeGroupMealConfigResponse created = service.createConfig(VICE_PRINCIPAL,
                gramsRequest(new BigDecimal("726"), new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("8")));

        assertThat(created.animalProteinGMin()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("NUT-24: chưa nhập năng lượng hoặc tỷ lệ thì không kiểm định mức gam")
    void skipsGramsCheckWithoutKcalOrPercent() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(false);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.createConfig(VICE_PRINCIPAL, gramsRequest(null, null, new BigDecimal("5000")));
    }

    private static AgeGroupMealConfigRequest gramsRequest(
            BigDecimal kcalMax, BigDecimal proteinPctMax, BigDecimal animalProteinGMin) {
        return gramsRequest(kcalMax, proteinPctMax, animalProteinGMin, null);
    }

    private static AgeGroupMealConfigRequest gramsRequest(
            BigDecimal kcalMax,
            BigDecimal proteinPctMax,
            BigDecimal animalProteinGMin,
            BigDecimal plantProteinGMin) {
        return new AgeGroupMealConfigRequest(
                AGE_GROUP,                                   // ageGroupId
                TODAY.plusDays(1),                            // effectiveFrom
                new BigDecimal("30000"),                       // mealPrice
                null,                                          // serviceFee
                null,                                          // kcalMin
                kcalMax,
                null,                                          // proteinPctMin
                proteinPctMax,
                null, null,                                   // lipidPct
                null, null,                                   // glucidPct
                animalProteinGMin, null,                       // đạm động vật (min, max)
                plantProteinGMin, null,                        // đạm thực vật
                null, null,                                    // béo động vật
                null, null,                                    // béo thực vật
                null, null,                                    // bột đường (g)
                null, null, null, null,                        // kcal% bữa sáng / trưa
                null, null,                                    // kcal% bữa chiều
                null,                                          // dailyKcalNeed
                // vi chất (canxi, photpho, sắt, A, B1, B2, PP, C): 8 cặp (min, max) để trống
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null);
    }

    private static AgeGroupMealConfigRequest feeRequest(
            LocalDate from,
            BigDecimal kcalMin,
            BigDecimal kcalMax,
            BigDecimal lunchPctMin,
            BigDecimal afternoonPctMin,
            BigDecimal ironMin,
            BigDecimal ironMax,
            BigDecimal serviceFee) {
        return new AgeGroupMealConfigRequest(
                AGE_GROUP,
                from,
                new BigDecimal("30000"),
                serviceFee,
                kcalMin,
                kcalMax,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                lunchPctMin,
                null,
                afternoonPctMin,
                null,
                null,
                // vi chất theo thứ tự canxi, photpho, sắt, A, B1, B2, PP, C (mỗi cặp min, max)
                null,
                null,
                null,
                null,
                ironMin,
                ironMax,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static AgeGroupMealConfig config(Long id, LocalDate from) {
        AgeGroupMealConfig config = AgeGroupMealConfig.create(AGE_GROUP, 3L);
        config.changeInfo(
                from,
                new BigDecimal("30000"),
                BigDecimal.ZERO,
                new AgeGroupMealConfig.Norms(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null),
                "",
                3L);
        ReflectionTestUtils.setField(config, "id", id);
        return config;
    }

    private static AgeGroupDetailResponse ageGroup() {
        return new AgeGroupDetailResponse(
                AGE_GROUP,
                "ag-5",
                "Mẫu giáo lớn",
                "MG lớn",
                (short) 4,
                60,
                72,
                List.of(MealSession.LUNCH, MealSession.AFTERNOON),
                2,
                35,
                "");
    }
}
