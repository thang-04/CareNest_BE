package com.carenest.repository;

import com.carenest.entity.PickupPerson;
import com.carenest.entity.PickupStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PickupPersonRepository extends JpaRepository<PickupPerson, Long> {

    List<PickupPerson> findByChildIdOrderByCreatedAtAsc(Long childId);

    boolean existsByReviewedById(Long reviewerId);

    List<PickupPerson> findByChildIdAndStatusOrderByCreatedAtAsc(Long childId, PickupStatus status);

    @EntityGraph(attributePaths = "child")
    List<PickupPerson> findByStatusOrderByCreatedAtAsc(PickupStatus status);
}
