package com.carenest.utils.enums;

import java.util.List;
import java.util.Set;

/**
 * Buổi trong ngày (ATT-12, Cần chốt 18/11/2026): buổi sáng gồm Phụ sáng + Trưa, buổi chiều gồm bữa Xế. Nhóm tuổi không
 * có bữa nào của buổi thì buổi đó không có bữa.
 */
public enum DayPart {
    MORNING,
    AFTERNOON;

    private static final Set<MealSession> AFTERNOON_MEALS = Set.of(MealSession.AFTERNOON);

    public boolean includes(MealSession session) {
        return this == AFTERNOON ? AFTERNOON_MEALS.contains(session) : !AFTERNOON_MEALS.contains(session);
    }

    /** Các bữa của buổi trong danh sách bữa của nhóm tuổi, theo thứ tự phục vụ. */
    public List<MealSession> mealsOf(List<MealSession> groupSessions) {
        return groupSessions.stream().filter(this::includes).sorted().toList();
    }

    public DayPart other() {
        return this == MORNING ? AFTERNOON : MORNING;
    }
}
