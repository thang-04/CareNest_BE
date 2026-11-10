package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduPlanGoal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduPlanGoalRepository extends JpaRepository<EduPlanGoal, Long> {

    @Query("""
            select (count(t) > 0) from EduContentThemeTick t, EduGoalContent c, EduPlanGoal g
            where t.contentId=c.id and c.goalRowId=g.id and g.id=:goalId
            and g.yearPlanId=:yearPlanId and t.themeId=:themeId
            """)
    boolean belongsToTheme(
            @Param("goalId") Long goalId, @Param("yearPlanId") Long yearPlanId, @Param("themeId") Long themeId);

    List<EduPlanGoal> findAllByYearPlanIdOrderBySortOrderAsc(Long yearPlanId);

    /** Số MT theo kế hoạch, cho cột "N mục tiêu" của danh sách. */
    @Query("""
            select r.yearPlanId, count(r) from EduPlanGoal r
            where r.yearPlanId in :planIds
            group by r.yearPlanId
            """)
    List<Object[]> countGoalsByPlan(@Param("planIds") Collection<Long> planIds);
}
