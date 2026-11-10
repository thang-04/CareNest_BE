package com.carenest.repository.observation;

import com.carenest.entity.observation.ChildSummary;
import com.carenest.utils.enums.SummaryPeriodType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildSummaryRepository extends JpaRepository<ChildSummary, Long> {

    Optional<ChildSummary> findByChildIdAndPeriodTypeAndPeriodFrom(
            Long childId, SummaryPeriodType periodType, LocalDate periodFrom);

    List<ChildSummary> findByChildIdOrderByPeriodFromDesc(Long childId);
}
