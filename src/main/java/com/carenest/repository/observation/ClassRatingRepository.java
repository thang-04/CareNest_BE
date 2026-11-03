package com.carenest.repository.observation;

import com.carenest.entity.observation.ClassRating;
import com.carenest.utils.enums.ClassRatingPeriod;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRatingRepository extends JpaRepository<ClassRating, Long> {

    List<ClassRating> findByClassroomIdInAndPeriod(Collection<Long> classroomIds, ClassRatingPeriod period);
}
