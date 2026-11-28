package com.carenest.repository.attendance;

import com.carenest.entity.attendance.LeaveRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** Đơn của các trẻ giao với khoảng [from, to] (chưa lọc phần đã hủy). */
    @Query("select r from LeaveRequest r where r.childId in :childIds and r.fromDate <= :to and r.toDate >= :from"
            + " order by r.fromDate desc, r.id desc")
    List<LeaveRequest> findOverlapping(
            @Param("childIds") Collection<Long> childIds, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<LeaveRequest> findByChildIdInOrderByFromDateDescIdDesc(Collection<Long> childIds);

    /** Ghi GV đã xem một lần: đơn đã có người xem thì giữ nguyên (không ghi đè khi nhiều GV mở cùng lúc). */
    @Modifying(flushAutomatically = true)
    @Query("update LeaveRequest r set r.teacherSeenAt = :at, r.teacherSeenBy = :userId"
            + " where r.id in :ids and r.teacherSeenAt is null")
    int markTeacherSeen(@Param("ids") Collection<Long> ids, @Param("userId") Long userId, @Param("at") Instant at);

    /** Đơn nghỉ còn hiệu lực sau :date của trẻ học lớp thuộc năm học (rút ngắn năm học). */
    @Query(
            value = "select count(*) from leave_requests l join school_years y on y.id = :yearId"
                    + " where l.from_date >= y.start_date and l.to_date > :date"
                    + " and (l.cancelled_from is null or (l.cancelled_from > l.from_date and l.cancelled_from - 1 > :date))"
                    + " and exists (select 1 from enrollments e join classrooms c on c.id = e.classroom_id"
                    + " where e.child_id = l.child_id and c.school_year_id = :yearId)",
            nativeQuery = true)
    long countOfYearAfter(@Param("yearId") Long yearId, @Param("date") java.time.LocalDate date);
}
