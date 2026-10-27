package com.carenest.service.meals.impl;

import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.meals.MealCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** ADR-0004: nghe điểm danh đổi sau commit, đánh dấu sổ báo ăn cần xác nhận lại (attendance không gọi meals). */
@Component
@RequiredArgsConstructor
class MealCountAttendanceListener {

    private final MealCountService mealCountService;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAttendanceChanged(AttendanceService.AttendanceChangedEvent event) {
        mealCountService.markAttendanceChanged(event.date(), event.campusId(), event.classroomId());
    }
}
