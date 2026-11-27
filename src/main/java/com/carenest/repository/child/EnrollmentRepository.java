package com.carenest.repository.child;

import com.carenest.entity.child.Enrollment;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Query("""
            select e.classroomId from Enrollment e
            where e.childId = :childId
              and e.validFrom <= :date and (e.validTo is null or e.validTo >= :date)
            order by e.validFrom desc, e.id desc
            """)
    List<Long> findActiveClassroomIds(@Param("childId") Long childId, @Param("date") LocalDate date);

    @Query("select e from Enrollment e where e.classroomId in :classroomIds"
            + " and e.validFrom <= :date and (e.validTo is null or e.validTo >= :date) order by e.id")
    List<Enrollment> findActiveByClassroomIds(
            @Param("classroomIds") Collection<Long> classroomIds, @Param("date") LocalDate date);

    @Query("select e from Enrollment e where e.classroomId = :classroomId"
            + " and e.validFrom <= :to and (e.validTo is null or e.validTo >= :from)")
    List<Enrollment> findDuring(
            @Param("classroomId") Long classroomId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select e from Enrollment e where e.classroomId in :classroomIds"
            + " and e.validFrom <= :to and (e.validTo is null or e.validTo >= :from)")
    List<Enrollment> findDuringClassroomIds(
            @Param("classroomIds") Collection<Long> classroomIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("select e from Enrollment e where e.childId = :childId"
            + " and e.validFrom <= :to and (e.validTo is null or e.validTo >= :from) order by e.validFrom, e.id")
    List<Enrollment> findChildDuring(
            @Param("childId") Long childId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    // Enrollment chưa kết thúc tới ngày :date (gồm cả lớp sẽ học), dùng khi chuyển lớp
    @Query("select e from Enrollment e where e.childId = :childId and (e.validTo is null or e.validTo >= :date)")
    List<Enrollment> findOpenByChildId(@Param("childId") Long childId, @Param("date") LocalDate date);

    /** Lớp chưa bắt đầu (ngày bắt đầu đi học hoặc lịch chuyển lớp sau :date) của các trẻ (G94-180/CHD-14). */
    @Query("select e from Enrollment e where e.childId in :childIds and e.validFrom > :date order by e.validFrom, e.id")
    List<Enrollment> findStartingAfter(@Param("childIds") Collection<Long> childIds, @Param("date") LocalDate date);

    /**
     * Số trẻ còn ở lớp từ ngày :date trở đi (đang học tới ngày đó hoặc sẽ vào sau), trừ trẻ đang xếp và trẻ có lịch nghỉ
     * học từ :date trở về trước (từ ngày nghỉ trẻ không giữ chỗ): sĩ số khi xếp / chuyển lớp từ một ngày (G94-180/CHD-14,
     * CHD-03).
     */
    @Query("select count(e) from Enrollment e where e.classroomId = :classroomId"
            + " and (e.validTo is null or e.validTo >= :date) and e.childId <> :excludeChildId"
            + " and not exists (select c.id from Child c where c.id = e.childId and c.scheduledLeaveOn <= :date)")
    long countOccupyingFrom(
            @Param("classroomId") Long classroomId,
            @Param("date") LocalDate date,
            @Param("excludeChildId") Long excludeChildId);

    /** Như {@link #countOccupyingFrom} cho nhiều lớp trong một truy vấn: mỗi dòng [classroomId, số trẻ]. */
    @Query("select e.classroomId, count(e) from Enrollment e where e.classroomId in :classroomIds"
            + " and (e.validTo is null or e.validTo >= :date) and e.childId <> :excludeChildId"
            + " and not exists (select c.id from Child c where c.id = e.childId and c.scheduledLeaveOn <= :date)"
            + " group by e.classroomId")
    List<Object[]> countOccupyingFromByClassroomIds(
            @Param("classroomIds") Collection<Long> classroomIds,
            @Param("date") LocalDate date,
            @Param("excludeChildId") Long excludeChildId);

    /** Enrollment gần nhất của trẻ (kể cả đã đóng): điểm trường của trẻ đã nghỉ học là điểm trường lớp cuối. */
    Optional<Enrollment> findFirstByChildIdOrderByValidFromDescIdDesc(Long childId);

    Optional<Enrollment> findFirstByChildIdOrderByValidFromAscIdAsc(Long childId);

    /** Lớp đầu tiên của trẻ từ ngày :from (mở lại hồ sơ): lần học trước không tính (G94-180/CHD-14). */
    Optional<Enrollment> findFirstByChildIdAndValidFromGreaterThanEqualOrderByValidFromAscIdAsc(
            Long childId, LocalDate from);

    @Query("""
            select e from Enrollment e
            where e.childId in :childIds
              and e.validFrom <= :date and (e.validTo is null or e.validTo >= :date)
            order by e.validFrom desc, e.id desc
            """)
    List<Enrollment> findActiveByChildIdIn(@Param("childIds") Collection<Long> childIds, @Param("date") LocalDate date);

    /*
     * Đã nghỉ học tính tới :date (CHD-03, CHD-04): cho nghỉ (status LEFT, left_on ≤ :date — kể cả trẻ chưa từng có lớp,
     * hoặc nghỉ ngay ngày vào lớp nên lớp bị xóa), hoặc ra trường khi chuyển năm học (từng có lớp, không còn lớp chưa
     * kết thúc tới :date; ra trường không đổi status). Chờ xếp lớp (mới tiếp nhận hoặc mở lại hồ sơ) không tính là nghỉ.
     */
    @Query("""
            select c.id from Child c
            where c.id in :childIds
              and ((c.status = com.carenest.utils.enums.ChildStatus.LEFT and c.leftOn <= :date)
                or (c.status <> com.carenest.utils.enums.ChildStatus.PENDING_PLACEMENT
                    and exists (select 1 from Enrollment e where e.childId = c.id)
                    and not exists (
                        select 1 from Enrollment o
                        where o.childId = c.id and (o.validTo is null or o.validTo >= :date))))
            """)
    List<Long> findChildIdsLeftSchool(@Param("childIds") Collection<Long> childIds, @Param("date") LocalDate date);
}
