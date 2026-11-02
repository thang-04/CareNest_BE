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
import java.util.Objects;
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
            String code = isBlank(request.code())
                    ? nextCode(Set.of())
                    : request.code().trim();
            if (!isBlank(request.code()) && foodRepository.existsByCode(code)) {
                throw GlobalException.of(ApiCode.FOOD_CODE_DUPLICATED, code);
            }
            Food food = Food.create(currentUser.userId());
            apply(food, request, code, resolveAllergens(request.allergenIds(), allergensById()), currentUser.userId());
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
            // Mã để trống khi sửa ⇒ giữ mã cũ
            String code =
                    isBlank(request.code()) ? food.getCode() : request.code().trim();
            if (foodRepository.existsByCodeAndIdNot(code, foodId)) {
                throw GlobalException.of(ApiCode.FOOD_CODE_DUPLICATED, code);
            }
            apply(food, request, code, resolveAllergens(request.allergenIds(), allergensById()), currentUser.userId());
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
            List<FoodImportRequest.Item> items = request.items();
            Map<Long, Allergen> allergens = allergensById();
            Map<Long, Food> existing = foodRepository
                    .findAllById(items.stream()
                            .map(FoodImportRequest.Item::id)
                            .filter(Objects::nonNull)
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(Food::getId, Function.identity()));
            // Mã đã có trong DB và thực phẩm đang giữ mã đó; trùng chỉ khi khác dòng đang sửa
            Map<String, Long> codeOwners = foodRepository
                    .findByCodeIn(items.stream()
                            .map(FoodImportRequest.Item::data)
                            .filter(data -> !isBlank(data.code()))
                            .map(data -> data.code().trim())
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(Food::getCode, Food::getId));
            // Kiểm hết các dòng trước khi lưu: một dòng lỗi thì không lưu dòng nào, báo đủ lỗi theo số dòng
            List<String> errors = new ArrayList<>();
            Set<String> seenCodes = new HashSet<>();
            for (int i = 0; i < items.size(); i++) {
                FoodImportRequest.Item item = items.get(i);
                FoodRequest data = item.data();
                String row = "dòng " + (i + 1);
                if (item.id() != null && !existing.containsKey(item.id())) {
                    errors.add(row + " không tìm thấy thực phẩm");
                }
                if (!isBlank(data.code())) {
                    String code = data.code().trim();
                    Long owner = codeOwners.get(code);
                    if (!seenCodes.add(code) || (owner != null && !owner.equals(item.id()))) {
                        errors.add(row + " trùng mã " + code);
                    }
                }
                if (isBlank(data.nutrientSource())) {
                    errors.add(row + " thiếu nguồn dinh dưỡng");
                }
                if (data.origin() == null) {
                    errors.add(row + " thiếu nguồn gốc");
                }
                if (data.allergenIds() != null && !allergens.keySet().containsAll(data.allergenIds())) {
                    errors.add(row + " chất dị ứng không hợp lệ");
                }
            }
            if (!errors.isEmpty()) {
                throw GlobalException.of(ApiCode.FOOD_IMPORT_INVALID, String.join("; ", errors));
            }
            // Dòng mới không ghi mã ⇒ tự sinh, tránh trùng mã đã có trong chính lô nhập; dòng sửa để trống ⇒ giữ mã cũ
            Set<String> reserved = new HashSet<>(seenCodes);
            List<Food> foods = new ArrayList<>();
            int created = 0;
            for (FoodImportRequest.Item item : items) {
                FoodRequest data = item.data();
                Food food = item.id() == null ? Food.create(currentUser.userId()) : existing.get(item.id());
                String code;
                if (!isBlank(data.code())) {
                    code = data.code().trim();
                } else if (item.id() != null) {
                    code = food.getCode();
                } else {
                    code = nextCode(reserved);
                }
                reserved.add(code);
                apply(food, data, code, resolveAllergens(data.allergenIds(), allergens), currentUser.userId());
                if (item.id() == null) {
                    created++;
                }
                foods.add(food);
            }
            foodRepository.saveAll(foods);
            log.info("{}|created={}|END", logPrefix, created);
            return new FoodImportResponse(created, foods.size() - created);
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
        // NUT-24: thiếu nguồn gốc thì không tách được đạm, béo động vật / thực vật
        if (request.origin() == null) {
            throw GlobalException.of(ApiCode.FOOD_ORIGIN_REQUIRED);
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

    /** Mã tự sinh TP001, TP002… bỏ qua mã đã có trong DB hoặc đã giữ cho lô đang nhập. */
    private String nextCode(Set<String> reserved) {
        long number = foodRepository.count() + reserved.size() + 1;
        String code = String.format("TP%03d", number);
        while (reserved.contains(code) || foodRepository.existsByCode(code)) {
            number++;
            code = String.format("TP%03d", number);
        }
        return code;
    }

    private static void apply(Food food, FoodRequest request, String code, Set<Allergen> allergens, Long userId) {
        food.changeInfo(
                new Food.Info(
                        code,
                        request.name().trim(),
                        request.foodGroup(),
                        request.unit(),
                        request.purchaseUnit().trim(),
                        request.purchaseUnitSize(),
                        request.unitPrice(),
                        request.wastePct(),
                        request.storage(),
                        request.origin(),
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
