package com.carenest.service.observation.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.TicketType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TicketParentNotifierTest {
    private final GuardianChildLinkService links = mock(GuardianChildLinkService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final TicketParentNotifier notifier = new TicketParentNotifier(links, notifications);

    @Test
    void weeklyTicketNotifiesLinkedParentsOfEachChild() {
        when(links.findLinksOfChildren(any())).thenReturn(List.of(link(5L, 1L), link(6L, 1L), link(7L, 2L)));
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(1L, "An");
        names.put(2L, "Bình");

        notifier.ticketsIssued(names, TicketType.WEEKLY, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20));

        verify(notifications)
                .notifyUsers(
                        List.of(5L, 6L),
                        NotificationType.CHILD_EVALUATION_CONFIRMED,
                        "Phiếu bé ngoan",
                        "Bé An được nhận phiếu Bé ngoan tuần 16/11 – 20/11. Mở mục Đánh giá để xem.",
                        1L);
        verify(notifications)
                .notifyUsers(
                        List.of(7L),
                        NotificationType.CHILD_EVALUATION_CONFIRMED,
                        "Phiếu bé ngoan",
                        "Bé Bình được nhận phiếu Bé ngoan tuần 16/11 – 20/11. Mở mục Đánh giá để xem.",
                        2L);
    }

    @Test
    void monthlyTicketUsesMonthAndSkipsChildWithoutParentAccount() {
        when(links.findLinksOfChildren(any())).thenReturn(List.of(link(5L, 1L), link(null, 2L)));
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(1L, "An");
        names.put(2L, "Bình");

        notifier.ticketsIssued(names, TicketType.MONTHLY, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        verify(notifications)
                .notifyUsers(
                        List.of(5L),
                        NotificationType.CHILD_EVALUATION_CONFIRMED,
                        "Phiếu bé ngoan toàn diện",
                        "Bé An được nhận phiếu Bé ngoan toàn diện tháng 11/2026. Mở mục Đánh giá để xem.",
                        1L);
        verify(notifications, never()).notifyUsers(any(), any(), anyString(), anyString(), eq(2L));
    }

    @Test
    void noChildrenSendsNothing() {
        notifier.ticketsIssued(Map.of(), TicketType.WEEKLY, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20));

        verify(links, never()).findLinksOfChildren(any());
        verify(notifications, never()).notifyUsers(any(), any(), anyString(), anyString(), anyLong());
    }

    private static GuardianLinkResponse link(Long userId, Long childId) {
        return new GuardianLinkResponse(userId, childId, GuardianRelationship.MOTHER, Instant.EPOCH);
    }
}
