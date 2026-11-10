package com.carenest.entity.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Chất gây dị ứng trong danh mục cố định (HLT-06); thực phẩm gắn chất để món suy ra chất dị ứng. */
@Getter
@Entity
@Table(name = "allergens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Allergen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;
}
