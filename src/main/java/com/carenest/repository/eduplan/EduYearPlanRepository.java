package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.utils.enums.EduPlanStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Điều kiện phạm vi do service truyền vào từ EduPlanAccessPolicy.ViewScope. */
public interface EduYearPlanRepository extends JpaRepository<EduYearPlan, Long> {

    boolean existsBySchoolYearIdAndAgeGroupId(Long schoolYearId, Long ageGroupId);

    boolean existsByCode(String code);

    // EDU-06: toàn trường theo trạng thái được xem, hoặc nhóm tuổi Tổ trưởng phụ trách, hoặc bản đã ban hành của GV
    @Query("""
            select p from EduYearPlan p
            where (:schoolYearId is null or p.schoolYearId = :schoolYearId)
              and (:ageGroupId is null or p.ageGroupId = :ageGroupId)
              and (p.status in :schoolWideStatuses
                   or p.ageGroupId in :fullAgeGroupIds
                   or (p.status = com.carenest.utils.enums.EduPlanStatus.APPROVED
                       and p.ageGroupId in :approvedAgeGroupIds))
              and (:keyword is null or lower(p.title) like :keyword or lower(p.code) like :keyword)
            """)
    Page<EduYearPlan> search(
            @Param("schoolYearId") Long schoolYearId,
            @Param("ageGroupId") Long ageGroupId,
            @Param("schoolWideStatuses") Collection<EduPlanStatus> schoolWideStatuses,
            @Param("fullAgeGroupIds") Collection<Long> fullAgeGroupIds,
            @Param("approvedAgeGroupIds") Collection<Long> approvedAgeGroupIds,
            @Param("keyword") String keyword,
            Pageable pageable);
}
