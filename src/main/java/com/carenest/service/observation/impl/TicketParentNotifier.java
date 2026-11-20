package com.carenest.service.observation.impl;

import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.TicketType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Cần chốt 17/11/2026: phát phiếu bé ngoan (tuần / tháng) ⇒ báo phụ huynh đã liên kết của từng trẻ. */
@Component
@RequiredArgsConstructor
class TicketParentNotifier {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final GuardianChildLinkService guardianChildLinkService;
    private final NotificationService notificationService;

    /** {@code names}: childId → họ tên trẻ được phát phiếu. */
    void ticketsIssued(Map<Long, String> names, TicketType type, LocalDate start, LocalDate end) {
        if (names.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> parentsByChild = guardianChildLinkService.findLinksOfChildren(names.keySet()).stream()
                .filter(l -> l.userId() != null)
                .collect(Collectors.groupingBy(
                        GuardianLinkResponse::childId,
                        Collectors.mapping(GuardianLinkResponse::userId, Collectors.toList())));
        String title = type == TicketType.WEEKLY ? "Phiếu bé ngoan" : "Phiếu bé ngoan toàn diện";
        String label = type == TicketType.WEEKLY ? "phiếu Bé ngoan" : "phiếu Bé ngoan toàn diện";
        String period = type == TicketType.WEEKLY
                ? "tuần " + DAY.format(start) + " – " + DAY.format(end)
                : "tháng " + MONTH.format(start);
        names.forEach((childId, name) -> {
            List<Long> parents = parentsByChild.getOrDefault(childId, List.of());
            if (parents.isEmpty()) {
                return;
            }
            String body = "Bé " + name + " được nhận " + label + " " + period + ". Mở mục Đánh giá để xem.";
            // Dùng loại "Đánh giá mới của bé" sẵn có: phiếu bé ngoan thuộc đánh giá trẻ, App mở đúng mục
            notificationService.notifyUsers(parents, NotificationType.CHILD_EVALUATION_CONFIRMED, title, body, childId);
        });
    }
}
