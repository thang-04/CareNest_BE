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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthCheckCampaignRepository extends JpaRepository<HealthCheckCampaign, Long> {

    List<HealthCheckCampaign> findByCampusIdInOrderByStartDateDescIdDesc(Collection<Long> campusIds);

    /** Đợt/kỳ chưa đóng, chưa hủy của điểm trường: tối đa 1 (uk_health_check_campaigns_campus_open). */
    Optional<HealthCheckCampaign> findFirstByCampusIdAndClosedAtIsNullAndCancelledAtIsNull(Long campusId);

    List<HealthCheckCampaign> findByCampusIdAndCancelledAtIsNull(Long campusId);

    /** Đợt/kỳ chưa hủy cùng loại ở điểm trường khác có ngày giao với from..to (kỳ liên quan, G94-180/HLT-09). */
    @Query("select c from HealthCheckCampaign c where c.kind = :kind and c.campusId <> :campusId"
            + " and c.cancelledAt is null and c.startDate <= :to and c.endDate >= :from")
    List<HealthCheckCampaign> findLinkable(
            @Param("kind") HealthCheckKind kind,
            @Param("campusId") Long campusId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("select c from HealthCheckCampaign c where c.kind = :kind"
            + " and c.cancelledAt is null and c.startDate <= :to and c.endDate >= :from")
    List<HealthCheckCampaign> findLinkableCandidates(
            @Param("kind") HealthCheckKind kind, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Đợt khám và kỳ cân đo chưa đóng, chưa hủy đã qua ngày kết thúc: hệ thống tự đóng (HLT-17). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<HealthCheckCampaign> findByClosedAtIsNullAndCancelledAtIsNullAndEndDateBefore(LocalDate date);
}
