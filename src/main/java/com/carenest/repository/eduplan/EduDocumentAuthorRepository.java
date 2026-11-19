package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduDocumentAuthor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduDocumentAuthorRepository extends JpaRepository<EduDocumentAuthor, Long> {

    List<EduDocumentAuthor> findAllByYearPlanId(Long yearPlanId);

    List<EduDocumentAuthor> findAllByClassDocId(Long classDocId);

    List<EduDocumentAuthor> findAllByClassDocIdIn(Collection<Long> classDocIds);

    Optional<EduDocumentAuthor> findByYearPlanIdAndUserId(Long yearPlanId, Long userId);

    Optional<EduDocumentAuthor> findByClassDocIdAndUserId(Long classDocId, Long userId);

    @Query("select a.userId from EduDocumentAuthor a where a.yearPlanId = :yearPlanId")
    Set<Long> findUserIdsByYearPlanId(@Param("yearPlanId") Long yearPlanId);

    @Query("select a.userId from EduDocumentAuthor a where a.classDocId = :classDocId")
    Set<Long> findUserIdsByClassDocId(@Param("classDocId") Long classDocId);

    @Query("select a.userId from EduDocumentAuthor a where a.classDocId in :classDocIds")
    Set<Long> findUserIdsByClassDocIdIn(@Param("classDocIds") Collection<Long> classDocIds);
}
