package com.carenest.repository.organization;

import com.carenest.entity.organization.TeacherWorkCampus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherWorkCampusRepository extends JpaRepository<TeacherWorkCampus, Long> {

    List<TeacherWorkCampus> findByUserIdIn(Collection<Long> userIds);

    List<TeacherWorkCampus> findByCampusIdIn(Collection<Long> campusIds);
}
