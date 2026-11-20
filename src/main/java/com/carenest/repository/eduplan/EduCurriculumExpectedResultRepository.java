package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduCurriculumExpectedResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduCurriculumExpectedResultRepository extends JpaRepository<EduCurriculumExpectedResult, Long> {

    List<EduCurriculumExpectedResult> findAllByAgeFromMonthsAndAgeToMonthsOrderBySourceIdAscSortOrderAsc(Short from, Short to);
}
