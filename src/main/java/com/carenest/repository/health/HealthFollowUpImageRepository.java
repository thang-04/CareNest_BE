package com.carenest.repository.health;

import com.carenest.entity.health.HealthFollowUpImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthFollowUpImageRepository extends JpaRepository<HealthFollowUpImage, Long> {

    List<HealthFollowUpImage> findByEventIdInOrderByEventIdAscPositionAsc(Collection<Long> eventIds);
}
