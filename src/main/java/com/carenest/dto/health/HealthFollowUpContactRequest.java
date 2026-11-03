package com.carenest.dto.health;

import java.time.LocalDate;

/** Ghi trao đổi với phụ huynh về kết quả khám: ngày trao đổi, nội dung (tối đa 500 ký tự). */
public record HealthFollowUpContactRequest(LocalDate contactedOn, String note) {}
