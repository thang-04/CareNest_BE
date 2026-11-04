package com.carenest.service.meals.impl;

import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.entity.meals.Food;
import com.carenest.utils.enums.Micronutrient;
import java.math.BigDecimal;

/** Đọc giá trị từng vi chất (NUT-26) từ thực phẩm và từ định mức nhóm tuổi. */
final class MicronutrientValues {

    private MicronutrientValues() {}

    /** Giá trị trên 100 g/ml ăn được của thực phẩm; null = chưa có số liệu. */
    static BigDecimal of(Food food, Micronutrient nutrient) {
        return switch (nutrient) {
            case CALCIUM -> food.getCalcium();
            case PHOSPHORUS -> food.getPhosphorus();
            case IRON -> food.getIron();
            case VITAMIN_A -> food.getVitaminA();
            case VITAMIN_B1 -> food.getVitaminB1();
            case VITAMIN_B2 -> food.getVitaminB2();
            case VITAMIN_PP -> food.getVitaminPp();
            case VITAMIN_C -> food.getVitaminC();
        };
    }

    static BigDecimal min(AgeGroupMealConfig config, Micronutrient nutrient) {
        return switch (nutrient) {
            case CALCIUM -> config.getCalciumMin();
            case PHOSPHORUS -> config.getPhosphorusMin();
            case IRON -> config.getIronMin();
            case VITAMIN_A -> config.getVitaminAMin();
            case VITAMIN_B1 -> config.getVitaminB1Min();
            case VITAMIN_B2 -> config.getVitaminB2Min();
            case VITAMIN_PP -> config.getVitaminPpMin();
            case VITAMIN_C -> config.getVitaminCMin();
        };
    }

    static BigDecimal max(AgeGroupMealConfig config, Micronutrient nutrient) {
        return switch (nutrient) {
            case CALCIUM -> config.getCalciumMax();
            case PHOSPHORUS -> config.getPhosphorusMax();
            case IRON -> config.getIronMax();
            case VITAMIN_A -> config.getVitaminAMax();
            case VITAMIN_B1 -> config.getVitaminB1Max();
            case VITAMIN_B2 -> config.getVitaminB2Max();
            case VITAMIN_PP -> config.getVitaminPpMax();
            case VITAMIN_C -> config.getVitaminCMax();
        };
    }
}
