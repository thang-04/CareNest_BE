package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduTeamPlan;
import com.carenest.utils.enums.EduPlanStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduTeamPlanRepository extends EduHeaderRepository<EduTeamPlan> {

    @Query(
            "select p from EduTeamPlan p where p.status = com.carenest.utils.enums.EduPlanStatus.APPROVED and p.disabled = false order by p.schoolYearId desc, p.versionNo desc, p.id desc")
    List<EduTeamPlan> findApprovedForExamples();

    // EDU-91: kế hoạch tổ không có kế hoạch năm học nguồn (dữ liệu cũ trước khi tách 2 cấp) không hiện trên web
    @Override
    @Query("""
            select p from EduTeamPlan p
            where (:schoolYearId is null or p.schoolYearId = :schoolYearId)
              and (:ageGroupId is null or p.ageGroupId in
                   (select g.id from AgeGroup g, AgeGroup chosen where chosen.id = :ageGroupId
                    and g.ageFromMonths = chosen.ageFromMonths and g.ageToMonths = chosen.ageToMonths))
              and (p.disabled = false or p.disabled is null)
              and p.legacyTeamWithoutSource = false
              and (p.status in :schoolWideStatuses
                   or (p.ageGroupId in :fullAgeGroupIds and (:fullOwnerId is null or p.ownerId = :fullOwnerId))
                   or (p.status = com.carenest.utils.enums.EduPlanStatus.REVIEW
                       and p.ageGroupId in :reviewAgeGroupIds)
                   or (p.status = com.carenest.utils.enums.EduPlanStatus.APPROVED
                       and p.ageGroupId in :approvedAgeGroupIds))
              and (:keyword is null or lower(p.title) like :keyword or lower(p.code) like :keyword)
              and (:currentOnly = false or p.status <> com.carenest.utils.enums.EduPlanStatus.APPROVED
                   or not exists (select q.id from EduTeamPlan q
                       where q.status = com.carenest.utils.enums.EduPlanStatus.APPROVED
                         and q.schoolYearId = p.schoolYearId and q.ageGroupId = p.ageGroupId
                         and q.versionNo > p.versionNo and (q.disabled = false or q.disabled is null)))
            """)
    Page<EduTeamPlan> search(
            @Param("schoolYearId") Long schoolYearId,
            @Param("ageGroupId") Long ageGroupId,
            @Param("schoolWideStatuses") Collection<EduPlanStatus> schoolWideStatuses,
            @Param("fullAgeGroupIds") Collection<Long> fullAgeGroupIds,
            @Param("fullOwnerId") Long fullOwnerId,
            @Param("approvedAgeGroupIds") Collection<Long> approvedAgeGroupIds,
            @Param("reviewAgeGroupIds") Collection<Long> reviewAgeGroupIds,
            @Param("keyword") String keyword,
            @Param("currentOnly") boolean currentOnly,
            Pageable pageable);
}
