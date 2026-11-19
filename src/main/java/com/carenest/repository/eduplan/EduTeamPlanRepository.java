package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduTeamPlan;
import com.carenest.utils.enums.EduPlanStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduTeamPlanRepository extends EduHeaderRepository<EduTeamPlan> {

    // EDU-91: kế hoạch tổ không có kế hoạch năm học nguồn (dữ liệu cũ trước khi tách 2 cấp) không hiện trên web
    @Override
    @Query("""
            select p from EduTeamPlan p
            where (:schoolYearId is null or p.schoolYearId = :schoolYearId)
              and (:ageGroupId is null or p.ageGroupId = :ageGroupId)
              and (p.disabled = false or p.disabled is null)
              and p.legacyTeamWithoutSource = false
              and (p.status in :schoolWideStatuses
                   or p.ageGroupId in :fullAgeGroupIds
                   or (p.status = com.carenest.utils.enums.EduPlanStatus.REVIEW
                       and p.ageGroupId in :reviewAgeGroupIds)
                   or (p.status = com.carenest.utils.enums.EduPlanStatus.APPROVED
                       and p.ageGroupId in :approvedAgeGroupIds))
              and (:keyword is null or lower(p.title) like :keyword or lower(p.code) like :keyword)
            """)
    Page<EduTeamPlan> search(
            @Param("schoolYearId") Long schoolYearId,
            @Param("ageGroupId") Long ageGroupId,
            @Param("schoolWideStatuses") Collection<EduPlanStatus> schoolWideStatuses,
            @Param("fullAgeGroupIds") Collection<Long> fullAgeGroupIds,
            @Param("approvedAgeGroupIds") Collection<Long> approvedAgeGroupIds,
            @Param("reviewAgeGroupIds") Collection<Long> reviewAgeGroupIds,
            @Param("keyword") String keyword,
            Pageable pageable);
}
