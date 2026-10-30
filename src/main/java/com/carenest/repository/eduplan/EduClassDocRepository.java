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
}
