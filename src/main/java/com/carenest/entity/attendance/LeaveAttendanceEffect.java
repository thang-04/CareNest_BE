package com.carenest.entity.attendance;

import com.carenest.entity.organization.MealSessionListConverter;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.MealSession;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ảnh chụp trước/sau của bản ghi bị một đơn nghỉ thay đổi, dùng để hoàn tác an toàn. */
@Getter
@Entity
@Table(name = "leave_attendance_effects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveAttendanceEffect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_request_id", nullable = false)
    private Long leaveRequestId;

    @Column(name = "attendance_record_id", nullable = false)
    private Long attendanceRecordId;

    @Column(name = "record_created", nullable = false)
    private boolean recordCreated;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", length = 20)
    private AttendanceStatus beforeStatus;

    @Convert(converter = MealSessionListConverter.class)
    @Column(name = "before_meals", length = 40)
    private List<MealSession> beforeMeals;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_absent_part", length = 10)
    private DayPart beforeAbsentPart;

    @Column(name = "before_recorded_by")
    private Long beforeRecordedBy;

    @Column(name = "before_recorded_at")
    private Instant beforeRecordedAt;

    @Column(name = "after_recorded_at", nullable = false)
    private Instant afterRecordedAt;

    public static LeaveAttendanceEffect created(Long leaveRequestId, AttendanceRecord record) {
        LeaveAttendanceEffect effect = new LeaveAttendanceEffect();
        effect.leaveRequestId = leaveRequestId;
        effect.attendanceRecordId = record.getId();
        effect.recordCreated = true;
        effect.afterRecordedAt = record.getRecordedAt();
        return effect;
    }

    public static LeaveAttendanceEffect changed(
            Long leaveRequestId,
            AttendanceRecord after,
            AttendanceStatus beforeStatus,
            List<MealSession> beforeMeals,
            DayPart beforeAbsentPart,
            Long beforeRecordedBy,
            Instant beforeRecordedAt) {
        LeaveAttendanceEffect effect = new LeaveAttendanceEffect();
        effect.leaveRequestId = leaveRequestId;
        effect.attendanceRecordId = after.getId();
        effect.recordCreated = false;
        effect.beforeStatus = beforeStatus;
        effect.beforeMeals = List.copyOf(beforeMeals);
        effect.beforeAbsentPart = beforeAbsentPart;
        effect.beforeRecordedBy = beforeRecordedBy;
        effect.beforeRecordedAt = beforeRecordedAt;
        effect.afterRecordedAt = after.getRecordedAt();
        return effect;
    }
}
