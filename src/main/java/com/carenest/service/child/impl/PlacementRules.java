package com.carenest.service.child.impl;

import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.child.ChildPlacement;
import com.carenest.entity.child.Enrollment;
import com.carenest.repository.child.ChildPlacementRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Luật xếp trẻ vào lớp dùng chung cho tiếp nhận có lớp và xếp / chuyển lớp (CHD-10, G94-180/CHD-02, plan tiếp nhận mục
 * 4.4, 6.2): kiểm lớp nhận được trẻ; ghi lịch sử xếp lớp và báo giáo viên lớp mới. Chạy trong giao dịch người gọi.
 */
@Component
@RequiredArgsConstructor
class PlacementRules {
    private final EnrollmentRepository enrollmentRepository;
    private final ChildPlacementRepository placementRepository;
    private final StaffAccessPolicy staffAccessPolicy;
    private final StaffAssignmentService staffAssignmentService;
    private final NotificationService notificationService;

    /**
     * Câu lỗi ô {@code classId} khi lớp không nhận được trẻ, null khi được: lớp đang hoạt động của năm học đang hoạt động
     * (CHD-10), thuộc điểm trường người làm được giao (AUTH-02), chưa đủ sĩ số tính tới hôm nay.
     */
    String classProblem(CurrentUser actor, ClassroomInfoResponse classroom, LocalDate today) {
        if (classroom == null || classroom.status() != ClassroomStatus.ACTIVE) {
            return "Lớp không tồn tại hoặc đã ngừng hoạt động";
        }
        if (classroom.yearStatus() != SchoolYearStatus.ACTIVE) {
            return "Chỉ xếp vào lớp của năm học đang hoạt động. Xếp lớp cho năm học sau ở màn Xếp lớp năm học mới.";
        }
        if (!staffAccessPolicy.managesWholeSchool(actor)
                && !staffAccessPolicy.managedCampusIds(actor).contains(classroom.campusId())) {
            return "Chỉ xếp được vào lớp thuộc điểm trường bạn được giao";
        }
        long enrolled = enrollmentRepository
                .findActiveByClassroomIds(List.of(classroom.id()), today)
                .size();
        if (classroom.capacity() != null && enrolled >= classroom.capacity()) {
            return classroom.name() + " đã đủ " + classroom.capacity() + " trẻ. Chọn lớp khác.";
        }
        return null;
    }

    /** Lớp mới tính từ hôm nay (người xếp ghi ở enrollment). */
    void startEnrollment(Long childId, ClassroomInfoResponse to, Long actorId, LocalDate today) {
        enrollmentRepository.save(Enrollment.start(childId, to.id(), today, actorId));
    }

    /**
     * Ghi lịch sử xếp lớp ({@code fromClassroomId} null = tiếp nhận / xếp lần đầu) và báo GV lớp mới {@code CHILD_PLACED}:
     * "Lớp có trẻ mới" — "{tên trẻ} đã được xếp vào {lớp}." (câu như BE giả của FE).
     */
    void recordPlacement(
            Long childId,
            String childName,
            Long fromClassroomId,
            ClassroomInfoResponse to,
            Long actorId,
            String reason,
            Instant at,
            LocalDate today) {
        placementRepository.save(ChildPlacement.record(childId, fromClassroomId, to.id(), actorId, reason, at));
        Set<Long> teacherIds = new HashSet<>(staffAssignmentService
                .findCurrentTeacherIds(List.of(to.id()), today)
                .getOrDefault(to.id(), List.of()));
        notificationService.notifyUsers(
                teacherIds,
                NotificationType.CHILD_PLACED,
                "Lớp có trẻ mới",
                childName + " đã được xếp vào " + to.name() + ".",
                childId);
    }
}
