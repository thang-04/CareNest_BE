package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduResourceItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduResourceItemRepository extends JpaRepository<EduResourceItem, Long> {
    Optional<EduResourceItem> findByOwnerIdAndUploadRequestId(Long ownerId, UUID requestId);

    Optional<EduResourceItem> findFirstByOwnerIdAndKindAndUrlOrderByIdAsc(Long ownerId, String kind, String url);

    @org.springframework.data.jpa.repository.Query("""
            select g.id from AgeGroup g, AgeGroup chosen where chosen.id = :selected
            and g.ageFromMonths = chosen.ageFromMonths and g.ageToMonths = chosen.ageToMonths
            """)
    List<Long> matchingAgeGroupIds(@org.springframework.data.repository.query.Param("selected") Long selected);

    /** EDU-84: kho chỉ cá nhân. */
    List<EduResourceItem> findAllByOwnerId(Long ownerId);
}
