package com.carenest.repository.observation;

import com.carenest.entity.observation.CriteriaOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriteriaOptionRepository extends JpaRepository<CriteriaOption, Long> {

    List<CriteriaOption> findByCriteriaIdInOrderBySortOrderAscIdAsc(Collection<Long> criteriaIds);
}
