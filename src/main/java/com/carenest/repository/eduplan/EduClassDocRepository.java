package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduClassDoc;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduClassDocRepository extends JpaRepository<EduClassDoc, Long> {

    List<EduClassDoc> findAllByClassroomIdInOrderByIdAsc(Collection<Long> classroomIds);

    List<EduClassDoc> findAllByClassroomIdAndYearPlanId(Long classroomId, Long yearPlanId);

    Optional<EduClassDoc> findByDocKey(String docKey);

    // Gửi / duyệt hồ sơ chủ đề đổi trạng thái nhiều tài liệu một lúc: khóa dòng để hai người duyệt không chồng nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.docKey = :docKey")
    Optional<EduClassDoc> findForUpdateByDocKey(@Param("docKey") String docKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.id = :id")
    Optional<EduClassDoc> findForUpdateById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.id in :ids order by d.id asc")
    List<EduClassDoc> findAllByIdInForUpdateOrderByIdAsc(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.status in :statuses order by d.id asc")
    List<EduClassDoc> findAllByStatusInForUpdateOrderByIdAsc(
            @Param("statuses") Collection<com.carenest.utils.enums.EduClassDocStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.yearPlanId = :yearPlanId order by d.id asc")
    List<EduClassDoc> findAllByYearPlanIdForUpdateOrderByIdAsc(@Param("yearPlanId") Long yearPlanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select d from EduClassDoc d where d.classroomId = :classroomId and d.yearPlanId = :yearPlanId and (d.themeId = :themeId or (:themeId is null and d.themeId is null)) order by d.id asc")
    List<EduClassDoc> findAllByDossierForUpdateOrderByIdAsc(
            @Param("classroomId") Long classroomId,
            @Param("yearPlanId") Long yearPlanId,
            @Param("themeId") Long themeId);
}
