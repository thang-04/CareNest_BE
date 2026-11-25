package com.carenest.mapper.meals;

import com.carenest.dto.meals.AgeGroupMealConfigResponse;
import com.carenest.dto.meals.AllergenResponse;
import com.carenest.dto.meals.FoodResponse;
import com.carenest.dto.meals.SupplierResponse;
import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.Supplier;
import com.carenest.service.meals.FoodPriceService.FoodPriceBook;
import com.carenest.utils.NutritionConversion;
import com.carenest.utils.enums.Allergen;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NutritionCatalogMapper {

    public AllergenResponse toResponse(Allergen allergen) {
        return new AllergenResponse(allergen.getId(), allergen.getCode(), allergen.getName());
    }

    public List<AllergenResponse> toAllergenResponses(Collection<Allergen> allergens) {
        return allergens.stream().distinct().sorted().map(this::toResponse).toList();
    }

    public FoodResponse toResponse(Food food, FoodPriceBook.PriceAt price) {
        return new FoodResponse(
                food.getId(),
                food.getCode(),
                food.getName(),
                food.getFoodGroup(),
                food.getUnit(),
                food.getPurchaseUnit(),
                food.getPurchaseUnitSize(),
                price.price(),
                food.getKcal(),
                food.getProtein(),
                food.getLipid(),
                food.getGlucid(),
                food.getCalcium(),
                food.getPhosphorus(),
                food.getIron(),
                food.getVitaminA(),
                food.getVitaminB1(),
                food.getVitaminB2(),
                food.getVitaminPp(),
                food.getVitaminC(),
                food.getZinc(),
                food.getNutrientSource(),
                food.getWastePct(),
                food.getStorage(),
                food.getOrigin(),
                food.getNote(),
                food.getStatus(),
                toAllergenResponses(food.getAllergens()),
                price.supplierId(),
                price.supplierName(),
                price.contractStatus());
    }

    public AgeGroupMealConfigResponse toResponse(
            AgeGroupMealConfig config, LocalDate today, NutritionConversion conversion) {
        return new AgeGroupMealConfigResponse(
                config.getId(),
                config.getAgeGroupId(),
                config.getEffectiveFrom(),
                config.getMealPrice(),
                config.getServiceFee(),
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
                config.getCalciumMin(),
                config.getCalciumMax(),
                config.getPhosphorusMin(),
                config.getPhosphorusMax(),
                config.getIronMin(),
                config.getIronMax(),
                config.getVitaminAMin(),
                config.getVitaminAMax(),
                config.getVitaminB1Min(),
                config.getVitaminB1Max(),
                config.getVitaminB2Min(),
                config.getVitaminB2Max(),
                config.getVitaminPpMin(),
                config.getVitaminPpMax(),
                config.getVitaminCMin(),
                config.getVitaminCMax(),
                config.getZincMin(),
                config.getZincMax(),
                config.getAppliedProteinPct(),
                config.getAppliedLipidPct(),
                config.getAppliedGlucidPct(),
                conversion.toResponse(
                        config.getKcalMin(),
                        config.getKcalMax(),
                        config.getAppliedProteinPct(),
                        config.getAppliedLipidPct(),
                        config.getAppliedGlucidPct()),
                config.getNormBasis(),
                config.getEffectiveFrom().isAfter(today));
    }

    public SupplierResponse toResponse(Supplier supplier, boolean showIdCard, LocalDate today) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getPhone(),
                supplier.getAddress(),
                showIdCard ? supplier.getIdCard() : null,
                supplier.getDeliveryPerson(),
                supplier.getStatus(),
                supplier.getContractNo(),
                supplier.getContractStart(),
                supplier.getContractEnd(),
                FoodPriceBook.contractStatus(
                        new FoodPriceBook.SupplierInfo(
                                supplier.getId(),
                                supplier.getName(),
                                supplier.getContractNo(),
                                supplier.getContractStart(),
                                supplier.getContractEnd()),
                        today));
    }
}
