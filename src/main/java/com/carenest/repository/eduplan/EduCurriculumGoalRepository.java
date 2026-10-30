package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduCurriculumGoal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduCurriculumGoalRepository extends JpaRepository<EduCurriculumGoal, Long> {

    List<EduCurriculumGoal> findAllByAgeGroupIdOrderBySourceIdAscSortOrderAsc(Long ageGroupId);
}
