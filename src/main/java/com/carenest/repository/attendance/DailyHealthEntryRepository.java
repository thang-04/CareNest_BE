package com.carenest.repository.attendance;

import com.carenest.entity.attendance.DailyHealthEntry;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyHealthEntryRepository extends JpaRepository<DailyHealthEntry, Long> {

    Optional<DailyHealthEntry> findByRecordedByAndRequestId(Long recordedBy, String requestId);

    List<DailyHealthEntry> findByChildIdAndDateOrderByRecordedAtDescIdDesc(Long childId, LocalDate date);

    List<DailyHealthEntry> findByChildIdInAndDateBetweenOrderByRecordedAtDescIdDesc(
            Collection<Long> childIds, LocalDate from, LocalDate to);

    List<DailyHealthEntry> findByClassroomIdInAndDateOrderByRecordedAtDescIdDesc(
            Collection<Long> classroomIds, LocalDate date);
}
