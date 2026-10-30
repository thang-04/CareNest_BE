package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduTheme;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduThemeRepository extends JpaRepository<EduTheme, Long> {

    List<EduTheme> findAllByYearPlanIdOrderBySortOrderAsc(Long yearPlanId);

    /** Số chủ đề theo kế hoạch, cho cột "M chủ đề" của danh sách. */
    @Query("select t.yearPlanId, count(t) from EduTheme t where t.yearPlanId in :planIds group by t.yearPlanId")
    List<Object[]> countByPlan(@Param("planIds") Collection<Long> planIds);
}
