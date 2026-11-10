package com.carenest.entity.meals;

import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Món = công thức nguyên liệu + gam/trẻ theo nhóm tuổi (NUT-18). Món tham khảo chỉ đọc, sao chép mới sửa được. */
@Getter
@Entity
@Table(name = "dishes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "dish_type", nullable = false, length = 20)
    private DishType dishType;

    @Column(nullable = false, length = 500)
    private String description = "";

    @Column(nullable = false)
    private boolean reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    @OneToMany(mappedBy = "dish", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DishIngredient> ingredients = new ArrayList<>();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Dish create(Long createdBy) {
        Dish dish = new Dish();
        dish.createdBy = createdBy;
        dish.updatedBy = createdBy;
        return dish;
    }

    public void changeInfo(String code, String name, DishType dishType, String description, Long updatedBy) {
        this.code = code;
        this.name = name;
        this.dishType = dishType;
        this.description = description;
        this.updatedBy = updatedBy;
    }

    /** Thay toàn bộ lưới định lượng; FE gửi cả lưới sau khi chép giữa nhóm tuổi. */
    public void replaceIngredients(List<DishIngredient> newIngredients) {
        ingredients.clear();
        newIngredients.forEach(ingredient -> {
            ingredient.attachTo(this);
            ingredients.add(ingredient);
        });
    }

    public void changeStatus(CatalogStatus status, Long updatedBy) {
        this.status = status;
        this.updatedBy = updatedBy;
    }
}
