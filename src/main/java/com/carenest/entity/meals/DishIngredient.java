package com.carenest.entity.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một ô định lượng: thực phẩm × nhóm tuổi, lượng ăn được (g hoặc ml) cho một trẻ. */
@Getter
@Entity
@Table(name = "dish_ingredients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DishIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dish_id", nullable = false)
    private Dish dish;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Column(name = "age_group_id", nullable = false)
    private Long ageGroupId;

    @Column(name = "grams_per_child", nullable = false)
    private BigDecimal gramsPerChild;

    public static DishIngredient create(Food food, Long ageGroupId, BigDecimal gramsPerChild) {
        DishIngredient ingredient = new DishIngredient();
        ingredient.food = food;
        ingredient.ageGroupId = ageGroupId;
        ingredient.gramsPerChild = gramsPerChild;
        return ingredient;
    }

    void attachTo(Dish dish) {
        this.dish = dish;
    }
}
