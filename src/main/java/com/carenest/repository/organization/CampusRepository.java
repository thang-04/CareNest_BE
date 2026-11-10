package com.carenest.repository.organization;

import com.carenest.entity.organization.Campus;
import com.carenest.utils.enums.CampusStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusRepository extends JpaRepository<Campus, Long> {

    List<Campus> findAllByOrderByMainDescCodeAsc();

    boolean existsByIdAndStatus(Long id, CampusStatus status);

    // Lớp của năm đang hoạt động hoặc sắp diễn ra ở điểm trường: chặn ngừng hoạt động
    @Query("select count(c) from Classroom c, SchoolYear y where y.id = c.schoolYearId and c.campusId = :campusId"
            + " and y.status <> com.carenest.utils.enums.SchoolYearStatus.CLOSED")
    long countOpenYearClasses(@Param("campusId") Long campusId);

    List<Campus> findAllByIdInOrderByMainDescCodeAsc(Collection<Long> ids);

    Optional<Campus> findFirstByMainTrue();

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByNameIgnoreCase(String name);

    // Số liệu chỉ đọc cho màn điểm trường: trẻ đang học (enrollment hiệu lực hôm nay) theo lớp của năm học
    @Query(value = """
                    select c.campus_id as campusId, count(distinct e.child_id) as total
                    from classrooms c
                    join enrollments e on e.classroom_id = c.id
                    where c.school_year_id = :schoolYearId
                      and e.valid_from <= :date and (e.valid_to is null or e.valid_to >= :date)
                    group by c.campus_id
                    """, nativeQuery = true)
    List<CampusCount> countChildrenByCampus(@Param("schoolYearId") Long schoolYearId, @Param("date") LocalDate date);

    @Query(value = """
                    select c.campus_id as campusId, count(*) as total
                    from classrooms c
                    where c.school_year_id = :schoolYearId
                    group by c.campus_id
                    """, nativeQuery = true)
    List<CampusCount> countClassesByCampus(@Param("schoolYearId") Long schoolYearId);

    // Họ tên nhân sự (bảng user_accounts thuộc identity-access) chỉ đọc để hiển thị, không ghi
    @Query(value = """
                    select a.campus_id as campusId, u.id as userId, u.full_name as fullName, a.staff_role as staffRole
                    from staff_assignments a
                    join user_accounts u on u.id = a.user_id
                    where a.campus_id is not null
                      and a.valid_from <= :date and (a.valid_to is null or a.valid_to >= :date)
                    order by u.full_name
                    """, nativeQuery = true)
    List<CampusStaff> findActiveCampusStaff(@Param("date") LocalDate date);

    @Query(value = """
                    select r.campus_id as campusId, u.id as userId, u.full_name as fullName, a.staff_role as staffRole
                    from staff_assignments a
                    join classrooms r on r.id = a.classroom_id
                    join user_accounts u on u.id = a.user_id
                    where r.campus_id = :campusId and r.school_year_id = :schoolYearId
                      and a.valid_from <= :date and (a.valid_to is null or a.valid_to >= :date)
                    order by u.full_name
                    """, nativeQuery = true)
    List<CampusStaff> findActiveClassroomStaff(
            @Param("campusId") Long campusId, @Param("schoolYearId") Long schoolYearId, @Param("date") LocalDate date);

    @Query(value = "select count(*) from staff_assignments where campus_id = :campusId", nativeQuery = true)
    long countStaffAssignments(@Param("campusId") Long campusId);

    interface CampusCount {
        Long getCampusId();

        long getTotal();
    }

    interface CampusStaff {
        Long getCampusId();

        Long getUserId();

        String getFullName();

        String getStaffRole();
    }
}
