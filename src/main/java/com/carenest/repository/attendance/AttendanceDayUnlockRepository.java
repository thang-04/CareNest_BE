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

    /** Lần mở lại còn hạn của các lớp trong khoảng ngày (phụ huynh không thấy số đang sửa dở). */
    List<AttendanceDayUnlock> findByClassroomIdInAndDateBetweenAndValidUntilAfter(
            Collection<Long> classroomIds, LocalDate from, LocalDate to, Instant now);

    List<AttendanceDayUnlock> findByCampusIdInOrderByUnlockedAtDesc(Collection<Long> campusIds);
}
