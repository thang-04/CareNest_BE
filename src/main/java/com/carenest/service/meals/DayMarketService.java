package com.carenest.service.meals;

import com.carenest.dto.meals.DayMarketResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

public interface DayMarketService {

    /** Bảng đi chợ của một điểm trường trong một ngày; {@code date} null ⇒ hôm nay. */
    DayMarketResponse getDayMarket(CurrentUser currentUser, Long campusId, LocalDate date);
}
