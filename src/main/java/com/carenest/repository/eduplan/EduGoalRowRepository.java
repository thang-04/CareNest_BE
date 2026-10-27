package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduGoalRow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduGoalRowRepository extends JpaRepository<EduGoalRow, Long> {

    @Query("""
            select (count(t) > 0) from EduContentThemeTick t, EduGoalContent c, EduGoalRow g
            where t.contentId=c.id and c.goalRowId=g.id and g.id=:goalId
            and g.yearPlanId=:yearPlanId and t.themeId=:themeId
            and g.kind=com.carenest.utils.enums.EduGoalRowKind.GOAL
            """)
    boolean belongsToTheme(
            @Param("goalId") Long goalId, @Param("yearPlanId") Long yearPlanId, @Param("themeId") Long themeId);

    List<EduGoalRow> findAllByYearPlanIdOrderBySortOrderAsc(Long yearPlanId);

    /** Số MT theo kế hoạch, cho cột "N mục tiêu" của danh sách. */
    @Query("""
            select r.yearPlanId, count(r) from EduGoalRow r
            where r.yearPlanId in :planIds and r.kind = com.carenest.utils.enums.EduGoalRowKind.GOAL
            group by r.yearPlanId
            """)
    List<Object[]> countGoalsByPlan(@Param("planIds") Collection<Long> planIds);
}
