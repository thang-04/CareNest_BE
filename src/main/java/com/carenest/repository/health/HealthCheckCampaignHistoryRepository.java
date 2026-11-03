package com.carenest.repository.health;

import com.carenest.entity.health.HealthCheckCampaignHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthCheckCampaignHistoryRepository extends JpaRepository<HealthCheckCampaignHistory, Long> {

    List<HealthCheckCampaignHistory> findByCampaignIdOrderByCreatedAtAscIdAsc(Long campaignId);
}
