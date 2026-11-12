package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduResourceItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduResourceItemRepository extends JpaRepository<EduResourceItem, Long> {
    Optional<EduResourceItem> findByOwnerIdAndUploadRequestId(Long ownerId, UUID requestId);

    Optional<EduResourceItem> findFirstByOwnerIdAndKindAndUrlOrderByIdAsc(Long ownerId, String kind, String url);

    /** EDU-84: học liệu một người thấy: của mình, của lớp mình dạy, của trường, hoặc đề nghị mình duyệt được. */
    @Query("""
            select i from EduResourceItem i
            where i.ownerId = :userId
               or (i.scope = 'CLASS' and i.classroomId in :classIds)
               or i.scope = 'SCHOOL'
               or (i.shareStatus = 'PENDING' and (:vicePrincipal = true or i.ageGroupId in :leaderAgeGroups))
            """)
    List<EduResourceItem> findVisible(
            @Param("userId") Long userId,
            @Param("classIds") Collection<Long> classIds,
            @Param("vicePrincipal") boolean vicePrincipal,
            @Param("leaderAgeGroups") Collection<Long> leaderAgeGroups);
}
