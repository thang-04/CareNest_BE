package com.carenest.service.child.impl;

import com.carenest.entity.child.Enrollment;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thông báo tiếp xúc người bệnh lao (G94-180/CHD-07; nội dung, người nhận như BE giả của FE — plan tiếp nhận mục 6.2).
 * Câu trung tính, không chẩn đoán. Chạy trong giao dịch của người gọi.
 */
@Component
@RequiredArgsConstructor
class TbContactNotifier {
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EnrollmentRepository enrollmentRepository;
    private final StaffAssignmentService staffAssignmentService;
    private final NotificationService notificationService;
    private final Clock clock;

    /** Khai Có tiếp xúc (tiếp nhận, nhập Excel, sửa khai báo): HT, PHT điểm trường, GV lớp, người lưu. */
    void contactReported(Long childId, String childName, Long campusId, LocalDate contactDate, Long savedBy) {
        Set<Long> recipients = leaders(campusId);
        recipients.addAll(classTeachers(childId));
        recipients.add(savedBy);
        notificationService.notifyUsers(
                recipients,
                NotificationType.CHILD_TB_CONTACT,
                "Trẻ có tiền sử tiếp xúc với người bệnh lao",
                "Phụ huynh khai " + childName + " có tiền sử tiếp xúc với người bệnh lao (gần nhất ngày "
                        + DMY.format(contactDate)
                        + "). Nhà trường trao đổi với phụ huynh về việc đưa trẻ đi khám sàng lọc; bác sĩ xem lại ở"
                        + " đợt khám tới.",
                childId);
    }

    /** Ngày tiếp xúc đã có đổi sang ngày khác (sửa khai báo, kết luận phiếu khám): HT, PHT điểm trường, người lưu. */
    void contactDateChanged(
            Long childId, String childName, Long campusId, LocalDate from, LocalDate to, String source, Long savedBy) {
        Set<Long> recipients = leaders(campusId);
        recipients.add(savedBy);
        notificationService.notifyUsers(
                recipients,
                NotificationType.CHILD_TB_CONTACT_DATE_CHANGED,
                "Đổi ngày tiếp xúc với người bệnh lao",
                "Ngày tiếp xúc gần nhất với người bệnh lao của " + childName + " đổi từ " + DMY.format(from) + " sang "
                        + DMY.format(to) + " (" + source + ").",
                childId);
    }

    private Set<Long> leaders(Long campusId) {
        LocalDate today = LocalDate.now(clock);
        Set<Long> ids = new HashSet<>(staffAssignmentService.findPrincipalIds(today));
        if (campusId != null) {
            ids.addAll(staffAssignmentService.findCampusVicePrincipalIds(campusId, today));
        }
        return ids;
    }

    private Set<Long> classTeachers(Long childId) {
        LocalDate today = LocalDate.now(clock);
        List<Long> classroomIds = enrollmentRepository.findActiveByChildIdIn(List.of(childId), today).stream()
                .map(Enrollment::getClassroomId)
                .toList();
        Set<Long> ids = new HashSet<>();
        staffAssignmentService
                .findCurrentTeacherIds(classroomIds, today)
                .values()
                .forEach(ids::addAll);
        return ids;
    }
}
