package com.carenest.repository.attendance;

import com.carenest.entity.attendance.DailyHealthEntry;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DailyHealthEntryRepository extends JpaRepository<DailyHealthEntry, Long> {

    @Query(
            value =
                    "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(concat('carenest:health:', CAST(:recordedBy AS text), ':', CAST(:requestId AS text)), 0))",
            nativeQuery = true)
    Integer lockRequest(Long recordedBy, String requestId);

    Optional<DailyHealthEntry> findByRecordedByAndRequestId(Long recordedBy, String requestId);

    List<DailyHealthEntry> findByChildIdAndDateOrderByRecordedAtDescIdDesc(Long childId, LocalDate date);

    List<DailyHealthEntry> findByChildIdInAndDateBetweenOrderByRecordedAtDescIdDesc(
            Collection<Long> childIds, LocalDate from, LocalDate to);

    List<DailyHealthEntry> findByClassroomIdInAndDateOrderByRecordedAtDescIdDesc(
            Collection<Long> classroomIds, LocalDate date);
}
