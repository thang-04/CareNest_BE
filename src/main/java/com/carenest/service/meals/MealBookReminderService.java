package com.carenest.service.meals;

/** Nhắc BGH (OBS-30): quá giờ chốt 30 phút mà sổ báo ăn của điểm trường hôm nay chưa xác nhận (NUT-01). */
public interface MealBookReminderService {

    int remindUnconfirmedMealBook();
}
