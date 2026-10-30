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

    // Enrollment chưa kết thúc tới ngày :date (gồm cả lớp sẽ học), dùng khi chuyển lớp
    @Query("select e from Enrollment e where e.childId = :childId and (e.validTo is null or e.validTo >= :date)")
    List<Enrollment> findOpenByChildId(@Param("childId") Long childId, @Param("date") LocalDate date);

    /** Enrollment gần nhất của trẻ (kể cả đã đóng): điểm trường của trẻ đã nghỉ học là điểm trường lớp cuối. */
    Optional<Enrollment> findFirstByChildIdOrderByValidFromDescIdDesc(Long childId);

    @Query("""
            select e from Enrollment e
            where e.childId in :childIds
              and e.validFrom <= :date and (e.validTo is null or e.validTo >= :date)
            order by e.validFrom desc, e.id desc
            """)
    List<Enrollment> findActiveByChildIdIn(@Param("childIds") Collection<Long> childIds, @Param("date") LocalDate date);

    // Đã từng xếp lớp mà không còn lớp nào chưa kết thúc tới :date (nghỉ học/ra trường khi chuyển năm học)
    @Query("""
            select distinct e.childId from Enrollment e
            where e.childId in :childIds
              and not exists (
                  select 1 from Enrollment o
                  where o.childId = e.childId and (o.validTo is null or o.validTo >= :date))
            """)
    List<Long> findChildIdsLeftSchool(@Param("childIds") Collection<Long> childIds, @Param("date") LocalDate date);
}
