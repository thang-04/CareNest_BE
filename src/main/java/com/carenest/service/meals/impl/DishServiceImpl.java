package com.carenest.service.meals.impl;

import com.carenest.dto.meals.AllergenResponse;
import com.carenest.dto.meals.DishCopyRequest;
import com.carenest.dto.meals.DishDetailResponse;
import com.carenest.dto.meals.DishRequest;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.entity.meals.Allergen;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.Food;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.DishService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodOrigin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
public class DishServiceImpl implements DishService {
    private static final String LOG_PREFIX = "[DishServiceImpl]";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DishRepository dishRepository;
    private final FoodRepository foodRepository;
    private final AgeGroupService ageGroupService;
    private final NutritionCatalogMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<DishDetailResponse> getDishes(
            String keyword, DishType dishType, Boolean reference, CatalogStatus status) {
        String logPrefix = LOG_PREFIX + "|getDishes";
        log.info("{}|START", logPrefix);
        try {
            String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
            List<DishDetailResponse> response = dishRepository.findAllByOrderByNameAsc().stream()
                    .filter(dish -> term.isEmpty()
                            || dish.getName().toLowerCase(Locale.ROOT).contains(term)
                            || dish.getCode().toLowerCase(Locale.ROOT).contains(term))
                    .filter(dish -> dishType == null || dish.getDishType() == dishType)
                    .filter(dish -> reference == null || dish.isReference() == reference)
                    .filter(dish -> status == null || dish.getStatus() == status)
                    .map(this::toDetail)
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
    public DishDetailResponse getDish(Long dishId) {
        String logPrefix = LOG_PREFIX + "|getDish|dishId=" + dishId;
        log.info("{}|START", logPrefix);
        try {
            DishDetailResponse response = toDetail(findDish(dishId));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public DishDetailResponse createDish(CurrentUser currentUser, DishRequest request) {
        String logPrefix = LOG_PREFIX + "|createDish|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            String code = codeOrNext(request.code());
            Dish dish = Dish.create(currentUser.userId());
            dish.changeInfo(
                    code,
                    request.name().trim(),
                    request.dishType(),
                    textOf(request.description()),
                    currentUser.userId());
            dish.replaceIngredients(buildIngredients(request.ingredients()));
            dish = dishRepository.save(dish);
            log.info("{}|dishId={}|END", logPrefix, dish.getId());
            return toDetail(dish);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public DishDetailResponse updateDish(CurrentUser currentUser, Long dishId, DishRequest request) {
        String logPrefix = LOG_PREFIX + "|updateDish|userId=" + currentUser.userId() + "|dishId=" + dishId;
        log.info("{}|START", logPrefix);
        try {
            Dish dish = findEditableDish(dishId);
            // Mã để trống khi sửa ⇒ giữ mã cũ
            String code =
                    isBlank(request.code()) ? dish.getCode() : request.code().trim();
            if (dishRepository.existsByCodeAndIdNot(code, dishId)) {
                throw GlobalException.of(ApiCode.DISH_CODE_DUPLICATED, code);
            }
            dish.changeInfo(
                    code,
                    request.name().trim(),
                    request.dishType(),
                    textOf(request.description()),
                    currentUser.userId());
            List<DishIngredient> ingredients = buildIngredients(request.ingredients());
            // Hibernate chèn trước khi xóa ⇒ xóa lưới cũ và flush trước, tránh trùng unique (món, thực phẩm, nhóm tuổi)
            dish.replaceIngredients(List.of());
            dishRepository.flush();
            dish.replaceIngredients(ingredients);
            dishRepository.flush();
            log.info("{}|END", logPrefix);
            return toDetail(dish);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public DishDetailResponse changeDishStatus(CurrentUser currentUser, Long dishId, CatalogStatus status) {
        String logPrefix = LOG_PREFIX + "|changeDishStatus|userId=" + currentUser.userId() + "|dishId=" + dishId
                + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            Dish dish = findEditableDish(dishId);
            dish.changeStatus(status, currentUser.userId());
            dishRepository.flush();
            log.info("{}|END", logPrefix);
            return toDetail(dish);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public DishDetailResponse copyDish(CurrentUser currentUser, Long dishId, DishCopyRequest request) {
        String logPrefix = LOG_PREFIX + "|copyDish|userId=" + currentUser.userId() + "|dishId=" + dishId;
        log.info("{}|START", logPrefix);
        try {
            Dish source = findDish(dishId);
            String code = codeOrNext(request.code());
            // Bản sao luôn là món của trường (sửa được), giữ nguyên định lượng của món gốc
            Dish copy = Dish.create(currentUser.userId());
            copy.changeInfo(
                    code, request.name().trim(), source.getDishType(), source.getDescription(), currentUser.userId());
            copy.replaceIngredients(source.getIngredients().stream()
                    .map(item -> DishIngredient.create(item.getFood(), item.getAgeGroupId(), item.getGramsPerChild()))
                    .toList());
            copy = dishRepository.save(copy);
            log.info("{}|newDishId={}|END", logPrefix, copy.getId());
            return toDetail(copy);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Mã nhập tay phải chưa có; để trống ⇒ tự sinh MA001, MA002… */
    private String codeOrNext(String requested) {
        if (!isBlank(requested)) {
            String code = requested.trim();
            if (dishRepository.existsByCode(code)) {
                throw GlobalException.of(ApiCode.DISH_CODE_DUPLICATED, code);
            }
            return code;
        }
        long number = dishRepository.count() + 1;
        String code = String.format("MA%03d", number);
        while (dishRepository.existsByCode(code)) {
            number++;
            code = String.format("MA%03d", number);
        }
        return code;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Dish findDish(Long dishId) {
        return dishRepository
                .findWithIngredientsById(dishId)
                .orElseThrow(() -> GlobalException.of(ApiCode.DISH_NOT_FOUND));
    }

    // Món tham khảo chỉ đọc; trường sao chép thành món của mình để sửa (NUT-18)
    private Dish findEditableDish(Long dishId) {
        Dish dish = findDish(dishId);
        if (dish.isReference()) {
            throw GlobalException.of(ApiCode.DISH_REFERENCE_READ_ONLY);
        }
        return dish;
    }

    private List<DishIngredient> buildIngredients(List<DishRequest.Ingredient> items) {
        Set<Long> ageGroupIds = ageGroupService.getAgeGroups().stream()
                .map(AgeGroupDetailResponse::id)
                .collect(Collectors.toSet());
        Map<Long, Food> foods =
                foodRepository
                        .findAllById(items.stream()
                                .map(DishRequest.Ingredient::foodId)
                                .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Food::getId, Function.identity()));
        Set<String> cells = new HashSet<>();
        return items.stream()
                .map(item -> {
                    Food food = foods.get(item.foodId());
                    if (food == null || food.getStatus() != CatalogStatus.ACTIVE) {
                        throw GlobalException.of(
                                ApiCode.DISH_INGREDIENT_INVALID, "thực phẩm không có hoặc đã ngừng dùng");
                    }
                    if (!ageGroupIds.contains(item.ageGroupId())) {
                        throw GlobalException.of(ApiCode.DISH_INGREDIENT_INVALID, "nhóm tuổi không tồn tại");
                    }
                    if (!cells.add(item.foodId() + ":" + item.ageGroupId())) {
                        throw GlobalException.of(
                                ApiCode.DISH_INGREDIENT_INVALID, "một thực phẩm nhập hai lần cho cùng nhóm tuổi");
                    }
                    return DishIngredient.create(food, item.ageGroupId(), item.gramsPerChild());
                })
                .toList();
    }

    private DishDetailResponse toDetail(Dish dish) {
        List<DishDetailResponse.Ingredient> ingredients = dish.getIngredients().stream()
                .map(item -> new DishDetailResponse.Ingredient(
                        item.getFood().getId(),
                        item.getFood().getCode(),
                        item.getFood().getName(),
                        item.getFood().getUnit(),
                        item.getAgeGroupId(),
                        item.getGramsPerChild()))
                .toList();
        Map<Long, List<DishIngredient>> byAgeGroup = dish.getIngredients().stream()
                .collect(Collectors.groupingBy(DishIngredient::getAgeGroupId, TreeMap::new, Collectors.toList()));
        List<DishDetailResponse.AgeGroupNutrition> nutrition = byAgeGroup.entrySet().stream()
                .map(entry -> nutritionOf(entry.getKey(), entry.getValue()))
                .toList();
        Set<Allergen> allergens = new LinkedHashSet<>();
        dish.getIngredients().forEach(item -> allergens.addAll(item.getFood().getAllergens()));
        List<AllergenResponse> allergenResponses = mapper.toAllergenResponses(allergens);
        return new DishDetailResponse(
                dish.getId(),
                dish.getCode(),
                dish.getName(),
                dish.getDishType(),
                dish.getDescription(),
                dish.isReference(),
                dish.getStatus(),
                ingredients,
                nutrition,
                allergenResponses);
    }

    /**
     * NUT-06, NUT-18: dinh dưỡng một suất = Σ lượng ăn được × giá trị/100. Giá một suất tính lượng mua, cộng hệ số thải bỏ
     * (NUT-07): lượng × (1 + thải bỏ%) × giá đơn vị mua / số đơn vị trong một đơn vị mua.
     */
    private static DishDetailResponse.AgeGroupNutrition nutritionOf(Long ageGroupId, List<DishIngredient> items) {
        BigDecimal kcal = BigDecimal.ZERO;
        BigDecimal animalProtein = BigDecimal.ZERO;
        BigDecimal plantProtein = BigDecimal.ZERO;
        BigDecimal animalLipid = BigDecimal.ZERO;
        BigDecimal plantLipid = BigDecimal.ZERO;
        BigDecimal glucid = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (DishIngredient item : items) {
            Food food = item.getFood();
            BigDecimal grams = item.getGramsPerChild();
            kcal = kcal.add(grams.multiply(food.getKcal()));
            // NUT-24: đạm, béo của thực phẩm tính vào nhóm theo nguồn gốc
            if (food.getOrigin() == FoodOrigin.ANIMAL) {
                animalProtein = animalProtein.add(grams.multiply(food.getProtein()));
                animalLipid = animalLipid.add(grams.multiply(food.getLipid()));
            } else {
                plantProtein = plantProtein.add(grams.multiply(food.getProtein()));
                plantLipid = plantLipid.add(grams.multiply(food.getLipid()));
            }
            glucid = glucid.add(grams.multiply(food.getGlucid()));
            BigDecimal purchased = grams.multiply(HUNDRED.add(food.getWastePct()));
            cost = cost.add(purchased
                    .multiply(food.getUnitPrice())
                    .divide(food.getPurchaseUnitSize(), 10, RoundingMode.HALF_UP));
        }
        return new DishDetailResponse.AgeGroupNutrition(
                ageGroupId,
                per100(kcal),
                per100(animalProtein),
                per100(plantProtein),
                per100(animalProtein.add(plantProtein)),
                per100(animalLipid),
                per100(plantLipid),
                per100(animalLipid.add(plantLipid)),
                per100(glucid),
                per100(cost));
    }

    private static BigDecimal per100(BigDecimal value) {
        return value.divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static String textOf(String value) {
        return value == null ? "" : value.trim();
    }
}
