package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduLearningResource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduLearningResourceRepository extends JpaRepository<EduLearningResource, Long> {
    List<EduLearningResource> findAllByClassDocIdOrderByIdAsc(Long classDocId);

    Optional<EduLearningResource> findByClassDocIdAndUploadRequestId(Long classDocId, UUID requestId);

    Optional<EduLearningResource> findByIdAndClassDocId(Long id, Long classDocId);
}
