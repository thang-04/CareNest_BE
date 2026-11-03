package com.carenest.repository.attendance;

import com.carenest.entity.attendance.PickupRecord;
import com.carenest.utils.enums.PickupOutcome;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupRecordRepository extends JpaRepository<PickupRecord, Long> {

    List<PickupRecord> findByChildIdInAndDateOrderByRecordedAtAscIdAsc(Collection<Long> childIds, LocalDate date);

    List<PickupRecord> findByClassroomIdInAndDateOrderByRecordedAtDescIdDesc(
            Collection<Long> classroomIds, LocalDate date);

    boolean existsByChildIdAndDateAndOutcome(Long childId, LocalDate date, PickupOutcome outcome);
}
