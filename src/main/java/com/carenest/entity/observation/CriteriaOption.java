package com.carenest.entity.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Lựa chọn của tiêu chí; score càng cao càng tốt. */
@Getter
@Entity
@Table(name = "criteria_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CriteriaOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "criteria_id", nullable = false)
    private Long criteriaId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private int score;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
