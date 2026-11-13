package com.carenest.repository.observation;

import com.carenest.entity.observation.WeeklyCommentReopen;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyCommentReopenRepository extends JpaRepository<WeeklyCommentReopen, Long> {
    List<WeeklyCommentReopen> findByWeeklyCommentIdInOrderByReopenedAtDescIdDesc(Collection<Long> commentIds);

    List<WeeklyCommentReopen> findAllByOrderByReopenedAtDescIdDesc();

    boolean existsByWeeklyCommentId(Long commentId);
}
