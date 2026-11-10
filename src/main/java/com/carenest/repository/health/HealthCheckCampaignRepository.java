package com.carenest.repository.health;

import com.carenest.entity.health.HealthCheckCampaign;
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

    /** Đợt khám và kỳ cân đo chưa đóng, chưa hủy đã qua ngày kết thúc: hệ thống tự đóng (HLT-17). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<HealthCheckCampaign> findByClosedAtIsNullAndCancelledAtIsNullAndEndDateBefore(LocalDate date);
}
