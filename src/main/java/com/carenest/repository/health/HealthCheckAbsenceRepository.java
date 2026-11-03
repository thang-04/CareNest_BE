package com.carenest.repository.health;

import com.carenest.entity.health.HealthCheckAbsence;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthCheckAbsenceRepository extends JpaRepository<HealthCheckAbsence, HealthCheckAbsence.Key> {

    List<HealthCheckAbsence> findByCampaignIdIn(Collection<Long> campaignIds);

    void deleteByCampaignId(Long campaignId);
}
