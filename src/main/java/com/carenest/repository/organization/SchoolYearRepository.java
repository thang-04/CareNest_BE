package com.carenest.repository.organization;

import com.carenest.entity.organization.SchoolYear;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolYearRepository extends JpaRepository<SchoolYear, Long> {

    List<SchoolYear> findAllByOrderByStartDateDesc();

    Optional<SchoolYear> findFirstByStatus(SchoolYearStatus status);

    Optional<SchoolYear> findFirstByStatusOrderByStartDateAsc(SchoolYearStatus status);

    @Query("select y from SchoolYear y where y.startDate <= :date and y.endDate >= :date")
    Optional<SchoolYear> findContaining(@Param("date") LocalDate date);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    // Hai khoảng [start, end] giao nhau khi start1 <= end2 và start2 <= end1; excludeId = null khi tạo mới
    @Query("select y from SchoolYear y where y.startDate <= :endDate and y.endDate >= :startDate"
            + " and (:excludeId is null or y.id <> :excludeId) order by y.startDate")
    List<SchoolYear> findOverlapping(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);
}
