package com.carenest.entity.organization;

import com.carenest.utils.enums.StaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "staff_assignments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_role", nullable = false, length = 30)
    private StaffRole staffRole;

    @Column(name = "campus_id")
    private Long campusId;

    @Column(name = "classroom_id")
    private Long classroomId;

    @Column(name = "age_group_id")
    private Long ageGroupId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_homeroom", nullable = false)
    private boolean homeroom;

    @Column(name = "school_year_id")
    private Long schoolYearId;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static StaffAssignment teacher(
            Long userId, Long classroomId, boolean homeroom, LocalDate validFrom, LocalDate validTo, Long assignedBy) {
        StaffAssignment a = new StaffAssignment();
        a.userId = userId;
        a.staffRole = StaffRole.TEACHER;
        a.classroomId = classroomId;
        a.homeroom = homeroom;
        a.validFrom = validFrom;
        a.validTo = validTo;
        a.assignedBy = assignedBy;
        return a;
    }

    public static StaffAssignment teamLeader(
            Long userId, Long ageGroupId, Long schoolYearId, LocalDate validFrom, LocalDate validTo, Long assignedBy) {
        StaffAssignment a = new StaffAssignment();
        a.userId = userId;
        a.staffRole = StaffRole.AGE_GROUP_TEAM_LEADER;
        a.ageGroupId = ageGroupId;
        a.schoolYearId = schoolYearId;
        a.validFrom = validFrom;
        a.validTo = validTo;
        a.assignedBy = assignedBy;
        return a;
    }

    public void markHomeroom(boolean homeroom) {
        this.homeroom = homeroom;
    }

    /** Kết thúc phân công: còn hiệu lực tới hết hôm trước ngày end (lịch sử ai từng có quyền được giữ). */
    public void endBefore(LocalDate end) {
        this.validTo = end.minusDays(1);
    }

    public static StaffAssignment create(
            Long userId, StaffRole staffRole, Long campusId, Long classroomId, LocalDate validFrom, LocalDate validTo) {
        StaffAssignment assignment = new StaffAssignment();
        assignment.userId = userId;
        assignment.staffRole = staffRole;
        assignment.campusId = campusId;
        assignment.classroomId = classroomId;
        assignment.validFrom = validFrom;
        assignment.validTo = validTo;
        return assignment;
    }

    // Kết thúc bằng valid_to, không xóa dòng: giữ lịch sử ai từng có phạm vi
    public void endOn(LocalDate lastDay) {
        this.validTo = lastDay;
    }
}
