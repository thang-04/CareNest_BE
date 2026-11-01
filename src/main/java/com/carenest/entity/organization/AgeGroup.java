package com.carenest.entity.organization;

import com.carenest.utils.enums.MealSession;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "age_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "short_name", nullable = false, length = 50)
    private String shortName;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(name = "age_from_months")
    private Short ageFromMonths;

    @Column(name = "age_to_months")
    private Short ageToMonths;

    @Convert(converter = MealSessionListConverter.class)
    @Column(name = "meal_sessions", nullable = false, length = 40)
    private List<MealSession> mealSessions = new ArrayList<>();

    @Column(name = "max_class_size")
    private Short maxClassSize;

    @Column(name = "nutrition_note", nullable = false, length = 500)
    private String nutritionNote = "";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AgeGroup create(String code, short sortOrder) {
        AgeGroup ageGroup = new AgeGroup();
        ageGroup.code = code;
        ageGroup.sortOrder = sortOrder;
        return ageGroup;
    }

    public void changeInfo(
            String name,
            String shortName,
            short ageFromMonths,
            short ageToMonths,
            List<MealSession> mealSessions,
            short maxClassSize,
            String nutritionNote) {
        this.name = name;
        this.shortName = shortName;
        this.ageFromMonths = ageFromMonths;
        this.ageToMonths = ageToMonths;
        this.mealSessions =
                new ArrayList<>(mealSessions.stream().distinct().sorted().toList());
        this.maxClassSize = maxClassSize;
        this.nutritionNote = nutritionNote;
    }
}
