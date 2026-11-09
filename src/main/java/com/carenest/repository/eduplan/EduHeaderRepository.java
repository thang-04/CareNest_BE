package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduPlan;
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
@org.springframework.data.repository.NoRepositoryBean
public interface EduHeaderRepository<T extends EduPlan> extends JpaRepository<T, Long> {

    boolean existsBySchoolYearIdAndAgeGroupId(Long schoolYearId, Long ageGroupId);

    Optional<T> findByOwnerIdAndCreatedRequestId(Long ownerId, UUID createdRequestId);

    boolean existsByCode(String code);

    List<T> findAllBySchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(Long yearId, Long ageId);

    List<T> findAllBySchoolYearIdIn(Collection<Long> schoolYearIds);

    // Mọi thao tác ghi khóa dòng: hai người cùng bấm sửa thì người sau thấy khóa của người trước (EDU-10)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from #{#entityName} p where p.id = :id")
    Optional<T> findForUpdateById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from #{#entityName} p where p.id in :ids order by p.id asc")
    List<T> findAllByIdInForUpdateOrderByIdAsc(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from #{#entityName} p where p.status in :statuses order by p.id asc")
    List<T> findAllByStatusInForUpdateOrderByIdAsc(@Param("statuses") Collection<EduPlanStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<T> findAllByEditingAtLessThanEqualOrderByIdAsc(Instant editingAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from #{#entityName} p where p.editingBy is not null order by p.id asc")
    List<T> findAllByEditingByIsNotNull();

    @Query("""
            select p from #{#entityName} p
            where (:schoolYearId is null or p.schoolYearId = :schoolYearId)
              and (:ageGroupId is null or p.ageGroupId = :ageGroupId)
              and (p.status in :schoolWideStatuses
                   or p.ageGroupId in :fullAgeGroupIds
                   or (p.status = com.carenest.utils.enums.EduPlanStatus.REVIEW
                       and p.ageGroupId in :reviewAgeGroupIds)
                   or (p.status = com.carenest.utils.enums.EduPlanStatus.APPROVED
                       and p.ageGroupId in :approvedAgeGroupIds))
              and (:keyword is null or lower(p.title) like :keyword or lower(p.code) like :keyword)
            """)
    Page<T> search(
            @Param("schoolYearId") Long schoolYearId,
            @Param("ageGroupId") Long ageGroupId,
            @Param("schoolWideStatuses") Collection<EduPlanStatus> schoolWideStatuses,
            @Param("fullAgeGroupIds") Collection<Long> fullAgeGroupIds,
            @Param("approvedAgeGroupIds") Collection<Long> approvedAgeGroupIds,
            @Param("reviewAgeGroupIds") Collection<Long> reviewAgeGroupIds,
            @Param("keyword") String keyword,
            Pageable pageable);
}
