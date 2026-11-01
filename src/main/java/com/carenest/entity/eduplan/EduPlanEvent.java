package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Lịch sử thao tác; lưu tên, vai trò lúc thao tác để lịch sử không đổi khi tài khoản đổi tên. */
@Getter
@Entity
@Table(name = "edu_plan_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduPlanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_plan_id", nullable = false, updatable = false)
    private Long yearPlanId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "action_label", nullable = false, length = 200)
    private String actionLabel;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;

    @Column(name = "actor_role_label", length = 100)
    private String actorRoleLabel;

    @Column(nullable = false, length = 5)
    private String tone;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static EduPlanEvent of(
            Long yearPlanId,
            String action,
            String actionLabel,
            Long actorId,
            String actorName,
            String actorRoleLabel,
            String tone,
            String note,
            Instant occurredAt) {
        EduPlanEvent event = new EduPlanEvent();
        event.yearPlanId = yearPlanId;
        event.action = action;
        event.actionLabel = actionLabel;
        event.actorId = actorId;
        event.actorName = actorName;
        event.actorRoleLabel = actorRoleLabel;
        event.tone = tone;
        event.note = note;
        event.occurredAt = occurredAt;
        return event;
    }
}
