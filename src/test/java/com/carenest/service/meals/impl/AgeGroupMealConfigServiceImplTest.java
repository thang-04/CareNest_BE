package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carenest.config.NutritionConversionProperties;
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
    private final AgeGroupMealConfigServiceImpl service = new AgeGroupMealConfigServiceImpl(
            repository,
            ageGroupService,
            new NutritionCatalogMapper(),
            FIXED_CLOCK,
            new NutritionConversionProperties(
                    new BigDecimal("4.1"),
                    new BigDecimal("9.3"),
                    new BigDecimal("4.1"),
                    new BigDecimal("0.60"),
                    new BigDecimal("0.60")));

    @Test
    void nutritionStatusDefaultsOnAndCanBeDisabledWithoutNewVersion() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.findDisabledNutritionGroupIds()).willReturn(Set.of());
        assertThat(service.getGroupStatuses().getFirst().enabled()).isTrue();
        var principal = new CurrentUser(
                7L,
                "kc-7",
                "demo",
                "Demo",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                com.carenest.utils.enums.Permission.fixedPermissionsOf(CareNestRole.PRINCIPAL));
        service.saveConfigs(
                principal, List.of(), List.of(new com.carenest.dto.meals.NutritionGroupStatus(AGE_GROUP, false)));
        verify(repository).setNutritionGroupEnabled(AGE_GROUP, false, 7L);
        verify(repository, never()).save(any());
        given(repository.findDisabledNutritionGroupIds()).willReturn(Set.of(AGE_GROUP));
        assertThat(service.getGroupStatuses().getFirst().enabled()).isFalse();
        assertThatThrownBy(() -> service.saveConfigs(
                        VICE_PRINCIPAL,
                        List.of(),
                        List.of(new com.carenest.dto.meals.NutritionGroupStatus(AGE_GROUP, false))))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FORBIDDEN);
    }

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
    @DisplayName("Nhu cầu năng lượng cả ngày không lớn hơn kcal tối đa thì từ chối, báo tại ô dailyKcalNeed")
    void rejectsDailyKcalNotAboveMax() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));

        for (String need : List.of("698", "750")) {
            assertThatThrownBy(() -> service.createConfig(
                            VICE_PRINCIPAL,
                            dailyRequest(
                                    TODAY.plusDays(1),
                                    new BigDecimal("700"),
                                    new BigDecimal("750"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    new BigDecimal(need))))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        GlobalException ge = (GlobalException) ex;
                        assertThat(ge.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                        assertThat(ge.getFieldErrors()).singleElement().satisfies(fe -> {
                            assertThat(fe.field()).isEqualTo("dailyKcalNeed");
                            assertThat(fe.message())
                                    .isEqualTo("Nhu cầu năng lượng cả ngày phải lớn hơn năng lượng tối đa (750 kcal).");
                        });
                    });
        }
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Gam tối đa của đạm, béo hoặc bột đường quy ra kcal vượt năng lượng tối đa thì từ chối, báo tại ô gam")
    void rejectsGramsAboveEnergy() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        BigDecimal kcalMax = new BigDecimal("726");
        List<AgeGroupMealConfigRequest> requests = List.of(
                gramsRequest(kcalMax, new BigDecimal("200"), new BigDecimal("100"), null, null, null),
                gramsRequest(kcalMax, null, null, new BigDecimal("50"), new BigDecimal("40"), null),
                gramsRequest(kcalMax, null, null, null, null, new BigDecimal("200")));
        List<String> fields = List.of("animalProteinGMax", "animalLipidGMax", "glucidGMax");

        for (int i = 0; i < requests.size(); i++) {
            AgeGroupMealConfigRequest request = requests.get(i);
            String field = fields.get(i);
            assertThatThrownBy(() -> service.createConfig(VICE_PRINCIPAL, request))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        GlobalException ge = (GlobalException) ex;
                        assertThat(ge.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                        assertThat(ge.getFieldErrors()).singleElement().satisfies(fe -> {
                            assertThat(fe.field()).isEqualTo(field);
                            assertThat(fe.message()).contains("vượt năng lượng tối đa (726 kcal)");
                        });
                    });
        }
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Gam hợp lý so với năng lượng tối đa, hoặc chưa nhập năng lượng, thì lưu được")
    void acceptsGramsWithinEnergyOrWithoutKcal() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.createConfig(
                VICE_PRINCIPAL,
                gramsRequest(
                        new BigDecimal("726"),
                        new BigDecimal("15"),
                        new BigDecimal("10"),
                        new BigDecimal("12"),
                        new BigDecimal("8"),
                        new BigDecimal("90")));
        service.createConfig(
                VICE_PRINCIPAL, gramsRequest(null, new BigDecimal("300"), null, null, null, new BigDecimal("500")));
    }

    @Test
    @DisplayName("Nhu cầu năng lượng cả ngày lớn hơn kcal tối đa, hoặc thiếu một trong hai, thì lưu được")
    void acceptsDailyKcalAboveMaxOrMissing() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.createConfig(
                VICE_PRINCIPAL,
                dailyRequest(
                        TODAY.plusDays(1),
                        new BigDecimal("700"),
                        new BigDecimal("750"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("751")));
        service.createConfig(
                VICE_PRINCIPAL,
                dailyRequest(TODAY.plusDays(2), null, null, null, null, null, null, null, new BigDecimal("698")));

        verify(repository, times(2)).save(any());
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

    private static AgeGroupMealConfigRequest feeRequest(
            LocalDate from,
            BigDecimal kcalMin,
            BigDecimal kcalMax,
            BigDecimal lunchPctMin,
            BigDecimal afternoonPctMin,
            BigDecimal ironMin,
            BigDecimal ironMax,
            BigDecimal serviceFee) {
        return dailyRequest(from, kcalMin, kcalMax, lunchPctMin, afternoonPctMin, ironMin, ironMax, serviceFee, null);
    }

    private static AgeGroupMealConfigRequest dailyRequest(
            LocalDate from,
            BigDecimal kcalMin,
            BigDecimal kcalMax,
            BigDecimal lunchPctMin,
            BigDecimal afternoonPctMin,
            BigDecimal ironMin,
            BigDecimal ironMax,
            BigDecimal serviceFee,
            BigDecimal dailyKcalNeed) {
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
                dailyKcalNeed,
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
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static AgeGroupMealConfigRequest gramsRequest(
            BigDecimal kcalMax,
            BigDecimal animalProteinGMax,
            BigDecimal plantProteinGMax,
            BigDecimal animalLipidGMax,
            BigDecimal plantLipidGMax,
            BigDecimal glucidGMax) {
        return new AgeGroupMealConfigRequest(
                AGE_GROUP,
                TODAY.plusDays(1),
                new BigDecimal("30000"),
                null,
                null,
                kcalMax,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                animalProteinGMax,
                null,
                plantProteinGMax,
                null,
                animalLipidGMax,
                null,
                plantLipidGMax,
                null,
                glucidGMax,
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

    // Khoảng đạm 13–20, béo 25–35, bột đường 55–65 (chứa mẫu nghiệm thu 14–26–60 và 15–25–60)
    private static AgeGroupMealConfigRequest structureRequest(BigDecimal p, BigDecimal l, BigDecimal g) {
        AgeGroupMealConfigRequest base = request(TODAY.plusDays(1), new BigDecimal("615"), new BigDecimal("726"));
        // 33 trường ở giữa (phạm vi gam ĐV/TV, % bữa, kcal/ngày, vi chất) để trống
        return new AgeGroupMealConfigRequest(
                base.ageGroupId(),
                base.effectiveFrom(),
                base.mealPrice(),
                base.serviceFee(),
                base.kcalMin(),
                base.kcalMax(),
                new BigDecimal("13"),
                new BigDecimal("20"),
                new BigDecimal("25"),
                new BigDecimal("35"),
                new BigDecimal("55"),
                new BigDecimal("65"),
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
                p,
                l,
                g,
                null);
    }

    @Test
    @DisplayName("NUT-27: cơ cấu 14–26–60 hợp lệ, trả gam quy đổi khớp bộ nghiệm thu tại 615 và 726 kcal")
    void convertsAppliedStructureToGrams() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(false);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        AgeGroupMealConfigResponse created = service.createConfig(
                VICE_PRINCIPAL, structureRequest(new BigDecimal("14"), new BigDecimal("26"), new BigDecimal("60")));

        var at615 = created.conversion().atKcalMin();
        assertThat(at615.animalProteinG()).isCloseTo(new BigDecimal("12.60"), within(new BigDecimal("0.005")));
        assertThat(at615.plantProteinG()).isCloseTo(new BigDecimal("8.40"), within(new BigDecimal("0.005")));
        assertThat(at615.animalLipidG()).isCloseTo(new BigDecimal("10.32"), within(new BigDecimal("0.005")));
        assertThat(at615.plantLipidG()).isCloseTo(new BigDecimal("6.88"), within(new BigDecimal("0.005")));
        assertThat(at615.glucidG()).isCloseTo(new BigDecimal("90.00"), within(new BigDecimal("0.005")));
        var at726 = created.conversion().atKcalMax();
        assertThat(at726.animalProteinG()).isCloseTo(new BigDecimal("14.87"), within(new BigDecimal("0.005")));
        assertThat(at726.glucidG()).isCloseTo(new BigDecimal("106.24"), within(new BigDecimal("0.005")));
    }

    @Test
    @DisplayName("NUT-27: đổi cơ cấu 14–26–60 sang 15–25–60 thì đạm ĐV 13,50, béo ĐV 9,92, bột đường giữ 90,00")
    void changingStructureChangesGrams() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));
        given(repository.existsByAgeGroupId(AGE_GROUP)).willReturn(false);
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        var at615 = service.createConfig(
                        VICE_PRINCIPAL,
                        structureRequest(new BigDecimal("15"), new BigDecimal("25"), new BigDecimal("60")))
                .conversion()
                .atKcalMin();

        assertThat(at615.animalProteinG()).isCloseTo(new BigDecimal("13.50"), within(new BigDecimal("0.005")));
        assertThat(at615.animalLipidG()).isCloseTo(new BigDecimal("9.92"), within(new BigDecimal("0.005")));
        assertThat(at615.glucidG()).isCloseTo(new BigDecimal("90.00"), within(new BigDecimal("0.005")));
    }

    @Test
    @DisplayName("NUT-27: cơ cấu có tổng khác 100% hoặc nằm ngoài khoảng thì từ chối, không lưu")
    void rejectsInvalidAppliedStructure() {
        given(ageGroupService.getAgeGroups()).willReturn(List.of(ageGroup()));

        assertThatThrownBy(() -> service.createConfig(
                        VICE_PRINCIPAL,
                        structureRequest(new BigDecimal("14"), new BigDecimal("26"), new BigDecimal("61"))))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.APPLIED_STRUCTURE_INVALID);
        assertThatThrownBy(() -> service.createConfig(
                        VICE_PRINCIPAL,
                        structureRequest(new BigDecimal("25"), new BigDecimal("30"), new BigDecimal("45"))))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.APPLIED_STRUCTURE_INVALID);
        verify(repository, never()).save(any());
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
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null),
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
