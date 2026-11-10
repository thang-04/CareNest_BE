package com.carenest.entity.health;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Trẻ chưa có kết quả khi đóng đợt/kỳ ("Vắng"), kèm lớp lúc đóng; mở lại thì xóa. */
@Getter
@Entity
@Table(name = "health_check_absences")
@IdClass(HealthCheckAbsence.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthCheckAbsence {

    @Id
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Id
    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    public static HealthCheckAbsence of(Long campaignId, Long childId, Long classroomId) {
        HealthCheckAbsence absence = new HealthCheckAbsence();
        absence.campaignId = campaignId;
        absence.childId = childId;
        absence.classroomId = classroomId;
        return absence;
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long campaignId;
        private Long childId;
    }
}
