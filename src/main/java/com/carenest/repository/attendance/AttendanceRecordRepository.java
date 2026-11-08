package com.carenest.repository.attendance;

import com.carenest.entity.attendance.AttendanceRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    @Query("SELECT MIN(r.date) FROM AttendanceRecord r WHERE r.date BETWEEN :from AND :to")
    Optional<LocalDate> findFirstDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<AttendanceRecord> findByChildIdInAndDate(Collection<Long> childIds, LocalDate date);

    List<AttendanceRecord> findByClassroomIdInAndDate(Collection<Long> classroomIds, LocalDate date);

    List<AttendanceRecord> findByChildIdInAndDateBetween(Collection<Long> childIds, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByClassroomIdInAndDateBetween(
            Collection<Long> classroomIds, LocalDate from, LocalDate to);
}
