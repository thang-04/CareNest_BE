package com.carenest.repository.attendance;

import com.carenest.entity.attendance.AttendanceRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByChildIdInAndDate(Collection<Long> childIds, LocalDate date);

    List<AttendanceRecord> findByClassroomIdInAndDate(Collection<Long> classroomIds, LocalDate date);

    List<AttendanceRecord> findByChildIdInAndDateBetween(Collection<Long> childIds, LocalDate from, LocalDate to);
}
