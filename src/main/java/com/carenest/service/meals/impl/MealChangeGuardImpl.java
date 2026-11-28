package com.carenest.service.meals.impl;

import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.InspectionSheetRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.service.attendance.MealChangeGuard;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.InspectionStatus;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Khóa cơ sở–ngày trước khi kiểm sổ suất đã xác nhận và phiếu bếp đã ký (ATT-02a, ATT-07). */
@Component
@RequiredArgsConstructor
class MealChangeGuardImpl implements MealChangeGuard {

    private final MealCountRepository mealCountRepository;
    private final InspectionSheetRepository inspectionSheetRepository;

    @Override
    public void ensureChangeAllowed(Long campusId, Long schoolYearId, LocalDate date, Set<MealSession> sessions) {
        if (sessions.isEmpty()) {
            return;
        }
        mealCountRepository.lockCampusDay(campusId, date);
        inspectionSheetRepository.lockCampus(campusId);
        boolean signed = Stream.of(InspectionTab.PROCESS, InspectionTab.SERVE)
                .anyMatch(tab -> inspectionSheetRepository.existsByCampusIdAndSheetDateAndTabAndStatus(
                        campusId, date, tab, InspectionStatus.SIGNED));
        if (signed) {
            throw GlobalException.of(ApiCode.MEAL_COUNT_INSPECTION_SIGNED, date);
        }
        boolean confirmed = mealCountRepository.findByCampusIdAndDate(campusId, date).stream()
                .anyMatch(count -> count.getSchoolYearId().equals(schoolYearId)
                        && sessions.contains(count.getSession())
                        && count.isConfirmed());
        if (confirmed) {
            throw GlobalException.of(ApiCode.MEAL_COUNT_ALREADY_CONFIRMED);
        }
    }
}
