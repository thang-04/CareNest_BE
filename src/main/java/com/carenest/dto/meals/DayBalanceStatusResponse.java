package com.carenest.dto.meals;

import java.time.LocalDate;
import java.util.List;

/**
 * Trạng thái cân đối lần 1 của một ngày. {@code problems} là các lý do chưa đạt để hiện cho người lập thực đơn; rỗng khi
 * đạt hoặc ngày nghỉ. {@code offReason}: vì sao ngày nghỉ (kế hoạch tuần, cuối tuần, lịch nghỉ của trường); null = ngày học.
 */
public record DayBalanceStatusResponse(
        LocalDate date,
        boolean dayOff,
        String offReason,
        boolean saved,
        boolean stale,
        boolean passed,
        List<String> problems) {}
