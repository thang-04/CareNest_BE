package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
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

    @org.hibernate.annotations.Formula("coalesce(school_year_plan_id, team_plan_id)")
    private Long yearPlanId;

    @Column(name = "school_year_plan_id", updatable = false)
    private Long schoolYearPlanId;

    @Column(name = "team_plan_id", updatable = false)
    private Long teamPlanId;

    public void initializeOwner(EduPlan plan) {
        if (yearPlanId != null && !yearPlanId.equals(plan.getId()))
            throw new IllegalArgumentException("Plan owner differs");
        yearPlanId = plan.getId();
        schoolYearPlanId =
                plan.getPlanLevel() == com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR ? plan.getId() : null;
        teamPlanId = plan.getPlanLevel() == com.carenest.utils.enums.EduPlanLevel.TEAM ? plan.getId() : null;
    }

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

    /** Spec 004: hạn mới khi Hiệu trưởng mở khóa (action = unlock). */
    @Column(name = "deadline")
    private LocalDate deadline;

    public EduPlanEvent withDeadline(LocalDate value) {
        deadline = value;
        return this;
    }

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
