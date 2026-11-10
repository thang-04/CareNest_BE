package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduGoalContent;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduGoalContentRepository extends JpaRepository<EduGoalContent, Long> {

    List<EduGoalContent> findAllByGoalRowIdInOrderBySortOrderAsc(Collection<Long> goalRowIds);
}
