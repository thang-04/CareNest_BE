package com.carenest.repository.observation;

import com.carenest.entity.observation.WeeklyComment;
import com.carenest.utils.enums.WeeklyCommentStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyCommentRepository extends JpaRepository<WeeklyComment, Long> {

    List<WeeklyComment> findByClassroomIdAndChildIdInAndWeekStart(
            Long classroomId, Collection<Long> childIds, LocalDate weekStart);

    List<WeeklyComment> findByClassroomIdInAndWeekStart(Collection<Long> classroomIds, LocalDate weekStart);

    Optional<WeeklyComment> findByClassroomIdAndChildIdAndWeekStart(
            Long classroomId, Long childId, LocalDate weekStart);

    List<WeeklyComment> findByChildIdAndStatusOrderByWeekStartDesc(Long childId, WeeklyCommentStatus status);

    List<WeeklyComment> findByChildIdAndStatusAndWeekStartBetweenOrderByWeekStartAsc(
            Long childId, WeeklyCommentStatus status, LocalDate from, LocalDate to);
}
