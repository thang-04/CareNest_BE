package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.utils.enums.EduPlanStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Điều kiện phạm vi do service truyền vào từ EduPlanAccessPolicy.ViewScope. */
public interface EduYearPlanRepository extends JpaRepository<EduYearPlan, Long> {

    boolean existsByPlanLevelAndSchoolYearIdAndAgeGroupId(
            com.carenest.utils.enums.EduPlanLevel level, Long yearId, Long ageId);

    List<EduYearPlan> findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
            com.carenest.utils.enums.EduPlanLevel level, Long yearId, Long ageId);

    boolean existsBySchoolYearIdAndAgeGroupId(Long schoolYearId, Long ageGroupId);

    Optional<EduYearPlan> findByOwnerIdAndCreatedRequestId(Long ownerId, UUID createdRequestId);

    boolean existsByCode(String code);

    // Spec 004: chuỗi phiên bản của một (năm học, nhóm tuổi)
    default List<EduYearPlan> findAllBySchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(Long yearId, Long ageId) {
        return findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                com.carenest.utils.enums.EduPlanLevel.TEAM, yearId, ageId);
    }

    List<EduYearPlan> findAllBySchoolYearIdIn(Collection<Long> schoolYearIds);

    // Mọi thao tác ghi khóa dòng: hai người cùng bấm sửa thì người sau thấy khóa của người trước (EDU-10)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from EduYearPlan p where p.id = :id")
    Optional<EduYearPlan> findForUpdateById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from EduYearPlan p where p.id in :ids order by p.id asc")
    List<EduYearPlan> findAllByIdInForUpdateOrderByIdAsc(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from EduYearPlan p where p.status in :statuses order by p.id asc")
    List<EduYearPlan> findAllByStatusInForUpdateOrderByIdAsc(@Param("statuses") Collection<EduPlanStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EduYearPlan> findAllByEditingAtLessThanEqualOrderByIdAsc(Instant editingAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from EduYearPlan p where p.editingBy is not null order by p.id asc")
    List<EduYearPlan> findAllByEditingByIsNotNull();

    // EDU-06: toàn trường theo trạng thái được xem, hoặc nhóm tuổi Tổ trưởng phụ trách, hoặc bản đã ban hành của GV
    default Page<EduYearPlan> search(
            Long yearId,
            Long ageId,
            Collection<EduPlanStatus> schoolWide,
            Collection<Long> fullAges,
            Collection<Long> issuedAges,
            String keyword,
            Pageable pageable) {
        return search(
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                yearId,
                ageId,
                schoolWide,
                fullAges,
                issuedAges,
                keyword,
                pageable);
    }

    default Page<EduYearPlan> search(
            com.carenest.utils.enums.EduPlanLevel level,
            Long yearId,
            Long ageId,
            Collection<EduPlanStatus> statuses,
            Collection<Long> full,
            Collection<Long> approved,
            String keyword,
            Pageable pageable) {
        return search(level, yearId, ageId, statuses, full, approved, List.of(), keyword, pageable);
    }

    @Query("""
            select p from EduYearPlan p
            where p.planLevel = :planLevel
              and (:schoolYearId is null or p.schoolYearId = :schoolYearId)
              and (:ageGroupId is null or p.ageGroupId = :ageGroupId)
              and (p.status in :schoolWideStatuses
                   or p.ageGroupId in :fullAgeGroupIds
                   or (p.status = com.carenest.utils.enums.EduPlanStatus.REVIEW
                       and p.ageGroupId in :reviewAgeGroupIds)
                   or (p.status = com.carenest.utils.enums.EduPlanStatus.APPROVED
                       and p.ageGroupId in :approvedAgeGroupIds))
              and (:keyword is null or lower(p.title) like :keyword or lower(p.code) like :keyword)
            """)
    Page<EduYearPlan> search(
            @Param("planLevel") com.carenest.utils.enums.EduPlanLevel planLevel,
            @Param("schoolYearId") Long schoolYearId,
            @Param("ageGroupId") Long ageGroupId,
            @Param("schoolWideStatuses") Collection<EduPlanStatus> schoolWideStatuses,
            @Param("fullAgeGroupIds") Collection<Long> fullAgeGroupIds,
            @Param("approvedAgeGroupIds") Collection<Long> approvedAgeGroupIds,
            @Param("reviewAgeGroupIds") Collection<Long> reviewAgeGroupIds,
            @Param("keyword") String keyword,
            Pageable pageable);
}
