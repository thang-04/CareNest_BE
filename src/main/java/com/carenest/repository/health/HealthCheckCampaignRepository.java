package com.carenest.repository.health;

import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.utils.enums.HealthCheckKind;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface HealthCheckCampaignRepository extends JpaRepository<HealthCheckCampaign, Long> {

    List<HealthCheckCampaign> findByCampusIdInOrderByStartDateDescIdDesc(Collection<Long> campusIds);

    /** Đợt/kỳ chưa đóng, chưa hủy của điểm trường: tối đa 1 (uk_health_check_campaigns_campus_open). */
    Optional<HealthCheckCampaign> findFirstByCampusIdAndClosedAtIsNullAndCancelledAtIsNull(Long campusId);

    List<HealthCheckCampaign> findByCampusIdAndCancelledAtIsNull(Long campusId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<HealthCheckCampaign> findByKindAndClosedAtIsNullAndCancelledAtIsNullAndEndDateBefore(
            HealthCheckKind kind, LocalDate date);
}
