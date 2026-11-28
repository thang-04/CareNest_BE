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
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final EnrollmentRepository enrollmentRepository;
    private final ChildPlacementRepository placementRepository;
    private final StaffAccessPolicy staffAccessPolicy;
    private final StaffAssignmentService staffAssignmentService;
    private final NotificationService notificationService;

    /**
     * Câu lỗi ô {@code classId} khi lớp không nhận được trẻ, null khi được: lớp đang hoạt động của năm học đang hoạt động
     * (CHD-10), thuộc điểm trường người làm được giao (AUTH-02), chưa đủ sĩ số từ ngày vào lớp trở đi (G94-180/CHD-14:
     * gồm trẻ đã hẹn vào lớp sau ngày đó). Không xét nhóm tuổi: nhóm tuổi chỉ gợi ý, lớp khác nhóm tuổi của trẻ vẫn nhận
     * (G94-180/CHD-13).
     */
    String classProblem(CurrentUser actor, ClassroomInfoResponse classroom, LocalDate from) {
        return classProblem(actor, classroom, from, null);
    }

    String classProblem(CurrentUser actor, ClassroomInfoResponse classroom, LocalDate from, Long childId) {
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
        // Trẻ đang xếp không tính (đổi ngày / lớp của lịch cũ); -1 = không trừ ai
        long enrolled = enrollmentRepository.countOccupyingFrom(classroom.id(), from, childId == null ? -1L : childId);
        if (classroom.capacity() != null && enrolled >= classroom.capacity()) {
            return classroom.name() + " đã đủ " + classroom.capacity() + " trẻ. Chọn lớp khác.";
        }
        return null;
    }

    /** Lớp mới tính từ ngày {@code from} (G94-180/CHD-14: có thể ở tương lai; người xếp ghi ở enrollment). */
    void startEnrollment(Long childId, ClassroomInfoResponse to, Long actorId, LocalDate from) {
        enrollmentRepository.save(Enrollment.start(childId, to.id(), from, actorId));
    }

    /**
     * Ghi lịch sử xếp lớp ({@code fromClassroomId} null = tiếp nhận / xếp lần đầu) kèm ngày vào lớp mới và báo GV lớp mới
     * {@code CHILD_PLACED}: "Lớp có trẻ mới" — "{tên trẻ} đã được xếp vào {lớp}." (câu như BE giả của FE); vào lớp ở
     * tương lai thì thêm "từ ngày" (G94-180/CHD-14). GV nhận là GV đang phân công hôm nay.
     */
    void recordPlacement(
            Long childId,
            String childName,
            Long fromClassroomId,
            ClassroomInfoResponse to,
            Long actorId,
            String reason,
            Instant at,
            LocalDate date) {
        // Kích hoạt năm học (bảng xếp lớp năm mới): lớp tính từ đúng ngày đó, GV nhận theo ngày đó như trước
        recordPlacement(childId, childName, fromClassroomId, to, actorId, reason, at, date, date);
    }

    void recordPlacement(
            Long childId,
            String childName,
            Long fromClassroomId,
            ClassroomInfoResponse to,
            Long actorId,
            String reason,
            Instant at,
            LocalDate effectiveDate,
            LocalDate today) {
        placementRepository.save(
                ChildPlacement.record(childId, fromClassroomId, to.id(), actorId, reason, at, effectiveDate));
        String from = DAY.format(effectiveDate);
        if (fromClassroomId != null) {
            notifyTeachers(
                    fromClassroomId,
                    today,
                    "Trẻ chuyển lớp",
                    childName + " chuyển sang " + to.name() + " từ ngày " + from + ".",
                    childId);
        }
        notifyTeachers(
                to.id(),
                today,
                "Lớp có trẻ mới",
                fromClassroomId == null
                        ? childName + " đã được xếp vào " + to.name()
                                + (effectiveDate.isAfter(today) ? " từ ngày " + from : "") + "."
                        : childName + " chuyển vào lớp từ ngày " + from + ".",
                childId);
    }

    /**
     * Bỏ lớp chưa bắt đầu của trẻ (G94-180/CHD-14: lịch chuyển bị thay / bị hủy, cho nghỉ học trước ngày vào lớp): xóa
     * enrollment, đánh dấu dòng lịch sử đã hủy; {@code className} khác null thì báo GV lớp đó.
     */
    void cancelUpcoming(
            Long childId,
            String childName,
            Enrollment upcoming,
            String className,
            Long actorId,
            Instant at,
            LocalDate today) {
        placementRepository
                .findFirstByChildIdAndToClassroomIdAndEffectiveFromAndCancelledAtIsNullOrderByIdDesc(
                        childId, upcoming.getClassroomId(), upcoming.getValidFrom())
                .ifPresent(placement -> {
                    placement.cancel(actorId, at);
                    placementRepository.save(placement);
                });
        enrollmentRepository.delete(upcoming);
        enrollmentRepository.flush();
        if (className != null) {
            notifyTeachers(
                    upcoming.getClassroomId(),
                    today,
                    "Hủy lịch vào lớp",
                    childName + " không vào " + className + " từ ngày " + DAY.format(upcoming.getValidFrom()) + " nữa.",
                    childId);
        }
    }

    void notifyTeachers(Long classroomId, LocalDate today, String title, String body, Long childId) {
        Set<Long> teacherIds = new HashSet<>(staffAssignmentService
                .findCurrentTeacherIds(List.of(classroomId), today)
                .getOrDefault(classroomId, List.of()));
        notificationService.notifyUsers(teacherIds, NotificationType.CHILD_PLACED, title, body, childId);
    }
}
