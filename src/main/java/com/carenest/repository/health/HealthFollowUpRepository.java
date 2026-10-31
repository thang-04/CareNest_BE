package com.carenest.repository.health;

import com.carenest.entity.health.HealthFollowUp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthFollowUpRepository extends JpaRepository<HealthFollowUp, Long> {

    Optional<HealthFollowUp> findByChildIdAndClosedAtIsNull(Long childId);

    List<HealthFollowUp> findByChildIdIn(Collection<Long> childIds);

    List<HealthFollowUp> findByChildIdInAndClosedAtIsNull(Collection<Long> childIds);
}
