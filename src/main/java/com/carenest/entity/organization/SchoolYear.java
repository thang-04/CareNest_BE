package com.carenest.entity.organization;

import com.carenest.utils.enums.SchoolYearStatus;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "school_years")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SchoolYearStatus status;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "activated_by")
    private Long activatedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static SchoolYear plan(String name, LocalDate startDate, LocalDate endDate) {
        SchoolYear schoolYear = new SchoolYear();
        schoolYear.name = name;
        schoolYear.startDate = startDate;
        schoolYear.endDate = endDate;
        schoolYear.status = SchoolYearStatus.PLANNED;
        return schoolYear;
    }

    public void changeInfo(String name, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void activate(Long userId, Instant at) {
        this.status = SchoolYearStatus.ACTIVE;
        this.activatedBy = userId;
        this.activatedAt = at;
    }

    public void close(Long userId, Instant at) {
        this.status = SchoolYearStatus.CLOSED;
        this.closedBy = userId;
        this.closedAt = at;
    }
}
