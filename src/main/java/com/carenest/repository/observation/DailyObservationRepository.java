package com.carenest.repository.observation;

import com.carenest.entity.observation.DailyObservation;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyObservationRepository extends JpaRepository<DailyObservation, Long> {

    List<DailyObservation> findByChildIdInAndDate(Collection<Long> childIds, LocalDate date);

    List<DailyObservation> findByChildIdInAndDateBetween(Collection<Long> childIds, LocalDate from, LocalDate to);
}
