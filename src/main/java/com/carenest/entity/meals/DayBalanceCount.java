package com.carenest.entity.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Số trẻ của một nhóm tuổi ở một điểm trường trong bản cân đối. */
@Embeddable
public record DayBalanceCount(
        @Column(name = "campus_id", nullable = false) Long campusId,
        @Column(name = "age_group_id", nullable = false) Long ageGroupId,
        @Column(name = "children", nullable = false) int children) {}
