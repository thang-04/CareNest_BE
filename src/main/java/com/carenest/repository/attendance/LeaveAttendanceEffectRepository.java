package com.carenest.repository.attendance;

import com.carenest.entity.attendance.LeaveAttendanceEffect;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveAttendanceEffectRepository extends JpaRepository<LeaveAttendanceEffect, Long> {

    List<LeaveAttendanceEffect> findByLeaveRequestId(Long leaveRequestId);
}
