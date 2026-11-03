package com.carenest.mapper.meals;

import com.carenest.dto.meals.AgeGroupMealConfigResponse;
import com.carenest.dto.meals.AllergenResponse;
import com.carenest.dto.meals.FoodResponse;
import com.carenest.dto.meals.SupplierResponse;
import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.entity.meals.Allergen;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.Supplier;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NutritionCatalogMapper {

    public AllergenResponse toResponse(Allergen allergen) {
        return new AllergenResponse(allergen.getId(), allergen.getCode(), allergen.getName());
    }

    public List<AllergenResponse> toAllergenResponses(Collection<Allergen> allergens) {
        return allergens.stream()
                .sorted(Comparator.comparing(Allergen::getSortOrder))
                .map(this::toResponse)
                .toList();
    }

    public FoodResponse toResponse(Food food) {
        return new FoodResponse(
                food.getId(),
                food.getCode(),
                food.getName(),
                food.getFoodGroup(),
                food.getUnit(),
                food.getPurchaseUnit(),
                food.getPurchaseUnitSize(),
                food.getUnitPrice(),
                food.getKcal(),
                food.getProtein(),
                food.getLipid(),
                food.getGlucid(),
                food.getNutrientSource(),
                food.getNutrientSourceVersion(),
                food.getWastePct(),
                food.getStorage(),
                food.getOrigin(),
                food.getNote(),
                food.getStatus(),
                toAllergenResponses(food.getAllergens()));
    }

    public AgeGroupMealConfigResponse toResponse(AgeGroupMealConfig config, LocalDate today) {
        return new AgeGroupMealConfigResponse(
                config.getId(),
                config.getAgeGroupId(),
                config.getEffectiveFrom(),
                config.getMealPrice(),
                config.getKcalMin(),
                config.getKcalMax(),
                config.getProteinPctMin(),
                config.getProteinPctMax(),
                config.getLipidPctMin(),
                config.getLipidPctMax(),
                config.getGlucidPctMin(),
                config.getGlucidPctMax(),
                config.getAnimalProteinGMin(),
                config.getAnimalProteinGMax(),
                config.getPlantProteinGMin(),
                config.getPlantProteinGMax(),
                config.getAnimalLipidGMin(),
                config.getAnimalLipidGMax(),
                config.getPlantLipidGMin(),
                config.getPlantLipidGMax(),
                config.getGlucidGMin(),
                config.getGlucidGMax(),
                config.getMorningKcalPctMin(),
                config.getMorningKcalPctMax(),
                config.getLunchKcalPctMin(),
                config.getLunchKcalPctMax(),
                config.getAfternoonKcalPctMin(),
                config.getAfternoonKcalPctMax(),
                config.getDailyKcalNeed(),
                config.getNormBasis(),
                config.getEffectiveFrom().isAfter(today));
    }

    public SupplierResponse toResponse(Supplier supplier, boolean showIdCard) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getPhone(),
                supplier.getAddress(),
                showIdCard ? supplier.getIdCard() : null,
                supplier.getDeliveryPerson(),
                supplier.getStatus());
    }
}
