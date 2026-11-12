package com.carenest.repository.health;

import com.carenest.entity.health.HealthInterpretation;
import com.carenest.utils.enums.InterpretationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthInterpretationRepository extends JpaRepository<HealthInterpretation, Long> {

    /** Bản đã duyệt mới nhất (phụ huynh xem — HLT-05). */
    Optional<HealthInterpretation> findFirstByChildIdAndStatusOrderByApprovedAtDescIdDesc(
            Long childId, InterpretationStatus status);

    /** Bản mới nhất (nháp hoặc đã duyệt) cho nhân sự. */
    Optional<HealthInterpretation> findFirstByChildIdOrderByGeneratedAtDescIdDesc(Long childId);

    Optional<HealthInterpretation> findByIdAndChildId(Long id, Long childId);
}
