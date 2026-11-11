package com.carenest.entity.meals;

import com.carenest.entity.attendance.EnumListConverter;
import com.carenest.utils.enums.Allergen;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Thực phẩm dùng chung hai điểm trường (SCH-06). Dinh dưỡng tính trên 100 đơn vị và phải có nguồn (NUT-05). */
@Getter
@Entity
@Table(name = "foods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_group", nullable = false, length = 20)
    private FoodGroup foodGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private FoodUnit unit;

    @Column(name = "purchase_unit", nullable = false, length = 30)
    private String purchaseUnit;

    @Column(name = "purchase_unit_size", nullable = false)
    private BigDecimal purchaseUnitSize;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal kcal;

    @Column(nullable = false)
    private BigDecimal protein;

    @Column(nullable = false)
    private BigDecimal lipid;

    @Column(nullable = false)
    private BigDecimal glucid;

    // NUT-26: vi chất trên 100 đơn vị ăn được; null = chưa có số liệu
    private BigDecimal calcium;

    private BigDecimal phosphorus;

    private BigDecimal iron;

    @Column(name = "vitamin_a")
    private BigDecimal vitaminA;

    @Column(name = "vitamin_b1")
    private BigDecimal vitaminB1;

    @Column(name = "vitamin_b2")
    private BigDecimal vitaminB2;

    @Column(name = "vitamin_pp")
    private BigDecimal vitaminPp;

    @Column(name = "vitamin_c")
    private BigDecimal vitaminC;

    @Column(name = "nutrient_source", nullable = false, length = 200)
    private String nutrientSource;

    @Column(name = "nutrient_source_version", length = 50)
    private String nutrientSourceVersion;

    @Column(name = "waste_pct", nullable = false)
    private BigDecimal wastePct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FoodStorage storage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FoodOrigin origin;

    @Column(nullable = false, length = 500)
    private String note = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    // Danh mục dị ứng cố định nên lưu mã ngay trên thực phẩm, không cần bảng nối
    @Convert(converter = EnumListConverter.Allergens.class)
    @Column(name = "allergen_codes", nullable = false, length = 200)
    private List<Allergen> allergens = List.of();

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

    public static Food create(Long createdBy) {
        Food food = new Food();
        food.createdBy = createdBy;
        food.updatedBy = createdBy;
        return food;
    }

    public void changeInfo(
            Info info, Nutrients nutrients, Micronutrients micro, Collection<Allergen> allergens, Long updatedBy) {
        this.code = info.code();
        this.name = info.name();
        this.foodGroup = info.foodGroup();
        this.unit = info.unit();
        this.purchaseUnit = info.purchaseUnit();
        this.purchaseUnitSize = info.purchaseUnitSize();
        this.unitPrice = info.unitPrice();
        this.wastePct = info.wastePct();
        this.storage = info.storage();
        this.origin = info.origin();
        this.note = info.note();
        this.kcal = nutrients.kcal();
        this.protein = nutrients.protein();
        this.lipid = nutrients.lipid();
        this.glucid = nutrients.glucid();
        this.nutrientSource = nutrients.source();
        this.nutrientSourceVersion = nutrients.sourceVersion();
        this.calcium = micro.calcium();
        this.phosphorus = micro.phosphorus();
        this.iron = micro.iron();
        this.vitaminA = micro.vitaminA();
        this.vitaminB1 = micro.vitaminB1();
        this.vitaminB2 = micro.vitaminB2();
        this.vitaminPp = micro.vitaminPp();
        this.vitaminC = micro.vitaminC();
        this.allergens = allergens.stream().distinct().sorted().toList();
        this.updatedBy = updatedBy;
    }

    public void changeStatus(CatalogStatus status, Long updatedBy) {
        this.status = status;
        this.updatedBy = updatedBy;
    }

    public record Info(
            String code,
            String name,
            FoodGroup foodGroup,
            FoodUnit unit,
            String purchaseUnit,
            BigDecimal purchaseUnitSize,
            BigDecimal unitPrice,
            BigDecimal wastePct,
            FoodStorage storage,
            FoodOrigin origin,
            String note) {}

    /** Giá trị dinh dưỡng trên 100 đơn vị kèm nguồn (NUT-05). */
    public record Nutrients(
            BigDecimal kcal,
            BigDecimal protein,
            BigDecimal lipid,
            BigDecimal glucid,
            String source,
            String sourceVersion) {}

    /** Vi chất trên 100 đơn vị ăn được (NUT-26): mg, riêng vitamin A là µg; null = chưa có số liệu. */
    public record Micronutrients(
            BigDecimal calcium,
            BigDecimal phosphorus,
            BigDecimal iron,
            BigDecimal vitaminA,
            BigDecimal vitaminB1,
            BigDecimal vitaminB2,
            BigDecimal vitaminPp,
            BigDecimal vitaminC) {}
}
