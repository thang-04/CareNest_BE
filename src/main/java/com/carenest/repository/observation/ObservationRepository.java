package com.carenest.repository.observation;

import com.carenest.entity.observation.Observation;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObservationRepository extends JpaRepository<Observation, Long> {

    // Nạp sẵn values: service dùng ngoài transaction (không giữ kết nối DB khi chờ AI)
    @Query("""
            select distinct o from Observation o left join fetch o.values
            where o.childId = :childId and o.observedOn between :from and :to
            order by o.observedOn
            """)
    List<Observation> findByChildInPeriod(
            @Param("childId") Long childId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select distinct o from Observation o left join fetch o.values
            where o.observedOn = :date and o.childId in :childIds
            """)
    List<Observation> findByChildrenOnDate(@Param("childIds") Collection<Long> childIds, @Param("date") LocalDate date);
}
