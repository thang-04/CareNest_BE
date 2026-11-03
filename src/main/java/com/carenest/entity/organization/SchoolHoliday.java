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

/** Một ngày nghỉ của năm học (SCH-05): không tính là ngày học dù là thứ 2–6. */
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

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 100)
    private String name;

    public static SchoolHoliday of(Long schoolYearId, LocalDate date, String name) {
        SchoolHoliday holiday = new SchoolHoliday();
        holiday.schoolYearId = schoolYearId;
        holiday.date = date;
        holiday.name = name;
        return holiday;
    }
}
