package com.carenest.repository.observation;

import com.carenest.entity.observation.DailyActivityResult;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyActivityResultRepository extends JpaRepository<DailyActivityResult, Long> {

    List<DailyActivityResult> findByChildIdInAndDate(Collection<Long> childIds, LocalDate date);

    List<DailyActivityResult> findByChildIdInAndDateBetween(Collection<Long> childIds, LocalDate from, LocalDate to);
}
