package com.carenest.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DevClockFilterTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant REAL = Instant.parse("2026-11-15T03:00:00Z");

    private final Clock clock = new RequestAwareClock(Clock.fixed(REAL, VN));
    private final DevClockFilter filter = new DevClockFilter();

    private Instant instantSeenByRequest(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/attendance/day-info");
        if (header != null) request.addHeader(DevClockFilter.HEADER, header);
        AtomicReference<Instant> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(clock.instant()));
        return seen.get();
    }

    @Test
    @DisplayName("Header hợp lệ: request chạy theo giờ giả lập, hết request trở về giờ thật")
    void simulatesTimeOnlyWithinRequest() throws Exception {
        Instant seen = instantSeenByRequest("2026-12-01T08:30:00+07:00");

        assertThat(seen).isEqualTo(Instant.parse("2026-12-01T01:30:00Z"));
        assertThat(LocalDate.ofInstant(seen, clock.getZone())).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(clock.instant()).isEqualTo(REAL);
        assertThat(RequestAwareClock.simulatedInstant()).isEmpty();
    }

    @Test
    @DisplayName("Không có header hoặc header sai định dạng: giữ giờ thật")
    void missingOrInvalidHeaderKeepsRealTime() throws Exception {
        assertThat(instantSeenByRequest(null)).isEqualTo(REAL);
        assertThat(instantSeenByRequest("01/12/2026 08:30")).isEqualTo(REAL);
        assertThat(instantSeenByRequest("2026-12-01T08:30:00")).isEqualTo(REAL);
    }

    @Test
    @DisplayName("Lỗi trong request vẫn xóa giờ giả lập khỏi thread")
    void clearsSimulationWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader(DevClockFilter.HEADER, "2026-12-01T08:30:00+07:00");
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception ignored) {
            // chỉ kiểm tra dọn dẹp
        }
        assertThat(RequestAwareClock.simulatedInstant()).isEmpty();
        assertThat(clock.instant()).isEqualTo(REAL);
    }

    @Test
    @DisplayName("withZone giữ hành vi giả lập")
    void withZoneKeepsSimulation() {
        Clock utc = clock.withZone(ZoneId.of("UTC"));
        Instant simulated = Instant.parse("2027-01-01T00:00:00Z");
        RequestAwareClock.simulate(simulated);
        try {
            assertThat(utc.instant()).isEqualTo(simulated);
            assertThat(utc.getZone()).isEqualTo(ZoneId.of("UTC"));
        } finally {
            RequestAwareClock.clearSimulation();
        }
    }
}
