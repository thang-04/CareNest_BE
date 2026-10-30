package com.carenest.repository.health;

import com.carenest.entity.health.HealthCheckCampaign;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthCheckCampaignRepository extends JpaRepository<HealthCheckCampaign, Long> {

    List<HealthCheckCampaign> findByCampusIdInOrderByStartDateDescIdDesc(Collection<Long> campusIds);

    /** Đợt/kỳ chưa đóng, chưa hủy của điểm trường: tối đa 1 (uk_health_check_campaigns_campus_open). */
    Optional<HealthCheckCampaign> findFirstByCampusIdAndClosedAtIsNullAndCancelledAtIsNull(Long campusId);

    List<HealthCheckCampaign> findByCampusIdAndCancelledAtIsNull(Long campusId);
}
