package com.carenest.repository.organization;

import com.carenest.entity.organization.Classroom;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    @Query("select c.campusId from Classroom c where c.id = :classroomId")
    Optional<Long> findCampusIdById(@Param("classroomId") Long classroomId);

    @Query("select c.id from Classroom c where c.campusId in :campusIds")
    List<Long> findIdsByCampusIdIn(@Param("campusIds") Collection<Long> campusIds);

    // Lớp của năm học đang diễn ra tại date (lớp gắn năm học — năm sau là lớp khác)
    @Query(value = """
            select c.* from classrooms c join school_years y on y.id = c.school_year_id
            where c.campus_id = :campusId and :date between y.start_date and y.end_date
            order by c.name
            """, nativeQuery = true)
    List<Classroom> findCurrentByCampusId(@Param("campusId") Long campusId, @Param("date") LocalDate date);
}
