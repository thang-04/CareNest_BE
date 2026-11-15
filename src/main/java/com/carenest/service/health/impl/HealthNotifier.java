package com.carenest.service.health.impl;

import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.NotificationType;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thông báo của module health, chạy trong giao dịch người gọi: phụ huynh khi kết quả công bố / sửa (HLT-11, PAR-03 — câu
 * không ghi số đo, kết luận); GV lớp, PHT điểm trường khi mở / mở lại đợt khám, kỳ cân đo (như BE giả của FE).
 */
@Component
@RequiredArgsConstructor
class HealthNotifier {
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GuardianChildLinkService guardianChildLinkService;
    private final StaffAssignmentService staffAssignmentService;
    private final NotificationService notificationService;
    private final HealthCheckRules rules;

    /** {@code updated}: sửa kết quả đã công bố (HLT-13) thay vì công bố lần đầu. */
    void resultPublished(Long childId, String childName, boolean updated) {
        Set<Long> parents = guardianChildLinkService.findLinksOfChild(childId).stream()
                .map(GuardianLinkResponse::userId)
                .collect(Collectors.toSet());
        notificationService.notifyUsers(
                parents,
                updated ? NotificationType.HEALTH_RESULT_UPDATED : NotificationType.HEALTH_RESULT_PUBLISHED,
                updated ? "Kết quả sức khỏe đã cập nhật" : "Kết quả sức khỏe mới",
                updated
                        ? "Kết quả cân đo, khám sức khỏe của " + childName
                                + " đã được cập nhật. Mở mục Sức khỏe để xem."
                        : "Đã có kết quả cân đo, khám sức khỏe mới của " + childName + ". Mở mục Sức khỏe để xem.",
                childId);
    }

    /**
     * HLT-21: GV duyệt nhận định xu hướng ⇒ mỗi tài khoản phụ huynh liên kết một thông báo, nội dung là tổng quan đã duyệt
     * (≤ 500 ký tự, vừa cột body). Bản lưu trước khi có tổng quan ⇒ câu chung. Push vẫn che nội dung (PushDispatchListener).
     */
    void trendApproved(Long childId, String overview) {
        Set<Long> parents = guardianChildLinkService.findLinksOfChild(childId).stream()
                .map(GuardianLinkResponse::userId)
                .collect(Collectors.toSet());
        notificationService.notifyUsers(
                parents,
                NotificationType.HEALTH_TREND_APPROVED,
                "Nhận định xu hướng sức khỏe mới",
                overview == null || overview.isBlank()
                        ? "Đã có nhận định xu hướng tăng trưởng mới. Mở mục Sức khỏe để xem."
                        : overview,
                childId);
    }

    void campaignOpened(HealthCheckCampaign campaign, boolean reopened) {
        List<Long> classIds = rules.classesOf(campaign).stream()
                .map(ClassroomInfoResponse::id)
                .toList();
        Set<Long> recipients = new HashSet<>();
        staffAssignmentService
                .findCurrentTeacherIds(classIds, rules.today())
                .values()
                .forEach(recipients::addAll);
        recipients.addAll(staffAssignmentService.findCampusVicePrincipalIds(campaign.getCampusId(), rules.today()));
        String label = campaign.getKind() == HealthCheckKind.EXAM ? "Đợt khám sức khỏe" : "Kỳ cân đo";
        notificationService.notifyUsers(
                recipients,
                NotificationType.HEALTH_CHECK_OPENED,
                reopened
                        ? label + " được mở lại"
                        : (campaign.getKind() == HealthCheckKind.EXAM ? "Đợt khám sức khỏe mới" : "Kỳ cân đo mới"),
                label + " \"" + campaign.getName() + "\" " + (reopened ? "được mở lại" : "đã mở") + ", từ ngày "
                        + DMY.format(campaign.getStartDate()) + " đến ngày " + DMY.format(campaign.getEndDate()) + ".",
                null);
    }
}
