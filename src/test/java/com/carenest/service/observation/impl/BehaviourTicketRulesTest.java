package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.TicketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BehaviourTicketRulesTest {

    @Test
    @DisplayName("OBS-10 (Cần chốt 11/11/2026): phiếu tuần chỉ cần 3 cờ, không còn điều kiện có mặt 4 ngày")
    void weeklyTicketNeedsOnlyThreeFlags() {
        assertThat(BehaviourTicketServiceImpl.eligible(TicketType.WEEKLY, 3, 3, null))
                .isTrue();
        assertThat(BehaviourTicketServiceImpl.eligible(TicketType.WEEKLY, 5, 2, null))
                .isFalse();
    }

    @Test
    @DisplayName("OBS-10: phiếu tháng cần ít nhất 3 phiếu tuần")
    void monthlyTicketNeedsThreeWeeklyTickets() {
        assertThat(BehaviourTicketServiceImpl.eligible(TicketType.MONTHLY, null, null, 3))
                .isTrue();
        assertThat(BehaviourTicketServiceImpl.eligible(TicketType.MONTHLY, null, null, 2))
                .isFalse();
    }
}
