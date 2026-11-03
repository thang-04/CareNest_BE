package com.carenest.repository.health;

import com.carenest.entity.health.HealthMeasurementEdit;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthMeasurementEditRepository extends JpaRepository<HealthMeasurementEdit, Long> {

    List<HealthMeasurementEdit> findByMeasurementIdInOrderByCreatedAtAscIdAsc(Collection<Long> measurementIds);
}
