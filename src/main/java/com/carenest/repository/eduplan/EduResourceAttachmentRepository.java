package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduResourceAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduResourceAttachmentRepository extends JpaRepository<EduResourceAttachment, Long> {
    List<EduResourceAttachment> findAllByClassDocIdOrderBySortOrderAscIdAsc(Long classDocId);

    Optional<EduResourceAttachment> findByIdAndClassDocId(Long id, Long classDocId);

    boolean existsByClassDocIdAndActivityKeyAndItemId(Long classDocId, String activityKey, Long itemId);

    long countByClassDocIdAndActivityKey(Long classDocId, String activityKey);

    long countByItemId(Long itemId);

    List<EduResourceAttachment> findAllByItemIdOrderByIdAsc(Long itemId);

    List<EduResourceAttachment> findAllByClassDocIdAndActivityKey(Long classDocId, String activityKey);

    @Query("select a.itemId, count(a) from EduResourceAttachment a where a.itemId in :itemIds group by a.itemId")
    List<Object[]> countByItemIds(@Param("itemIds") Collection<Long> itemIds);
}
