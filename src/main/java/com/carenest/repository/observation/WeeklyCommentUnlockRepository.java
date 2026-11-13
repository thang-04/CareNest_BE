package com.carenest.repository.observation;

import com.carenest.entity.observation.WeeklyCommentUnlock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyCommentUnlockRepository extends JpaRepository<WeeklyCommentUnlock, Long> {
    Optional<WeeklyCommentUnlock> findFirstByClassroomIdAndWeekStartAndValidUntilAfterOrderByIdDesc(
            Long classroomId, LocalDate weekStart, Instant now);

    List<WeeklyCommentUnlock> findByClassroomIdAndValidUntilAfter(Long classroomId, Instant now);

    List<WeeklyCommentUnlock> findAllByOrderByUnlockedAtDescIdDesc();
}
