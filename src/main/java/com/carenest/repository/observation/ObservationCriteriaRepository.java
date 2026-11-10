package com.carenest.repository.observation;

import com.carenest.entity.observation.ObservationCriteria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationCriteriaRepository extends JpaRepository<ObservationCriteria, Long> {

    List<ObservationCriteria> findByActiveTrueOrderBySortOrderAscIdAsc();
}
