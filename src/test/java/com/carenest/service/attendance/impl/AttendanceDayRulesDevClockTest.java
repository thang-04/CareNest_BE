package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.config.DevClockFilter;
import com.carenest.config.RequestAwareClock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** ATT-06 tính theo giờ giả lập của request: cùng một ngày điểm danh, giờ thật không đổi, kết quả đổi theo header. */
class AttendanceDayRulesDevClockTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);
    private static final LocalTime CUTOFF = LocalTime.of(9, 0);

    // Giờ thật cố định sau ngày điểm danh: không giả lập thì ngày luôn khóa
    private final Clock clock = new RequestAwareClock(Clock.fixed(Instant.parse("2026-12-10T03:00:00Z"), VN));
    private final AttendanceDayRules rules =
            new AttendanceDayRules(clock, null, null, null, null, null, null, null, null, null);

    private boolean lockedWhenRequestAt(String devNow) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/attendance");
        request.addHeader(DevClockFilter.HEADER, devNow);
        AtomicBoolean locked = new AtomicBoolean();
        new DevClockFilter()
                .doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (req, res) -> locked.set(rules.isBaseLocked(DAY, CUTOFF)));
        return locked.get();
    }

    @Test
    @DisplayName("Chưa đến ngày: giả lập ngày trước đó, ngày điểm danh chưa khóa")
    void beforeDayNotLocked() throws Exception {
        assertThat(lockedWhenRequestAt("2026-11-30T10:00:00+07:00")).isFalse();
    }

    @Test
    @DisplayName("Đang trong thời gian mở: cùng ngày, trước giờ chốt")
    void sameDayBeforeCutoffOpen() throws Exception {
        assertThat(lockedWhenRequestAt("2026-12-01T08:59:00+07:00")).isFalse();
    }

    @Test
    @DisplayName("Đã quá hạn: cùng ngày từ giờ chốt trở đi")
    void sameDayAfterCutoffLocked() throws Exception {
        assertThat(lockedWhenRequestAt("2026-12-01T09:00:00+07:00")).isTrue();
    }

    @Test
    @DisplayName("Không giả lập: theo giờ thật (đã qua ngày) nên khóa")
    void realTimeLocked() {
        assertThat(rules.isBaseLocked(DAY, CUTOFF)).isTrue();
    }
}
