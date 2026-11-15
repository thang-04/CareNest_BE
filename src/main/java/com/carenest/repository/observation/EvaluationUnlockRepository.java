package com.carenest.repository.observation;

import com.carenest.entity.observation.EvaluationUnlock;
import com.carenest.utils.enums.EvaluationLockKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationUnlockRepository extends JpaRepository<EvaluationUnlock, Long> {

    Optional<EvaluationUnlock> findFirstByClassroomIdAndKindAndPeriodKeyAndValidUntilAfterOrderByIdDesc(
            Long classroomId, EvaluationLockKind kind, String periodKey, Instant now);

    List<EvaluationUnlock> findByValidUntilBetween(Instant from, Instant to);

    List<EvaluationUnlock> findAllByOrderByUnlockedAtDescIdDesc();
}
