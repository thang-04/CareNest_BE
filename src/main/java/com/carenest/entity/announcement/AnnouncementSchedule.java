package com.carenest.entity.announcement;

import com.carenest.entity.announcement.SchoolAnnouncement.Target;
import com.carenest.utils.enums.AnnouncementScopeType;
import com.carenest.utils.enums.RecipientRole;
import com.carenest.utils.enums.ScheduleFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Lịch gửi lặp một mẫu soạn sẵn theo tuần/tháng (PAR-10), chỉ HT quản lý. */
@Getter
@Entity
@Table(name = "announcement_schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id")
    private Long templateId;

    @Column(length = 150)
    private String title;

    @Column(length = 4000)
    private String body;

    public void content(String title, String body) {
        this.title = title;
        this.body = body;
    }

    public void activate(boolean active, Instant next, Instant at) {
        this.active = active;
        this.nextRunAt = next;
        this.updatedAt = at;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 10)
    private AnnouncementScopeType scopeType;

    @Column(name = "scope_campus_id")
    private Long scopeCampusId;

    @Column(name = "scope_age_group_id")
    private Long scopeAgeGroupId;

    @Column(name = "scope_classroom_id")
    private Long scopeClassroomId;

    @Column(nullable = false, length = 60)
    private String roles;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScheduleFrequency frequency;

    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Column(name = "day_of_month")
    private Short dayOfMonth;

    @Column(name = "send_time", nullable = false)
    private LocalTime sendTime;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AnnouncementSchedule create(Long createdBy, Instant at) {
        AnnouncementSchedule s = new AnnouncementSchedule();
        s.createdBy = createdBy;
        s.createdAt = at;
        return s;
    }

    public void edit(
            Long templateId,
            Target target,
            ScheduleFrequency frequency,
            Integer dayOfWeek,
            Integer dayOfMonth,
            LocalTime sendTime,
            boolean active,
            Instant nextRunAt,
            Instant at) {
        this.templateId = templateId;
        this.scopeType = target.scopeType();
        this.scopeCampusId = target.campusId();
        this.scopeAgeGroupId = target.ageGroupId();
        this.scopeClassroomId = target.classroomId();
        this.roles = target.roles().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        this.frequency = frequency;
        this.dayOfWeek = dayOfWeek == null ? null : dayOfWeek.shortValue();
        this.dayOfMonth = dayOfMonth == null ? null : dayOfMonth.shortValue();
        this.sendTime = sendTime;
        this.active = active;
        this.nextRunAt = nextRunAt;
        this.updatedAt = at;
    }

    public Target target() {
        Set<RecipientRole> parsed = EnumSet.noneOf(RecipientRole.class);
        Arrays.stream(roles.split(",")).map(RecipientRole::valueOf).forEach(parsed::add);
        return new Target(scopeType, scopeCampusId, scopeAgeGroupId, scopeClassroomId, parsed);
    }

    public void advanceTo(Instant next, Instant at) {
        this.nextRunAt = next;
        this.updatedAt = at;
    }
}
