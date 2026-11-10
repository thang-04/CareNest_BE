package com.carenest.repository.observation;

import com.carenest.entity.observation.AssessmentDayUnlock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentDayUnlockRepository extends JpaRepository<AssessmentDayUnlock, Long> {

    Optional<AssessmentDayUnlock> findFirstByClassroomIdAndDateAndValidUntilAfterOrderByIdDesc(
            Long classroomId, LocalDate date, Instant now);

    List<AssessmentDayUnlock> findByClassroomIdInAndDateBetweenAndValidUntilAfter(
            Collection<Long> classroomIds, LocalDate from, LocalDate to, Instant now);

    List<AssessmentDayUnlock> findAllByOrderByUnlockedAtDescIdDesc();
}
