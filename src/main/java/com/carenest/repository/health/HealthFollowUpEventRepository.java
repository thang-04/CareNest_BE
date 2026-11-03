package com.carenest.repository.health;

import com.carenest.entity.health.HealthFollowUpEvent;
import com.carenest.utils.enums.HealthFollowUpAction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthFollowUpEventRepository extends JpaRepository<HealthFollowUpEvent, Long> {

    List<HealthFollowUpEvent> findByFollowUpIdInOrderByIdAsc(Collection<Long> followUpIds);

    /** Sự kiện lần khám vào phiếu (OPENED, MEASUREMENT_ADDED) hoặc trao đổi tại buổi khám của một lần khám. */
    Optional<HealthFollowUpEvent> findFirstByMeasurementIdAndActionIn(
            Long measurementId, Collection<HealthFollowUpAction> actions);
}
