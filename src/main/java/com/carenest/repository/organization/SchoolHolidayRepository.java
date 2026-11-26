package com.carenest.repository.organization;

import com.carenest.entity.organization.SchoolHoliday;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SchoolHolidayRepository extends JpaRepository<SchoolHoliday, Long> {

    boolean existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate start, LocalDate end);

    Optional<SchoolHoliday> findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate start, LocalDate end);

    List<SchoolHoliday> findAllBySchoolYearIdOrderByStartDateAsc(Long schoolYearId);

    @Modifying
    @Query("DELETE FROM SchoolHoliday h WHERE h.schoolYearId = :schoolYearId")
    void deleteAllBySchoolYearId(Long schoolYearId);
}
