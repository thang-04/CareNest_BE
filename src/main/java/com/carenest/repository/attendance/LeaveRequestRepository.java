package com.carenest.repository.attendance;

import com.carenest.entity.attendance.LeaveRequest;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** Đơn của các trẻ giao với khoảng [from, to] (chưa lọc phần đã hủy). */
    @Query("select r from LeaveRequest r where r.childId in :childIds and r.fromDate <= :to and r.toDate >= :from"
            + " order by r.fromDate desc, r.id desc")
    List<LeaveRequest> findOverlapping(
            @Param("childIds") Collection<Long> childIds, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<LeaveRequest> findByChildIdInOrderByFromDateDescIdDesc(Collection<Long> childIds);
}
