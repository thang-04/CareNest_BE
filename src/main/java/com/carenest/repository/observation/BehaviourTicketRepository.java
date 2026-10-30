package com.carenest.repository.observation;

import com.carenest.entity.observation.BehaviourTicket;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BehaviourTicketRepository extends JpaRepository<BehaviourTicket, Long> {

    List<BehaviourTicket> findByClassroomIdInAndPeriodStartBetween(
            Collection<Long> classroomIds, LocalDate from, LocalDate to);

    List<BehaviourTicket> findByChildIdInAndPeriodStartBetween(Collection<Long> childIds, LocalDate from, LocalDate to);
}
