package com.carenest.repository.attendance;

import com.carenest.entity.attendance.AttendanceDayUnlock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceDayUnlockRepository extends JpaRepository<AttendanceDayUnlock, Long> {

    Optional<AttendanceDayUnlock> findFirstByClassroomIdAndDateAndValidUntilAfterOrderByIdDesc(
            Long classroomId, LocalDate date, Instant now);

    List<AttendanceDayUnlock> findByCampusIdInOrderByUnlockedAtDesc(Collection<Long> campusIds);
}
