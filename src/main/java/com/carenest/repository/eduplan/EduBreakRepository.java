package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduBreak;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduBreakRepository extends JpaRepository<EduBreak, Long> {

    List<EduBreak> findAllByYearPlanIdOrderBySortOrderAsc(Long yearPlanId);
}
