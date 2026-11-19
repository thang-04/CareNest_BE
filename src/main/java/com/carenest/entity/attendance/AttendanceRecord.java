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
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Điểm danh + bữa ăn của một trẻ trong một ngày (ATT-01b: unique child_id, date). */
@Getter
@Entity
@Table(name = "attendance_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Convert(converter = MealSessionListConverter.class)
    @Column(nullable = false, length = 40)
    private List<MealSession> meals;

    /** ATT-13: buổi vắng có phép theo đơn nghỉ nửa buổi; trạng thái status là của buổi còn lại. */
    @Enumerated(EnumType.STRING)
    @Column(name = "absent_part", length = 10)
    private DayPart absentPart;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AttendanceRecord create(
            Long childId,
            Long classroomId,
            Long campusId,
            LocalDate date,
            AttendanceStatus status,
            List<MealSession> meals,
            Long userId,
            Instant at) {
        AttendanceRecord record = new AttendanceRecord();
        record.childId = childId;
        record.classroomId = classroomId;
        record.campusId = campusId;
        record.date = date;
        record.update(status, meals, userId, at);
        return record;
    }

    public boolean differsFrom(AttendanceStatus newStatus, List<MealSession> newMeals) {
        return status != newStatus || !meals.equals(newMeals);
    }

    public boolean differsFrom(AttendanceStatus newStatus, List<MealSession> newMeals, DayPart newAbsentPart) {
        return differsFrom(newStatus, newMeals) || absentPart != newAbsentPart;
    }

    public void markAbsentPart(DayPart part) {
        this.absentPart = part;
    }

    /** Số ngày có mặt quy đổi (ATT-14): mỗi buổi 0,5. */
    public double presentDays() {
        if (status != AttendanceStatus.PRESENT) {
            return 0;
        }
        return absentPart == null ? 1 : 0.5;
    }

    /** Số ngày vắng có phép quy đổi: buổi nghỉ theo đơn luôn có phép, buổi còn lại theo status. */
    public double excusedDays() {
        double own = status == AttendanceStatus.EXCUSED ? 1 : 0;
        return absentPart == null ? own : 0.5 + own * 0.5;
    }

    public double unexcusedDays() {
        double own = status == AttendanceStatus.UNEXCUSED ? 1 : 0;
        return absentPart == null ? own : own * 0.5;
    }

    public void update(AttendanceStatus status, List<MealSession> meals, Long userId, Instant at) {
        this.status = status;
        this.meals = List.copyOf(meals);
        this.recordedBy = userId;
        this.recordedAt = at;
    }
}
