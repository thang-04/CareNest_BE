package com.carenest.repository.child;

import com.carenest.entity.child.GuardianChildLink;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuardianChildLinkRepository extends JpaRepository<GuardianChildLink, Long> {

    boolean existsByUserIdAndChildId(Long userId, Long childId);

    @Query("select g.childId from GuardianChildLink g where g.userId = :userId order by g.childId")
    List<Long> findChildIdsByUserId(@Param("userId") Long userId);

    List<GuardianChildLink> findByChildIdOrderByCreatedAtAscIdAsc(Long childId);

    Optional<GuardianChildLink> findByUserIdAndChildId(Long userId, Long childId);

    @Query("select g.childId, count(g) from GuardianChildLink g where g.childId in :childIds group by g.childId")
    List<Object[]> countByChildIds(@Param("childIds") Collection<Long> childIds);
}
