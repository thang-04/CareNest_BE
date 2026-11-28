package com.carenest.repository.attendance;

import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.DayPart;
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

    /** ATT-13: lớp còn trẻ nghỉ sáng chưa đến trong ngày (chỉ id, không nạp bản ghi trước khi khóa lớp). */
    @Query("SELECT DISTINCT r.classroomId FROM AttendanceRecord r WHERE r.date = :date"
            + " AND r.absentPart = com.carenest.utils.enums.DayPart.MORNING"
            + " AND r.status = com.carenest.utils.enums.AttendanceStatus.EXCUSED")
    List<Long> findClassroomIdsWithOpenMorningLeave(@Param("date") LocalDate date);

    /** Các ngày đã qua còn trẻ nghỉ sáng chưa được đóng (lỡ lần chạy của job). */
    @Query("SELECT DISTINCT r.date FROM AttendanceRecord r WHERE r.date < :date"
            + " AND r.absentPart = com.carenest.utils.enums.DayPart.MORNING"
            + " AND r.status = com.carenest.utils.enums.AttendanceStatus.EXCUSED ORDER BY r.date")
    List<LocalDate> findPastDatesWithOpenMorningLeave(@Param("date") LocalDate date);

    List<AttendanceRecord> findByDateAndAbsentPartAndStatus(
            LocalDate date, DayPart absentPart, AttendanceStatus status);
}
