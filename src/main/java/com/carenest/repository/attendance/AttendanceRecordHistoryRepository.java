package com.carenest.repository.attendance;

import com.carenest.entity.attendance.AttendanceRecordHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordHistoryRepository extends JpaRepository<AttendanceRecordHistory, Long> {

    List<AttendanceRecordHistory> findByRecordIdOrderByIdAsc(Long recordId);
}
