package com.carenest.repository.health;

import com.carenest.entity.health.HealthMeasurement;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthMeasurementRepository extends JpaRepository<HealthMeasurement, Long> {

    List<HealthMeasurement> findByCampaignIdIn(Collection<Long> campaignIds);

    boolean existsByCampaignId(Long campaignId);

    boolean existsByChildIdAndCampaignId(Long childId, Long campaignId);

    Optional<HealthMeasurement> findByChildIdAndDate(Long childId, LocalDate date);

    Optional<HealthMeasurement> findByChildIdAndCampaignId(Long childId, Long campaignId);

    List<HealthMeasurement> findByChildIdOrderByDateDescIdDesc(Long childId);

    List<HealthMeasurement> findByChildIdIn(Collection<Long> childIds);

    /** Tên bác sĩ kết luận đã ghi ở các phiếu khám của đợt (cả nháp), để chọn nhanh. */
    @Query(
            value = "SELECT DISTINCT btrim(exam -> 'conclusion' ->> 'doctorName') FROM health_measurements"
                    + " WHERE campaign_id = :campaignId AND exam IS NOT NULL",
            nativeQuery = true)
    List<String> findDoctorNames(@Param("campaignId") Long campaignId);
}
