package com.carenest.repository.observation;

import com.carenest.entity.observation.SemesterAssessment;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterAssessmentRepository extends JpaRepository<SemesterAssessment, Long> {
    Optional<SemesterAssessment> findByClassroomIdAndSemester(Long classroomId, int semester);

    boolean existsBySchoolYearIdAndStartBetween(Long schoolYearId, LocalDate start, LocalDate end);
}
