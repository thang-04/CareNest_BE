package com.carenest.repository.health;

import com.carenest.entity.health.HealthMeasurement;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthMeasurementRepository extends JpaRepository<HealthMeasurement, Long> {

    List<HealthMeasurement> findByCampaignIdIn(Collection<Long> campaignIds);

    boolean existsByCampaignId(Long campaignId);

    boolean existsByChildIdAndCampaignId(Long childId, Long campaignId);

    Optional<HealthMeasurement> findByChildIdAndDate(Long childId, LocalDate date);

    Optional<HealthMeasurement> findByChildIdAndCampaignId(Long childId, Long campaignId);

    List<HealthMeasurement> findByChildIdOrderByDateDescIdDesc(Long childId);
}
