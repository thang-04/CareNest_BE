package com.carenest.service.meals.impl;

import com.carenest.config.NutritionConversionProperties;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.meals.AgeGroupMealConfigRequest;
import com.carenest.dto.meals.AgeGroupMealConfigResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.AgeGroupMealConfigRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.AgeGroupMealConfigService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.NutritionConversion;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AgeGroupMealConfigServiceImpl implements AgeGroupMealConfigService {
    @Override
    @Transactional(readOnly = true)
    public List<com.carenest.dto.meals.NutritionGroupStatus> getGroupStatuses() {
        Set<Long> disabled = configRepository.findDisabledNutritionGroupIds();
        return ageGroupService.getAgeGroups().stream()
                .map(g -> new com.carenest.dto.meals.NutritionGroupStatus(g.id(), !disabled.contains(g.id())))
                .toList();
    }

    @Override
    public List<AgeGroupMealConfigResponse> saveConfigs(
            CurrentUser user,
            List<AgeGroupMealConfigRequest> requests,
            List<com.carenest.dto.meals.NutritionGroupStatus> statuses) {
        if (!user.hasPermission(com.carenest.utils.enums.Permission.MEAL_CONFIG_MANAGE)) {
            throw GlobalException.of(ApiCode.FORBIDDEN);
        }
        Set<Long> known = ageGroupIds();
        Set<Long> seen = new HashSet<>();
        for (var status : statuses) {
            if (!known.contains(status.ageGroupId()) || !seen.add(status.ageGroupId())) {
                throw GlobalException.of(ApiCode.AGE_GROUP_INVALID);
            }
        }
        var result = saveConfigs(user, requests);
        for (var status : statuses) {
            configRepository.setNutritionGroupEnabled(status.ageGroupId(), status.enabled(), user.userId());
        }
        return result;
    }

    private static final String LOG_PREFIX = "[AgeGroupMealConfigServiceImpl]";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    // kcal/g của đạm, bột đường và béo (NUT-24): kiểm định mức gam có hợp với năng lượng theo cơ cấu P:L:G
    private static final BigDecimal PROTEIN_KCAL_PER_G = BigDecimal.valueOf(4);
    private static final BigDecimal LIPID_KCAL_PER_G = BigDecimal.valueOf(9);
    private static final BigDecimal GLUCID_KCAL_PER_G = BigDecimal.valueOf(4);

    private final AgeGroupMealConfigRepository configRepository;
    private final AgeGroupService ageGroupService;
    private final NutritionCatalogMapper mapper;
    private final Clock clock;
    private final NutritionConversionProperties conversionProperties;

    private NutritionConversion conversion() {
        return new NutritionConversion(conversionProperties);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgeGroupMealConfigResponse> getEffectiveConfigs(LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getEffectiveConfigs|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            LocalDate at = date == null ? today : date;
            // Danh sách sắp ngày hiệu lực giảm dần ⇒ bản đầu tiên của mỗi nhóm tuổi là bản đang áp dụng
            Map<Long, AgeGroupMealConfig> effective = new LinkedHashMap<>();
            configRepository
                    .findByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(at)
                    .forEach(config -> effective.putIfAbsent(config.getAgeGroupId(), config));
            List<AgeGroupMealConfigResponse> response = effective.values().stream()
                    .map(config -> mapper.toResponse(config, today, conversion()))
                    .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgeGroupMealConfigResponse> getConfigVersions(Long ageGroupId) {
        String logPrefix = LOG_PREFIX + "|getConfigVersions|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            List<AgeGroupMealConfigResponse> response =
                    configRepository.findByAgeGroupIdOrderByEffectiveFromDesc(ageGroupId).stream()
                            .map(config -> mapper.toResponse(config, today, conversion()))
                            .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AgeGroupMealConfigResponse createConfig(CurrentUser currentUser, AgeGroupMealConfigRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createConfig|userId=" + currentUser.userId() + "|ageGroupId=" + request.ageGroupId();
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            requireAgeGroup(request.ageGroupId());
            if (configRepository.existsByAgeGroupIdAndEffectiveFrom(request.ageGroupId(), request.effectiveFrom())) {
                throw GlobalException.of(ApiCode.MEAL_CONFIG_DUPLICATED);
            }
            AgeGroupMealConfig config = createNew(currentUser, request, today);
            log.info("{}|configId={}|END", logPrefix, config.getId());
            return mapper.toResponse(config, today, conversion());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AgeGroupMealConfigResponse updateConfig(
            CurrentUser currentUser, Long configId, AgeGroupMealConfigRequest request) {
        String logPrefix = LOG_PREFIX + "|updateConfig|userId=" + currentUser.userId() + "|configId=" + configId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            AgeGroupMealConfig config = configRepository
                    .findById(configId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.MEAL_CONFIG_NOT_FOUND));
            requireNotInEffect(config, today);
            if (!config.getAgeGroupId().equals(request.ageGroupId())) {
                throw GlobalException.of(ApiCode.AGE_GROUP_INVALID);
            }
            requireFutureDate(request.effectiveFrom(), today);
            if (configRepository.existsByAgeGroupIdAndEffectiveFromAndIdNot(
                    request.ageGroupId(), request.effectiveFrom(), configId)) {
                throw GlobalException.of(ApiCode.MEAL_CONFIG_DUPLICATED);
            }
            apply(config, request, currentUser.userId());
            configRepository.flush();
            log.info("{}|END", logPrefix);
            return mapper.toResponse(config, today, conversion());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<AgeGroupMealConfigResponse> saveConfigs(
            CurrentUser currentUser, List<AgeGroupMealConfigRequest> requests) {
        String logPrefix = LOG_PREFIX + "|saveConfigs|userId=" + currentUser.userId() + "|items=" + requests.size();
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Set<Long> ageGroupIds = ageGroupIds();
            Set<String> seen = new HashSet<>();
            List<AgeGroupMealConfigResponse> response = new ArrayList<>();
            // Cùng một transaction: lỗi ở dòng nào thì toàn bộ lô rollback
            for (AgeGroupMealConfigRequest request : requests) {
                if (!ageGroupIds.contains(request.ageGroupId())) {
                    throw GlobalException.of(ApiCode.AGE_GROUP_INVALID);
                }
                if (!seen.add(request.ageGroupId() + ":" + request.effectiveFrom())) {
                    throw GlobalException.of(ApiCode.MEAL_CONFIG_DUPLICATED);
                }
                AgeGroupMealConfig existing = configRepository
                        .findByAgeGroupIdAndEffectiveFrom(request.ageGroupId(), request.effectiveFrom())
                        .orElse(null);
                AgeGroupMealConfig config;
                if (existing == null) {
                    config = createNew(currentUser, request, today);
                } else {
                    requireNotInEffect(existing, today);
                    apply(existing, request, currentUser.userId());
                    config = existing;
                }
                response.add(mapper.toResponse(config, today, conversion()));
            }
            configRepository.flush();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Bản đầu tiên của nhóm tuổi lấy ngày nào cũng được để trường bắt đầu dùng; các bản sau phải từ ngày mai
    private AgeGroupMealConfig createNew(CurrentUser currentUser, AgeGroupMealConfigRequest request, LocalDate today) {
        if (configRepository.existsByAgeGroupId(request.ageGroupId())) {
            requireFutureDate(request.effectiveFrom(), today);
        }
        AgeGroupMealConfig config = AgeGroupMealConfig.create(request.ageGroupId(), currentUser.userId());
        apply(config, request, currentUser.userId());
        return configRepository.save(config);
    }

    // NUT-17: bản đã tới ngày hiệu lực đang được dùng để kiểm thực đơn, chỉ đọc
    private static void requireNotInEffect(AgeGroupMealConfig config, LocalDate today) {
        if (!config.getEffectiveFrom().isAfter(today)) {
            throw GlobalException.of(ApiCode.MEAL_CONFIG_LOCKED);
        }
    }

    private Set<Long> ageGroupIds() {
        return ageGroupService.getAgeGroups().stream()
                .map(AgeGroupDetailResponse::id)
                .collect(Collectors.toSet());
    }

    private void requireAgeGroup(Long ageGroupId) {
        boolean exists = ageGroupService.getAgeGroups().stream()
                .map(AgeGroupDetailResponse::id)
                .anyMatch(ageGroupId::equals);
        if (!exists) {
            throw GlobalException.of(ApiCode.AGE_GROUP_INVALID);
        }
    }

    private static void requireFutureDate(LocalDate effectiveFrom, LocalDate today) {
        if (!effectiveFrom.isAfter(today)) {
            throw GlobalException.of(ApiCode.MEAL_CONFIG_DATE_INVALID);
        }
    }

    private static void apply(AgeGroupMealConfig config, AgeGroupMealConfigRequest request, Long userId) {
        checkRange("năng lượng", request.kcalMin(), request.kcalMax());
        checkRange("đạm", request.proteinPctMin(), request.proteinPctMax());
        checkRange("béo", request.lipidPctMin(), request.lipidPctMax());
        checkRange("bột đường", request.glucidPctMin(), request.glucidPctMax());
        checkRange("đạm động vật", request.animalProteinGMin(), request.animalProteinGMax());
        checkRange("đạm thực vật", request.plantProteinGMin(), request.plantProteinGMax());
        checkRange("béo động vật", request.animalLipidGMin(), request.animalLipidGMax());
        checkRange("béo thực vật", request.plantLipidGMin(), request.plantLipidGMax());
        checkRange("bột đường (g)", request.glucidGMin(), request.glucidGMax());
        checkRange("năng lượng bữa sáng", request.morningKcalPctMin(), request.morningKcalPctMax());
        checkRange("năng lượng bữa trưa", request.lunchKcalPctMin(), request.lunchKcalPctMax());
        checkRange("năng lượng bữa chiều", request.afternoonKcalPctMin(), request.afternoonKcalPctMax());
        checkRange("canxi", request.calciumMin(), request.calciumMax());
        checkRange("photpho", request.phosphorusMin(), request.phosphorusMax());
        checkRange("sắt", request.ironMin(), request.ironMax());
        checkRange("vitamin A", request.vitaminAMin(), request.vitaminAMax());
        checkRange("vitamin B1", request.vitaminB1Min(), request.vitaminB1Max());
        checkRange("vitamin B2", request.vitaminB2Min(), request.vitaminB2Max());
        checkRange("vitamin PP", request.vitaminPpMin(), request.vitaminPpMax());
        checkRange("vitamin C", request.vitaminCMin(), request.vitaminCMax());
        checkGramsWithinEnergy(request);
        checkRange("kẽm", request.zincMin(), request.zincMax());
        checkSessionShares(request);
        BigDecimal serviceFee = request.serviceFee() == null ? BigDecimal.ZERO : request.serviceFee();
        // NUT-19: phí dịch vụ là một phần giá suất ⇒ ngân sách thực phẩm không âm
        if (serviceFee.compareTo(request.mealPrice()) > 0) {
            throw GlobalException.of(ApiCode.MEAL_SERVICE_FEE_INVALID);
        }
        checkDailyKcalAboveMax(request);
        checkMaxGramsWithinEnergy(request);
        checkAppliedStructure(request);
        // NUT-19: để trống biên tiền thì dùng 5%
        config.changeBudgetTolerance(
                request.budgetTolerancePct() == null ? BigDecimal.valueOf(5) : request.budgetTolerancePct());
        config.changeInfo(
                request.effectiveFrom(),
                request.mealPrice(),
                serviceFee,
                new AgeGroupMealConfig.Norms(
                        request.kcalMin(),
                        request.kcalMax(),
                        request.proteinPctMin(),
                        request.proteinPctMax(),
                        request.lipidPctMin(),
                        request.lipidPctMax(),
                        request.glucidPctMin(),
                        request.glucidPctMax(),
                        request.animalProteinGMin(),
                        request.animalProteinGMax(),
                        request.plantProteinGMin(),
                        request.plantProteinGMax(),
                        request.animalLipidGMin(),
                        request.animalLipidGMax(),
                        request.plantLipidGMin(),
                        request.plantLipidGMax(),
                        request.glucidGMin(),
                        request.glucidGMax(),
                        request.morningKcalPctMin(),
                        request.morningKcalPctMax(),
                        request.lunchKcalPctMin(),
                        request.lunchKcalPctMax(),
                        request.afternoonKcalPctMin(),
                        request.afternoonKcalPctMax(),
                        request.dailyKcalNeed(),
                        request.calciumMin(),
                        request.calciumMax(),
                        request.phosphorusMin(),
                        request.phosphorusMax(),
                        request.ironMin(),
                        request.ironMax(),
                        request.vitaminAMin(),
                        request.vitaminAMax(),
                        request.vitaminB1Min(),
                        request.vitaminB1Max(),
                        request.vitaminB2Min(),
                        request.vitaminB2Max(),
                        request.vitaminPpMin(),
                        request.vitaminPpMax(),
                        request.vitaminCMin(),
                        request.vitaminCMax(),
                        request.zincMin(),
                        request.zincMax(),
                        request.appliedProteinPct(),
                        request.appliedLipidPct(),
                        request.appliedGlucidPct()),
                request.normBasis() == null ? "" : request.normBasis().trim(),
                userId);
    }

    // NUT-25: các bữa chia nhau nhu cầu năng lượng cả ngày ⇒ tổng mức tối thiểu không vượt 100%
    private static void checkSessionShares(AgeGroupMealConfigRequest request) {
        BigDecimal minTotal = Stream.of(
                        request.morningKcalPctMin(), request.lunchKcalPctMin(), request.afternoonKcalPctMin())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (minTotal.compareTo(ONE_HUNDRED) > 0) {
            throw GlobalException.of(
                    ApiCode.MEAL_SESSION_KCAL_PCT_INVALID,
                    minTotal.stripTrailingZeros().toPlainString());
        }
    }

    // Định mức gam tối thiểu không được vượt mức cho phép theo năng lượng và cơ cấu P:L:G
    // đã nhập cùng bản (NUT-24): (đạm ĐV min + đạm TV min) × 4 kcal ≤ năng lượng max × tỷ lệ đạm max,
    // (béo ĐV min + béo TV min) × 9 kcal ≤ năng lượng max × tỷ lệ béo max,
    // bột đường (g) min × 4 kcal ≤ năng lượng max × tỷ lệ bột đường max. Thiếu số nào thì bỏ qua hạng đó.
    private static void checkGramsWithinEnergy(AgeGroupMealConfigRequest request) {
        checkGramsPair(
                "đạm",
                add(request.animalProteinGMin(), request.plantProteinGMin()),
                PROTEIN_KCAL_PER_G,
                request.kcalMax(),
                request.proteinPctMax());
        checkGramsPair(
                "béo",
                add(request.animalLipidGMin(), request.plantLipidGMin()),
                LIPID_KCAL_PER_G,
                request.kcalMax(),
                request.lipidPctMax());
        checkGramsPair("bột đường", request.glucidGMin(), GLUCID_KCAL_PER_G, request.kcalMax(), request.glucidPctMax());
    }

    private static void checkGramsPair(
            String label, BigDecimal gramsMin, BigDecimal kcalPerGram, BigDecimal kcalMax, BigDecimal pctMax) {
        if (gramsMin == null || kcalMax == null || pctMax == null) {
            return;
        }
        // Mức năng lượng cho phép của chất đó = năng lượng tối đa × tỷ lệ tối đa, đổi ra gram
        BigDecimal allowedGrams = kcalMax.multiply(pctMax, new MathContext(6))
                .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP)
                .divide(kcalPerGram, 6, RoundingMode.HALF_UP);
        BigDecimal gramsAllowedCap = allowedGrams.setScale(0, RoundingMode.CEILING);
        if (gramsMin.compareTo(gramsAllowedCap) > 0) {
            throw GlobalException.of(
                    ApiCode.MEAL_CONFIG_GRAMS_EXCEED_ENERGY,
                    label,
                    gramsMin.stripTrailingZeros().toPlainString(),
                    gramsAllowedCap.toPlainString());
        }
    }

    private static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return null;
        }
        return (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b);
    }

    // NUT-28: cơ cấu áp dụng nhập đủ ba chất, tổng 100%, mỗi chất nằm trong khoảng min–max của chính chất đó (nếu đã
    // đặt)
    private static void checkAppliedStructure(AgeGroupMealConfigRequest request) {
        BigDecimal protein = request.appliedProteinPct();
        BigDecimal lipid = request.appliedLipidPct();
        BigDecimal glucid = request.appliedGlucidPct();
        if (protein == null && lipid == null && glucid == null) {
            return;
        }
        if (protein == null || lipid == null || glucid == null) {
            throw GlobalException.of(ApiCode.APPLIED_STRUCTURE_INVALID, "nhập đủ đạm, béo và bột đường");
        }
        BigDecimal total = protein.add(lipid).add(glucid);
        if (total.compareTo(ONE_HUNDRED) != 0) {
            throw GlobalException.of(
                    ApiCode.APPLIED_STRUCTURE_INVALID,
                    "tổng là " + total.stripTrailingZeros().toPlainString() + "%, cần đúng 100%");
        }
        checkWithinRange("đạm", protein, request.proteinPctMin(), request.proteinPctMax());
        checkWithinRange("béo", lipid, request.lipidPctMin(), request.lipidPctMax());
        checkWithinRange("bột đường", glucid, request.glucidPctMin(), request.glucidPctMax());
    }

    private static void checkWithinRange(String label, BigDecimal value, BigDecimal min, BigDecimal max) {
        if ((min != null && value.compareTo(min) < 0) || (max != null && value.compareTo(max) > 0)) {
            throw GlobalException.of(
                    ApiCode.APPLIED_STRUCTURE_INVALID,
                    label + " " + value.stripTrailingZeros().toPlainString() + "% nằm ngoài khoảng cho phép");
        }
    }

    /** Nhu cầu năng lượng cả ngày phải lớn hơn năng lượng tối đa (chỉ so kcal, không so các tỷ lệ %). */
    private static void checkDailyKcalAboveMax(AgeGroupMealConfigRequest request) {
        BigDecimal need = request.dailyKcalNeed();
        BigDecimal kcalMax = request.kcalMax();
        if (need != null && kcalMax != null && need.compareTo(kcalMax) <= 0) {
            throw GlobalException.withFieldErrors(
                    ApiCode.INVALID_REQUEST_DATA,
                    List.of(new FieldErrorResponse(
                            "dailyKcalNeed",
                            "Nhu cầu năng lượng cả ngày phải lớn hơn năng lượng tối đa ("
                                    + kcalMax.stripTrailingZeros().toPlainString() + " kcal).")));
        }
    }

    private static final BigDecimal KCAL_PER_G_PROTEIN = BigDecimal.valueOf(4);
    private static final BigDecimal KCAL_PER_G_LIPID = BigDecimal.valueOf(9);
    private static final BigDecimal KCAL_PER_G_GLUCID = BigDecimal.valueOf(4);

    /**
     * NUT-24: gam tối đa của một chất (đạm, béo, bột đường) quy ra kcal không được vượt năng lượng tối đa của nhóm tuổi,
     * để không nhập được số vô lý (vd. 300 g đạm) làm mọi ngày bị chấm thiếu và chặn công bố.
     */
    private static void checkMaxGramsWithinEnergy(AgeGroupMealConfigRequest request) {
        BigDecimal kcalMax = request.kcalMax();
        if (kcalMax == null) {
            return;
        }
        checkMacroEnergy(
                "Đạm",
                "animalProteinGMax",
                KCAL_PER_G_PROTEIN,
                kcalMax,
                request.animalProteinGMax(),
                request.plantProteinGMax());
        checkMacroEnergy(
                "Béo",
                "animalLipidGMax",
                KCAL_PER_G_LIPID,
                kcalMax,
                request.animalLipidGMax(),
                request.plantLipidGMax());
        checkMacroEnergy("Bột đường", "glucidGMax", KCAL_PER_G_GLUCID, kcalMax, request.glucidGMax());
    }

    private static void checkMacroEnergy(
            String label, String field, BigDecimal kcalPerGram, BigDecimal kcalMax, BigDecimal... maxGrams) {
        BigDecimal grams = BigDecimal.ZERO;
        for (BigDecimal part : maxGrams) {
            if (part != null) {
                grams = grams.add(part);
            }
        }
        BigDecimal kcal = grams.multiply(kcalPerGram);
        if (kcal.compareTo(kcalMax) > 0) {
            throw GlobalException.withFieldErrors(
                    ApiCode.INVALID_REQUEST_DATA,
                    List.of(new FieldErrorResponse(
                            field,
                            label + " tối đa " + grams.stripTrailingZeros().toPlainString() + " g quy ra "
                                    + kcal.stripTrailingZeros().toPlainString()
                                    + " kcal, vượt năng lượng tối đa ("
                                    + kcalMax.stripTrailingZeros().toPlainString() + " kcal).")));
        }
    }

    private static void checkRange(String label, BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw GlobalException.of(ApiCode.NUTRITION_NORM_RANGE_INVALID, label);
        }
    }
}
