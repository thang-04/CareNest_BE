package com.carenest.entity.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/** Cơ sở công tác của giáo viên (AUTH-15): có ngay khi tạo, phân công lớp ở cơ sở khác thì chuyển theo lớp. */
@Getter
@Entity
@Table(name = "teacher_work_campuses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeacherWorkCampus {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TeacherWorkCampus of(Long userId, Long campusId, Long updatedBy) {
        TeacherWorkCampus workCampus = new TeacherWorkCampus();
        workCampus.userId = userId;
        workCampus.campusId = campusId;
        workCampus.updatedBy = updatedBy;
        return workCampus;
    }

    public void moveTo(Long campusId, Long updatedBy) {
        this.campusId = campusId;
        this.updatedBy = updatedBy;
    }
}
