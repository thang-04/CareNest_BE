package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.TicketType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Predicate;
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

    private static Predicate<LocalDate> schoolDays(LocalDate... holidays) {
        Set<LocalDate> off = Set.of(holidays);
        return d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !off.contains(d);
    }

    @Test
    @DisplayName("OBS-10 (Cần chốt 18/11/2026): phiếu tuần phát từ thứ Sáu; thứ Sáu nghỉ thì ngày học cuối của tuần")
    void weeklyTicketOpensOnFriday() {
        LocalDate monday = LocalDate.of(2027, 1, 11);
        assertThat(BehaviourTicketServiceImpl.issueFrom(TicketType.WEEKLY, monday, schoolDays()))
                .isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(BehaviourTicketServiceImpl.issueFrom(
                        TicketType.WEEKLY, monday, schoolDays(LocalDate.of(2027, 1, 15))))
                .isEqualTo(LocalDate.of(2027, 1, 14));
    }

    @Test
    @DisplayName("OBS-10 (Cần chốt 18/11/2026): phiếu toàn diện phát từ thứ Sáu cuối cùng của tháng")
    void monthlyTicketOpensOnLastFridayOfMonth() {
        // Tháng 3/2027 kết thúc thứ Tư 31/03 ⇒ thứ Sáu cuối là 26/03
        assertThat(BehaviourTicketServiceImpl.issueFrom(TicketType.MONTHLY, LocalDate.of(2027, 3, 1), schoolDays()))
                .isEqualTo(LocalDate.of(2027, 3, 26));
        assertThat(BehaviourTicketServiceImpl.issueFrom(
                        TicketType.MONTHLY, LocalDate.of(2027, 1, 1), schoolDays(LocalDate.of(2027, 1, 29))))
                .isEqualTo(LocalDate.of(2027, 1, 28));
    }

    @Test
    @DisplayName("OBS-10 (Cần chốt 18/11/2026): tuần vắt hai tháng thuộc tháng chứa thứ Sáu")
    void weekBelongsToMonthOfItsFriday() {
        LocalDate march = LocalDate.of(2027, 3, 1);
        LocalDate april = LocalDate.of(2027, 4, 1);
        // Tuần 29/03 – 02/04/2027: thứ Sáu 02/04 ⇒ tháng 4
        assertThat(BehaviourTicketServiceImpl.weekMondayTo(march)).isEqualTo(LocalDate.of(2027, 3, 27));
        assertThat(BehaviourTicketServiceImpl.weekMondayFrom(april)).isEqualTo(LocalDate.of(2027, 3, 28));
        LocalDate monday = LocalDate.of(2027, 3, 29);
        assertThat(monday).isAfter(BehaviourTicketServiceImpl.weekMondayTo(march));
        assertThat(monday)
                .isBetween(
                        BehaviourTicketServiceImpl.weekMondayFrom(april),
                        BehaviourTicketServiceImpl.weekMondayTo(april));
        // Tuần 01/03 – 05/03 thuộc tháng 3; tuần 22/02 – 26/02 thuộc tháng 2
        assertThat(LocalDate.of(2027, 3, 1))
                .isBetween(
                        BehaviourTicketServiceImpl.weekMondayFrom(march),
                        BehaviourTicketServiceImpl.weekMondayTo(march));
        assertThat(LocalDate.of(2027, 2, 22)).isBefore(BehaviourTicketServiceImpl.weekMondayFrom(march));
    }
}
