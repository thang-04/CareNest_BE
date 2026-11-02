package com.carenest.service.meals.impl;

import com.carenest.dto.meals.AllergenResponse;
import com.carenest.dto.meals.FoodImportRequest;
import com.carenest.dto.meals.FoodImportResponse;
import com.carenest.dto.meals.FoodRequest;
import com.carenest.dto.meals.FoodResponse;
import com.carenest.entity.meals.Allergen;
import com.carenest.entity.meals.Food;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.AllergenRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.FoodService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodStorage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FoodServiceImpl implements FoodService {
    private static final String LOG_PREFIX = "[FoodServiceImpl]";

    private final FoodRepository foodRepository;
    private final AllergenRepository allergenRepository;
    private final NutritionCatalogMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<AllergenResponse> getAllergens() {
        String logPrefix = LOG_PREFIX + "|getAllergens";
        log.info("{}|START", logPrefix);
        try {
            List<AllergenResponse> response = allergenRepository.findAllByOrderBySortOrderAsc().stream()
                    .map(mapper::toResponse)
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
    public List<FoodResponse> getFoods(String keyword, FoodGroup foodGroup, FoodStorage storage, CatalogStatus status) {
        String logPrefix = LOG_PREFIX + "|getFoods";
        log.info("{}|START", logPrefix);
        try {
            String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
            List<FoodResponse> response = foodRepository.findAllByOrderByNameAsc().stream()
                    .filter(food -> term.isEmpty()
                            || food.getName().toLowerCase(Locale.ROOT).contains(term)
                            || food.getCode().toLowerCase(Locale.ROOT).contains(term))
                    .filter(food -> foodGroup == null || food.getFoodGroup() == foodGroup)
                    .filter(food -> storage == null || food.getStorage() == storage)
                    .filter(food -> status == null || food.getStatus() == status)
                    .map(mapper::toResponse)
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
    public FoodResponse getFood(Long foodId) {
        String logPrefix = LOG_PREFIX + "|getFood|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        try {
            FoodResponse response = mapper.toResponse(findFood(foodId));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodResponse createFood(CurrentUser currentUser, FoodRequest request) {
        String logPrefix = LOG_PREFIX + "|createFood|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            requireNutrientSource(request);
            if (foodRepository.existsByCode(request.code().trim())) {
                throw GlobalException.of(
                        ApiCode.FOOD_CODE_DUPLICATED, request.code().trim());
            }
            Food food = Food.create(currentUser.userId());
            apply(food, request, resolveAllergens(request.allergenIds(), allergensById()), currentUser.userId());
            food = foodRepository.save(food);
            log.info("{}|foodId={}|END", logPrefix, food.getId());
            return mapper.toResponse(food);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodResponse updateFood(CurrentUser currentUser, Long foodId, FoodRequest request) {
        String logPrefix = LOG_PREFIX + "|updateFood|userId=" + currentUser.userId() + "|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        try {
            Food food = findFood(foodId);
            requireNutrientSource(request);
            if (foodRepository.existsByCodeAndIdNot(request.code().trim(), foodId)) {
                throw GlobalException.of(
                        ApiCode.FOOD_CODE_DUPLICATED, request.code().trim());
            }
            apply(food, request, resolveAllergens(request.allergenIds(), allergensById()), currentUser.userId());
            foodRepository.flush();
            log.info("{}|END", logPrefix);
            return mapper.toResponse(food);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodResponse changeFoodStatus(CurrentUser currentUser, Long foodId, CatalogStatus status) {
        String logPrefix = LOG_PREFIX + "|changeFoodStatus|userId=" + currentUser.userId() + "|foodId=" + foodId
                + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            Food food = findFood(foodId);
            food.changeStatus(status, currentUser.userId());
            foodRepository.flush();
            log.info("{}|END", logPrefix);
            return mapper.toResponse(food);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodImportResponse importFoods(CurrentUser currentUser, FoodImportRequest request) {
        String logPrefix = LOG_PREFIX + "|importFoods|userId=" + currentUser.userId() + "|rows="
                + request.items().size();
        log.info("{}|START", logPrefix);
        try {
            List<FoodRequest> items = request.items();
            Map<Long, Allergen> allergens = allergensById();
            Set<String> existingCodes =
                    foodRepository
                            .findByCodeIn(items.stream()
                                    .map(item -> item.code().trim())
                                    .toList())
                            .stream()
                            .map(Food::getCode)
                            .collect(Collectors.toSet());
            // Kiểm hết các dòng trước khi lưu: một dòng lỗi thì không lưu dòng nào, báo đủ lỗi theo số dòng
            List<String> errors = new ArrayList<>();
            Set<String> seenCodes = new HashSet<>();
            for (int i = 0; i < items.size(); i++) {
                FoodRequest item = items.get(i);
                String code = item.code().trim();
                String row = "dòng " + (i + 1);
                if (!seenCodes.add(code) || existingCodes.contains(code)) {
                    errors.add(row + " trùng mã " + code);
                }
                if (isBlank(item.nutrientSource())) {
                    errors.add(row + " thiếu nguồn dinh dưỡng");
                }
                if (item.allergenIds() != null && !allergens.keySet().containsAll(item.allergenIds())) {
                    errors.add(row + " chất dị ứng không hợp lệ");
                }
            }
            if (!errors.isEmpty()) {
                throw GlobalException.of(ApiCode.FOOD_IMPORT_INVALID, String.join("; ", errors));
            }
            List<Food> foods = items.stream()
                    .map(item -> {
                        Food food = Food.create(currentUser.userId());
                        apply(food, item, resolveAllergens(item.allergenIds(), allergens), currentUser.userId());
                        return food;
                    })
                    .toList();
            foodRepository.saveAll(foods);
            log.info("{}|END", logPrefix);
            return new FoodImportResponse(foods.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Food findFood(Long foodId) {
        return foodRepository
                .findWithAllergensById(foodId)
                .orElseThrow(() -> GlobalException.of(ApiCode.FOOD_NOT_FOUND));
    }

    // NUT-05: số liệu dinh dưỡng phải ghi rõ nguồn
    private static void requireNutrientSource(FoodRequest request) {
        if (isBlank(request.nutrientSource())) {
            throw GlobalException.of(ApiCode.NUTRIENT_SOURCE_REQUIRED);
        }
    }

    private Map<Long, Allergen> allergensById() {
        return allergenRepository.findAll().stream().collect(Collectors.toMap(Allergen::getId, Function.identity()));
    }

    private static Set<Allergen> resolveAllergens(List<Long> allergenIds, Map<Long, Allergen> allergens) {
        if (allergenIds == null || allergenIds.isEmpty()) {
            return Set.of();
        }
        Set<Allergen> resolved = new HashSet<>();
        for (Long allergenId : allergenIds) {
            Allergen allergen = allergens.get(allergenId);
            if (allergen == null) {
                throw GlobalException.of(ApiCode.ALLERGEN_INVALID);
            }
            resolved.add(allergen);
        }
        return resolved;
    }

    private static void apply(Food food, FoodRequest request, Set<Allergen> allergens, Long userId) {
        food.changeInfo(
                new Food.Info(
                        request.code().trim(),
                        request.name().trim(),
                        request.foodGroup(),
                        request.unit(),
                        request.purchaseUnit().trim(),
                        request.purchaseUnitSize(),
                        request.unitPrice(),
                        request.wastePct(),
                        request.storage(),
                        request.note() == null ? "" : request.note().trim()),
                new Food.Nutrients(
                        request.kcal(),
                        request.protein(),
                        request.lipid(),
                        request.glucid(),
                        request.nutrientSource().trim(),
                        isBlank(request.nutrientSourceVersion())
                                ? null
                                : request.nutrientSourceVersion().trim()),
                allergens,
                userId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
