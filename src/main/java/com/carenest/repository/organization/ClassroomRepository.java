package com.carenest.repository.organization;

import com.carenest.entity.organization.Classroom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    @Query("select c.campusId from Classroom c where c.id = :classroomId")
    Optional<Long> findCampusIdById(@Param("classroomId") Long classroomId);
}
