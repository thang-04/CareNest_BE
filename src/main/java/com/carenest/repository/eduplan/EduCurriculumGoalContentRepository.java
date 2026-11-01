package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduCurriculumGoalContent;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduCurriculumGoalContentRepository extends JpaRepository<EduCurriculumGoalContent, Long> {

    List<EduCurriculumGoalContent> findAllByCurriculumGoalIdInOrderByCurriculumGoalIdAscSortOrderAsc(
            Collection<Long> curriculumGoalIds);
}
