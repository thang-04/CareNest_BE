package com.carenest.entity.attendance;

import com.carenest.entity.organization.MealSessionListConverter;
import com.carenest.utils.enums.AttendanceStatus;
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

/** Một lần ghi/sửa bản ghi điểm danh; chỉ thêm, không sửa. */
@Getter
@Entity
@Table(name = "attendance_record_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRecordHistory {

    public static final String RECORDED = "RECORDED";
    public static final String UPDATED = "UPDATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(nullable = false, length = 20)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Convert(converter = MealSessionListConverter.class)
    @Column(nullable = false, length = 40)
    private List<MealSession> meals;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "unlock_id")
    private Long unlockId;

    public static AttendanceRecordHistory of(AttendanceRecord record, String action, Long unlockId) {
        AttendanceRecordHistory history = new AttendanceRecordHistory();
        history.recordId = record.getId();
        history.action = action;
        history.status = record.getStatus();
        history.meals = record.getMeals();
        history.userId = record.getRecordedBy();
        history.at = record.getRecordedAt();
        history.unlockId = unlockId;
        return history;
    }
}
