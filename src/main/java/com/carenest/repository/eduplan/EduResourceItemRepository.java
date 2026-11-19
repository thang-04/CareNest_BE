package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduResourceItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduResourceItemRepository extends JpaRepository<EduResourceItem, Long> {
    Optional<EduResourceItem> findByOwnerIdAndUploadRequestId(Long ownerId, UUID requestId);

    Optional<EduResourceItem> findFirstByOwnerIdAndKindAndUrlOrderByIdAsc(Long ownerId, String kind, String url);

    /** EDU-84: kho chỉ cá nhân. */
    List<EduResourceItem> findAllByOwnerId(Long ownerId);
}
