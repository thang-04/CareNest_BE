package com.carenest.entity.observation;

import com.carenest.utils.enums.TicketType;
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

/** Phiếu bé ngoan đã phát cho một trẻ trong một kỳ (không sửa, không thu hồi). */
@Getter
@Entity
@Table(name = "behaviour_tickets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BehaviourTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketType type;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "present_days")
    private Integer presentDays;

    @Column(name = "flag_count")
    private Integer flagCount;

    @Column(name = "weekly_tickets")
    private Integer weeklyTickets;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @SuppressWarnings("java:S107")
    public static BehaviourTicket issue(
            Long childId,
            Long classroomId,
            Long campusId,
            Long schoolYearId,
            TicketType type,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer presentDays,
            Integer flagCount,
            Integer weeklyTickets,
            Long issuedBy,
            Instant issuedAt) {
        BehaviourTicket t = new BehaviourTicket();
        t.childId = childId;
        t.classroomId = classroomId;
        t.campusId = campusId;
        t.schoolYearId = schoolYearId;
        t.type = type;
        t.periodStart = periodStart;
        t.periodEnd = periodEnd;
        t.presentDays = presentDays;
        t.flagCount = flagCount;
        t.weeklyTickets = weeklyTickets;
        t.issuedBy = issuedBy;
        t.issuedAt = issuedAt;
        return t;
    }
}
