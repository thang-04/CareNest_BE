package com.carenest.entity.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một đợt nghỉ của năm học (SCH-05): các ngày từ startDate tới endDate không tính là ngày học. */
@Getter
@Entity
@Table(name = "school_holidays")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 100)
    private String name;

    public static SchoolHoliday of(Long schoolYearId, LocalDate startDate, LocalDate endDate, String name) {
        SchoolHoliday holiday = new SchoolHoliday();
        holiday.schoolYearId = schoolYearId;
        holiday.startDate = startDate;
        holiday.endDate = endDate;
        holiday.name = name;
        return holiday;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
