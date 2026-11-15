package com.carenest.repository.observation;

import com.carenest.entity.observation.WeeklyCommentRevision;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyCommentRevisionRepository extends JpaRepository<WeeklyCommentRevision, Long> {
    boolean existsByWeeklyCommentId(Long commentId);

    List<WeeklyCommentRevision> findByWeeklyCommentIdIn(Collection<Long> commentIds);
}
