package com.carenest.service.meals.impl;

import com.carenest.dto.meals.DayBalanceRequest;
import com.carenest.dto.meals.DayBalanceResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.DayBalanceService;
import java.time.LocalDate;

/** Cân đối mọi ngày có món của tuần (giữ gam công thức) để tuần đủ điều kiện công bố. */
final class BalancedWeek {

    private BalancedWeek() {}

    static void balance(DayBalanceService service, CurrentUser user, LocalDate monday) {
        for (int i = 0; i < 5; i++) {
            LocalDate date = monday.plusDays(i);
            DayBalanceResponse view = service.getBalance(user, date, 1);
            if (view.canEdit() && !view.lines().isEmpty()) {
                service.saveBalance(
                        user, date, 1, new DayBalanceRequest(view.version(), null, null, null, false, null));
            }
        }
    }
}
