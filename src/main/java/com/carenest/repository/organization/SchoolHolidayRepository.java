package com.carenest.repository.organization;

import com.carenest.entity.organization.SchoolHoliday;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SchoolHolidayRepository extends JpaRepository<SchoolHoliday, Long> {

    List<SchoolHoliday> findAllBySchoolYearIdOrderByStartDateAsc(Long schoolYearId);

    @Modifying
    @Query("DELETE FROM SchoolHoliday h WHERE h.schoolYearId = :schoolYearId")
    void deleteAllBySchoolYearId(Long schoolYearId);
}
