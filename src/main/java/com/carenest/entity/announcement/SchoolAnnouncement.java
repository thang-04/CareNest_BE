package com.carenest.entity.announcement;

import com.carenest.utils.enums.AnnouncementScopeType;
import com.carenest.utils.enums.AnnouncementStatus;
import com.carenest.utils.enums.RecipientRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Thông báo của trường do BGH soạn (PAR-05): phạm vi × vai trò người nhận, gửi ngay hoặc hẹn giờ. */
@Getter
@Entity
@Table(name = "school_announcements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 10)
    private AnnouncementScopeType scopeType;

    @Column(name = "scope_campus_id")
    private Long scopeCampusId;

    @Column(name = "scope_age_group_id")
    private Long scopeAgeGroupId;

    @Column(name = "scope_classroom_id")
    private Long scopeClassroomId;

    // Danh sách RecipientRole ngăn bởi dấu phẩy
    @Column(nullable = false, length = 60)
    private String roles;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AnnouncementStatus status;

    @Column(name = "send_at", nullable = false)
    private Instant sendAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "schedule_id", updatable = false)
    private Long scheduleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static SchoolAnnouncement create(
            Long senderId,
            String title,
            String body,
            Target target,
            Instant sendAt,
            boolean now,
            Long scheduleId,
            Instant at) {
        SchoolAnnouncement a = new SchoolAnnouncement();
        a.senderId = senderId;
        a.scheduleId = scheduleId;
        a.createdAt = at;
        a.edit(title, body, target, sendAt, at);
        a.status = now ? AnnouncementStatus.SENDING : AnnouncementStatus.SCHEDULED;
        return a;
    }

    /** Sửa khi còn hẹn giờ (clarify Q2); nơi gọi đã kiểm trạng thái. */
    public void edit(String title, String body, Target target, Instant sendAt, Instant at) {
        this.title = title;
        this.body = body;
        this.scopeType = target.scopeType();
        this.scopeCampusId = target.campusId();
        this.scopeAgeGroupId = target.ageGroupId();
        this.scopeClassroomId = target.classroomId();
        this.roles = target.roles().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        this.sendAt = sendAt;
        this.updatedAt = at;
    }

    public Target target() {
        Set<RecipientRole> parsed = EnumSet.noneOf(RecipientRole.class);
        Arrays.stream(roles.split(",")).map(RecipientRole::valueOf).forEach(parsed::add);
        return new Target(scopeType, scopeCampusId, scopeAgeGroupId, scopeClassroomId, parsed);
    }

    public void startSending(Instant at) {
        status = AnnouncementStatus.SENDING;
        updatedAt = at;
    }

    public void markSent(int recipients, Instant at) {
        status = AnnouncementStatus.SENT;
        recipientCount = recipients;
        sentAt = at;
        updatedAt = at;
    }

    public void cancel(Instant at) {
        status = AnnouncementStatus.CANCELLED;
        updatedAt = at;
    }

    /** Phạm vi × vai trò người nhận (dùng chung cho thông báo và lịch lặp). */
    public record Target(
            AnnouncementScopeType scopeType,
            Long campusId,
            Long ageGroupId,
            Long classroomId,
            Set<RecipientRole> roles) {}
}
