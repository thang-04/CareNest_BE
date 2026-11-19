package com.carenest.service.observation.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.enums.NotificationType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** PAR-03: xác nhận đánh giá trẻ / đánh giá chủ đề ⇒ báo phụ huynh của từng trẻ (HT tắt được — PAR-09). */
@Component
@RequiredArgsConstructor
class EvaluationNotifier {

    private final GuardianChildLinkService guardianChildLinkService;
    private final ChildService childService;
    private final NotificationService notificationService;

    void childEvaluationConfirmed(Long childId) {
        notifyEach(List.of(childId), NotificationType.CHILD_EVALUATION_CONFIRMED, Map.of());
    }

    void themeEvaluationConfirmed(Collection<Long> childIds, String className) {
        notifyEach(childIds, NotificationType.THEME_EVALUATION_CONFIRMED, Map.of("lop", className));
    }

    private void notifyEach(Collection<Long> childIds, NotificationType type, Map<String, String> extra) {
        if (childIds.isEmpty()) {
            return;
        }
        Map<Long, ChildBriefResponse> children = childService.findChildBriefs(List.copyOf(childIds));
        Map<Long, List<Long>> parentsByChild = guardianChildLinkService.findLinksOfChildren(childIds).stream()
                .filter(l -> l.userId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        GuardianLinkResponse::childId,
                        java.util.stream.Collectors.mapping(
                                GuardianLinkResponse::userId, java.util.stream.Collectors.toList())));
        for (Long childId : childIds) {
            List<Long> parents = parentsByChild.getOrDefault(childId, List.of());
            if (parents.isEmpty()) {
                continue;
            }
            ChildBriefResponse child = children.get(childId);
            Map<String, String> values = new java.util.HashMap<>(extra);
            values.put("tenTre", child == null ? "Bé" : "Bé " + Objects.toString(child.fullName(), ""));
            notificationService.notifyEvent(parents, type, values, childId);
        }
    }
}
