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
 * Giá suất và chuẩn dinh dưỡng của một nhóm tuổi từ ngày hiệu lực (NUT-17, NUT-19). Bản đã tới ngày hiệu lực chỉ đọc,
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
        this.normBasis = normBasis;
        this.updatedBy = updatedBy;
    }

    /** Khoảng chuẩn dinh dưỡng; null = trường chưa chốt chuẩn cho chỉ số đó. */
    public record Norms(
            BigDecimal kcalMin,
            BigDecimal kcalMax,
            BigDecimal proteinPctMin,
            BigDecimal proteinPctMax,
            BigDecimal lipidPctMin,
            BigDecimal lipidPctMax,
            BigDecimal glucidPctMin,
            BigDecimal glucidPctMax) {}
}
