package com.carenest.entity.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Giá suất và chuẩn dinh dưỡng của một nhóm tuổi từ ngày hiệu lực (NUT-17, NUT-19, NUT-24, NUT-25). Bản đã tới ngày hiệu lực chỉ đọc,
 * đổi thì tạo bản mới. Bữa trong ngày nằm ở {@code age_groups.meal_sessions}.
 */
@Getter
@Entity
@Table(name = "age_group_meal_configs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgeGroupMealConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "age_group_id", nullable = false)
    private Long ageGroupId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "meal_price", nullable = false)
    private BigDecimal mealPrice;

    @Column(name = "kcal_min")
    private BigDecimal kcalMin;

    @Column(name = "kcal_max")
    private BigDecimal kcalMax;

    @Column(name = "protein_pct_min")
    private BigDecimal proteinPctMin;

    @Column(name = "protein_pct_max")
    private BigDecimal proteinPctMax;

    @Column(name = "lipid_pct_min")
    private BigDecimal lipidPctMin;

    @Column(name = "lipid_pct_max")
    private BigDecimal lipidPctMax;

    @Column(name = "glucid_pct_min")
    private BigDecimal glucidPctMin;

    @Column(name = "glucid_pct_max")
    private BigDecimal glucidPctMax;

    @Column(name = "animal_protein_g_min")
    private BigDecimal animalProteinGMin;

    @Column(name = "animal_protein_g_max")
    private BigDecimal animalProteinGMax;

    @Column(name = "plant_protein_g_min")
    private BigDecimal plantProteinGMin;

    @Column(name = "plant_protein_g_max")
    private BigDecimal plantProteinGMax;

    @Column(name = "animal_lipid_g_min")
    private BigDecimal animalLipidGMin;

    @Column(name = "animal_lipid_g_max")
    private BigDecimal animalLipidGMax;

    @Column(name = "plant_lipid_g_min")
    private BigDecimal plantLipidGMin;

    @Column(name = "plant_lipid_g_max")
    private BigDecimal plantLipidGMax;

    @Column(name = "glucid_g_min")
    private BigDecimal glucidGMin;

    @Column(name = "glucid_g_max")
    private BigDecimal glucidGMax;

    @Column(name = "morning_kcal_pct_min")
    private BigDecimal morningKcalPctMin;

    @Column(name = "morning_kcal_pct_max")
    private BigDecimal morningKcalPctMax;

    @Column(name = "lunch_kcal_pct_min")
    private BigDecimal lunchKcalPctMin;

    @Column(name = "lunch_kcal_pct_max")
    private BigDecimal lunchKcalPctMax;

    @Column(name = "afternoon_kcal_pct_min")
    private BigDecimal afternoonKcalPctMin;

    @Column(name = "afternoon_kcal_pct_max")
    private BigDecimal afternoonKcalPctMax;

    @Column(name = "daily_kcal_need")
    private BigDecimal dailyKcalNeed;

    @Column(name = "norm_basis", nullable = false, length = 300)
    private String normBasis = "";

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

    public static AgeGroupMealConfig create(Long ageGroupId, Long createdBy) {
        AgeGroupMealConfig config = new AgeGroupMealConfig();
        config.ageGroupId = ageGroupId;
        config.createdBy = createdBy;
        config.updatedBy = createdBy;
        return config;
    }

    public void changeInfo(
            LocalDate effectiveFrom, BigDecimal mealPrice, Norms norms, String normBasis, Long updatedBy) {
        this.effectiveFrom = effectiveFrom;
        this.mealPrice = mealPrice;
        this.kcalMin = norms.kcalMin();
        this.kcalMax = norms.kcalMax();
        this.proteinPctMin = norms.proteinPctMin();
        this.proteinPctMax = norms.proteinPctMax();
        this.lipidPctMin = norms.lipidPctMin();
        this.lipidPctMax = norms.lipidPctMax();
        this.glucidPctMin = norms.glucidPctMin();
        this.glucidPctMax = norms.glucidPctMax();
        this.animalProteinGMin = norms.animalProteinGMin();
        this.animalProteinGMax = norms.animalProteinGMax();
        this.plantProteinGMin = norms.plantProteinGMin();
        this.plantProteinGMax = norms.plantProteinGMax();
        this.animalLipidGMin = norms.animalLipidGMin();
        this.animalLipidGMax = norms.animalLipidGMax();
        this.plantLipidGMin = norms.plantLipidGMin();
        this.plantLipidGMax = norms.plantLipidGMax();
        this.glucidGMin = norms.glucidGMin();
        this.glucidGMax = norms.glucidGMax();
        this.morningKcalPctMin = norms.morningKcalPctMin();
        this.morningKcalPctMax = norms.morningKcalPctMax();
        this.lunchKcalPctMin = norms.lunchKcalPctMin();
        this.lunchKcalPctMax = norms.lunchKcalPctMax();
        this.afternoonKcalPctMin = norms.afternoonKcalPctMin();
        this.afternoonKcalPctMax = norms.afternoonKcalPctMax();
        this.dailyKcalNeed = norms.dailyKcalNeed();
        this.normBasis = normBasis;
        this.updatedBy = updatedBy;
    }

    /**
     * Khoảng chuẩn dinh dưỡng; null = trường chưa chốt chuẩn cho chỉ số đó. P:L:G là % năng lượng (cơ cấu áp dụng),
     * đạm/béo ĐV-TV và bột đường là gam/trẻ/ngày (NUT-24), mỗi bữa là % nhu cầu năng lượng cả ngày {@code dailyKcalNeed} (NUT-25).
     */
    public record Norms(
            BigDecimal kcalMin,
            BigDecimal kcalMax,
            BigDecimal proteinPctMin,
            BigDecimal proteinPctMax,
            BigDecimal lipidPctMin,
            BigDecimal lipidPctMax,
            BigDecimal glucidPctMin,
            BigDecimal glucidPctMax,
            BigDecimal animalProteinGMin,
            BigDecimal animalProteinGMax,
            BigDecimal plantProteinGMin,
            BigDecimal plantProteinGMax,
            BigDecimal animalLipidGMin,
            BigDecimal animalLipidGMax,
            BigDecimal plantLipidGMin,
            BigDecimal plantLipidGMax,
            BigDecimal glucidGMin,
            BigDecimal glucidGMax,
            BigDecimal morningKcalPctMin,
            BigDecimal morningKcalPctMax,
            BigDecimal lunchKcalPctMin,
            BigDecimal lunchKcalPctMax,
            BigDecimal afternoonKcalPctMin,
            BigDecimal afternoonKcalPctMax,
            BigDecimal dailyKcalNeed) {}
}
