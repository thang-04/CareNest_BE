package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduExpectedResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduExpectedResultRepository extends JpaRepository<EduExpectedResult, Long> {

    List<EduExpectedResult> findAllByAgeGroupIdOrderByDomainIdAscSortOrderAsc(Long ageGroupId);
}
