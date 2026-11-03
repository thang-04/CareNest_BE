package com.carenest.service.meals;

import com.carenest.dto.meals.DayBoardResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/** Bán trú hôm nay: trạng thái việc của ngày theo điểm trường, chỉ đọc (không tự tổng hợp sổ suất). */
public interface DayBoardService {

    /** date null = hôm nay; campusId null = mọi điểm trường người xem đọc được. */
    DayBoardResponse getDayBoard(CurrentUser currentUser, LocalDate date, Long campusId);
}
