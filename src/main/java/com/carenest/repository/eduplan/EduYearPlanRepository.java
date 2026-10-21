package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.utils.enums.EduPlanStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Điều kiện phạm vi (nhóm tuổi, trạng thái được xem) do service truyền vào (EDU-06). */
public interface EduYearPlanRepository extends JpaRepository<EduYearPlan, Long> {

    boolean existsBySchoolYearIdAndAgeGroupId(Long schoolYearId, Long ageGroupId);

    boolean existsByCode(String code);

    @Query("""
            select p from EduYearPlan p
            where (:schoolYearId is null or p.schoolYearId = :schoolYearId)
              and (:ageGroupId is null or p.ageGroupId = :ageGroupId)
              and (:allAgeGroups = true or p.ageGroupId in :ageGroupIds)
              and p.status in :statuses
              and (:q is null or lower(p.title) like :q or lower(p.code) like :q)
            """)
    Page<EduYearPlan> search(
            @Param("schoolYearId") Long schoolYearId,
            @Param("ageGroupId") Long ageGroupId,
            @Param("allAgeGroups") boolean allAgeGroups,
            @Param("ageGroupIds") Collection<Long> ageGroupIds,
            @Param("statuses") Collection<EduPlanStatus> statuses,
            @Param("q") String q,
            Pageable pageable);
}
