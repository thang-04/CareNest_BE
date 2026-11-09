package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduTeamReviewItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduTeamReviewItemRepository extends JpaRepository<EduTeamReviewItem,Long> {
    List<EduTeamReviewItem> findAllByTeamPlanIdOrderByIdAsc(Long teamPlanId);
    boolean existsByTeamPlanIdAndResolvedFalse(Long teamPlanId);
}
