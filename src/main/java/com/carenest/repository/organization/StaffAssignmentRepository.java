package com.carenest.repository.organization;

import com.carenest.entity.organization.StaffAssignment;
import com.carenest.entity.organization.StaffRole;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Mọi query lọc theo ngày: chỉ phân công có hiệu lực tại {@code date} mới tạo phạm vi. */
public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {

    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.userId = :userId and a.staffRole = :staffRole
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            """)
    boolean existsActiveByRole(@Param("userId") Long userId, @Param("staffRole") StaffRole staffRole,
                               @Param("date") LocalDate date);

    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.userId = :userId and a.staffRole = :staffRole and a.campusId = :campusId
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            """)
    boolean existsActiveForCampus(@Param("userId") Long userId, @Param("staffRole") StaffRole staffRole,
                                  @Param("campusId") Long campusId, @Param("date") LocalDate date);

    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.userId = :userId and a.staffRole = com.carenest.entity.organization.StaffRole.TEACHER
              and a.classroomId = :classroomId
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            """)
    boolean existsActiveTeacherForClassroom(@Param("userId") Long userId, @Param("classroomId") Long classroomId,
                                            @Param("date") LocalDate date);

    @Query("select a from StaffAssignment a where a.userId = :userId"
            + " and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date) order by a.staffRole, a.id")
    List<StaffAssignment> findActiveByUserId(@Param("userId") Long userId, @Param("date") LocalDate date);
}
