package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduCurriculumContent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduCurriculumContentRepository extends JpaRepository<EduCurriculumContent, Long> {

    List<EduCurriculumContent> findAllByAgeGroupIdOrderBySourceIdAscSortOrderAsc(Long ageGroupId);
}
