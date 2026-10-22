package com.carenest.repository.child;

import com.carenest.entity.child.Child;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildRepository extends JpaRepository<Child, Long> {

    Page<Child> findByFullNameContainingIgnoreCase(String keyword, Pageable pageable);

    // Trẻ đang học (tại date) ở một trong các lớp — phạm vi theo lớp/campus do service truyền vào
    @Query("""
            select c from Child c
            where lower(c.fullName) like :pattern
              and exists (select 1 from Enrollment e where e.childId = c.id and e.classroomId in :classroomIds
                          and e.validFrom <= :date and (e.validTo is null or e.validTo >= :date))
            """)
    Page<Child> searchEnrolled(
            @Param("classroomIds") Collection<Long> classroomIds,
            @Param("date") LocalDate date,
            @Param("pattern") String pattern,
            Pageable pageable);
}
