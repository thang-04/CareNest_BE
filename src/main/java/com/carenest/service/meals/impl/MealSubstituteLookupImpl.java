package com.carenest.service.meals.impl;

import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealCountLine;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.service.attendance.MealSubstituteLookup;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Suất thay thế theo sổ báo ăn đang hiệu lực với người duyệt (số chờ xác nhận lại nếu có, không thì số đã chốt). */
@Component
@RequiredArgsConstructor
public class MealSubstituteLookupImpl implements MealSubstituteLookup {

    private final MealCountRepository mealCountRepository;

    @Override
    public Map<LocalDate, Integer> substitutes(Long campusId, Long classroomId, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> byDate = new HashMap<>();
        for (MealCount count : mealCountRepository.findByCampusIdAndDateBetween(campusId, from, to)) {
            count.visibleLine(classroomId)
                    .map(MealCountLine::getSubstitute)
                    .ifPresent(sub -> byDate.merge(count.getDate(), sub, Math::max));
        }
        return byDate;
    }
}
