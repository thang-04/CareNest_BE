package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduCurriculumSubDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduCurriculumSubDomainRepository extends JpaRepository<EduCurriculumSubDomain, Long> {

    List<EduCurriculumSubDomain> findAllByAgeGroupIdOrderBySourceIdAscSortOrderAsc(Long ageGroupId);
}
