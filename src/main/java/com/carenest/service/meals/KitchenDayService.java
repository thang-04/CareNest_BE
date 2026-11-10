package com.carenest.service.meals;

import com.carenest.dto.meals.KitchenDayResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/** Bếp hôm nay: thực đơn đã công bố, suất đã xác nhận theo lớp và lượng thực phẩm cần (NUT-03, AUTH-05). */
public interface KitchenDayService {

    /** Bếp xem điểm trường mình (campusId bỏ trống được); HT/PHT bắt buộc campusId. {@code date} bỏ trống ⇒ hôm nay. */
    KitchenDayResponse getKitchenDay(CurrentUser currentUser, LocalDate date, Long campusId);
}
