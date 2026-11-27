package com.carenest.repository.organization;

import com.carenest.entity.organization.Campus;
import com.carenest.utils.enums.CampusStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // So tên đầy đủ không phân biệt hoa thường, coi mọi dấu gạch (-, –, —) là một và bỏ khoảng trắng quanh dấu gạch
    @Query(
            value = "select count(*) > 0 from campuses c where c.id <> :id and "
                    + "lower(regexp_replace(c.name, '\\s*[-‐‑‒–—−]\\s*', '-', 'g'))"
                    + " = lower(regexp_replace(:name, '\\s*[-‐‑‒–—−]\\s*', '-', 'g'))",
            nativeQuery = true)
    boolean existsByNameFoldedAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Query(
            value = "select count(*) > 0 from campuses c where "
                    + "lower(regexp_replace(c.name, '\\s*[-‐‑‒–—−]\\s*', '-', 'g'))"
                    + " = lower(regexp_replace(:name, '\\s*[-‐‑‒–—−]\\s*', '-', 'g'))",
            nativeQuery = true)
    boolean existsByNameFolded(@Param("name") String name);

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
                      and (a.valid_to is not null or a.staff_role <> 'VICE_PRINCIPAL'
                           or not exists (select 1 from school_years y
                                          where y.end_date >= a.valid_from and y.end_date < :date))
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

    // Điểm trường của từng năm học (V151): danh mục chung giữ mã, mỗi năm có danh sách riêng (Cần chốt 22/10/2026)
    @Query(
            value = "select campus_id from school_year_campus_members where school_year_id = :yearId",
            nativeQuery = true)
    List<Long> findYearCampusIds(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value =
                    "insert into school_year_campus_members (school_year_id, campus_id, code, name, short_name, address,"
                            + " phone, status) select :yearId, c.id, c.code, c.name, c.short_name, c.address, c.phone,"
                            + " c.status from campuses c where c.id = :campusId on conflict do nothing",
            nativeQuery = true)
    void addToYear(@Param("yearId") Long yearId, @Param("campusId") Long campusId);

    @Modifying
    @Query(
            value =
                    "insert into school_year_campus_members (school_year_id, campus_id, code, name, short_name, address,"
                            + " phone, status, is_main) select :targetYearId, m.campus_id, coalesce(m.code, c.code),"
                            + " coalesce(m.name, c.name), coalesce(m.short_name, c.short_name),"
                            + " coalesce(m.address, c.address), coalesce(m.phone, c.phone), coalesce(m.status, c.status),"
                            + " m.is_main"
                            + " from school_year_campus_members m join campuses c on c.id = m.campus_id"
                            + " where m.school_year_id = :sourceYearId on conflict do nothing",
            nativeQuery = true)
    int copyYearCampuses(@Param("sourceYearId") Long sourceYearId, @Param("targetYearId") Long targetYearId);

    @Modifying
    @Query(
            value =
                    "insert into school_year_campus_members (school_year_id, campus_id, code, name, short_name, address,"
                            + " phone, status, is_main) select :yearId, c.id, c.code, c.name, c.short_name, c.address,"
                            + " c.phone, c.status, c.is_main from campuses c on conflict do nothing",
            nativeQuery = true)
    void addAllToYear(@Param("yearId") Long yearId);

    /** Thông tin điểm trường riêng của năm (V152, V154 bảo đảm các cột bắt buộc không null). */
    @Query(
            value = "select campus_id as campusId, code, name, short_name as shortName, address, phone, status,"
                    + " is_main as main from school_year_campus_members where school_year_id = :yearId",
            nativeQuery = true)
    List<YearCampusInfo> findYearCampusInfo(@Param("yearId") Long yearId);

    interface YearCampusInfo {
        Long getCampusId();

        String getCode();

        String getName();

        String getShortName();

        String getAddress();

        String getPhone();

        String getStatus();

        Boolean getMain();
    }

    /** Điểm trường chính của năm (V180); rỗng khi năm chưa có. */
    @Query(
            value = "select campus_id from school_year_campus_members where school_year_id = :yearId and is_main",
            nativeQuery = true)
    Optional<Long> findYearMainCampusId(@Param("yearId") Long yearId);

    // Hai câu riêng: bỏ điểm chính cũ trước rồi mới đặt điểm mới (chỉ mục unique kiểm từng dòng)
    @Modifying
    @Query(
            value = "update school_year_campus_members set is_main = false"
                    + " where school_year_id = :yearId and is_main and campus_id <> :campusId",
            nativeQuery = true)
    int clearYearMain(@Param("yearId") Long yearId, @Param("campusId") Long campusId);

    @Modifying
    @Query(
            value = "update school_year_campus_members set is_main = true"
                    + " where school_year_id = :yearId and campus_id = :campusId",
            nativeQuery = true)
    int markYearMain(@Param("yearId") Long yearId, @Param("campusId") Long campusId);

    /** Kích hoạt năm: bỏ cờ chính ở bảng chung của điểm trường không phải điểm chính năm mới (bước 1/2). */
    @Modifying
    @Query(
            value = "update campuses c set is_main = false, updated_at = now() where c.is_main"
                    + " and exists (select 1 from school_year_campus_members m"
                    + " where m.school_year_id = :yearId and m.is_main and m.campus_id <> c.id)",
            nativeQuery = true)
    int clearMainOutsideYear(@Param("yearId") Long yearId);

    /** Kích hoạt năm: bảng chung nhận điểm trường chính của năm mới (bước 2/2). */
    @Modifying
    @Query(
            value = "update campuses c set is_main = true, updated_at = now() where not c.is_main"
                    + " and exists (select 1 from school_year_campus_members m"
                    + " where m.school_year_id = :yearId and m.is_main and m.campus_id = c.id)",
            nativeQuery = true)
    int applyYearMain(@Param("yearId") Long yearId);

    /** Điểm trường có mặt và đang hoạt động trong đúng năm học. */
    @Query(
            value = "select count(*) > 0 from school_year_campus_members m"
                    + " where m.school_year_id = :yearId and m.campus_id = :campusId and m.status = 'ACTIVE'",
            nativeQuery = true)
    Boolean isActiveInYear(@Param("yearId") Long yearId, @Param("campusId") Long campusId);

    /** Trạng thái hoạt động / ngừng của điểm trường trong năm (V154). */
    @Modifying
    @Query(
            value = "update school_year_campus_members set status = :status"
                    + " where school_year_id = :yearId and campus_id = :campusId",
            nativeQuery = true)
    int updateYearStatus(
            @Param("yearId") Long yearId, @Param("campusId") Long campusId, @Param("status") String status);

    /** Kích hoạt năm: bảng chung nhận thông tin, trạng thái điểm trường của năm mới (module khác đọc bảng chung). */
    @Modifying
    @Query(
            value = "update campuses c set code = coalesce(m.code, c.code), name = coalesce(m.name, c.name),"
                    + " short_name = coalesce(m.short_name, c.short_name), address = coalesce(m.address, c.address),"
                    + " phone = coalesce(m.phone, c.phone), status = coalesce(m.status, c.status),"
                    + " deactivated_at = case when coalesce(m.status, c.status) = 'INACTIVE'"
                    + " then coalesce(c.deactivated_at, now()) else null end, updated_at = now()"
                    + " from school_year_campus_members m where m.campus_id = c.id and m.school_year_id = :yearId",
            nativeQuery = true)
    int applyYearInfo(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value = "update campuses c set status = 'INACTIVE', deactivated_at = coalesce(c.deactivated_at, now()),"
                    + " updated_at = now() where c.status = 'ACTIVE'"
                    + " and exists (select 1 from school_year_campus_members where school_year_id = :yearId)"
                    + " and not exists (select 1 from school_year_campus_members m"
                    + " where m.campus_id = c.id and m.school_year_id = :yearId)",
            nativeQuery = true)
    int deactivateOutsideYear(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value = "update school_year_campus_members set code = :code, name = :name, short_name = :shortName,"
                    + " address = :address, phone = :phone where school_year_id = :yearId and campus_id = :campusId",
            nativeQuery = true)
    int updateYearInfo(
            @Param("yearId") Long yearId,
            @Param("campusId") Long campusId,
            @Param("code") String code,
            @Param("name") String name,
            @Param("shortName") String shortName,
            @Param("address") String address,
            @Param("phone") String phone);

    // Cần chốt 22/10/2026: mã, tên điểm trường không trùng trong cùng năm học (không phân biệt hoa thường)
    @Query(
            value = "select count(*) > 0 from school_year_campus_members m"
                    + " where m.school_year_id = :yearId and m.campus_id <> :campusId"
                    + " and lower(m.code) = lower(:code)",
            nativeQuery = true)
    boolean existsCodeInYear(
            @Param("yearId") Long yearId, @Param("campusId") Long campusId, @Param("code") String code);

    @Query(
            value = "select count(*) > 0 from school_year_campus_members m"
                    + " where m.school_year_id = :yearId and m.campus_id <> :campusId and "
                    + "lower(regexp_replace(m.name, '\\s*[-‐‑‒–—−]\\s*', '-', 'g'))"
                    + " = lower(regexp_replace(:name, '\\s*[-‐‑‒–—−]\\s*', '-', 'g'))",
            nativeQuery = true)
    boolean existsNameInYear(
            @Param("yearId") Long yearId, @Param("campusId") Long campusId, @Param("name") String name);

    // Cần chốt 24/11/2026: tên ngắn không trùng như mã, tên; so sánh sau khi bỏ khoảng trắng thừa, không phân biệt hoa
    // thường (:shortName đã chuẩn hóa)
    @Query(
            value = "select count(*) > 0 from school_year_campus_members m"
                    + " where m.school_year_id = :yearId and m.campus_id <> :campusId"
                    + " and lower(regexp_replace(trim(m.short_name), '\\s+', ' ', 'g')) = lower(:shortName)",
            nativeQuery = true)
    boolean existsShortNameInYear(
            @Param("yearId") Long yearId, @Param("campusId") Long campusId, @Param("shortName") String shortName);

    /** Tên ngắn trùng trong danh mục chung, trừ điểm trường excludeId (tạo mới truyền 0). */
    @Query(
            value = "select count(*) > 0 from campuses c where c.id <> :excludeId"
                    + " and lower(regexp_replace(trim(c.short_name), '\\s+', ' ', 'g')) = lower(:shortName)",
            nativeQuery = true)
    boolean existsShortName(@Param("shortName") String shortName, @Param("excludeId") Long excludeId);

    @Modifying
    @Query(
            value = "delete from school_year_campus_members where school_year_id = :yearId and campus_id = :campusId",
            nativeQuery = true)
    void removeFromYear(@Param("yearId") Long yearId, @Param("campusId") Long campusId);

    @Query(value = "select count(*) from school_year_campus_members where campus_id = :campusId", nativeQuery = true)
    long countYears(@Param("campusId") Long campusId);

    @Query(
            value = "select count(*) from classrooms where campus_id = :campusId and school_year_id = :yearId",
            nativeQuery = true)
    long countClassesInYear(@Param("campusId") Long campusId, @Param("yearId") Long yearId);

    /** PHT phân công cho điểm trường trong khoảng của năm học (V144: phân công PHT theo năm). */
    @Query(
            value = "select distinct u.full_name from staff_assignments s join user_accounts u on u.id = s.user_id"
                    + " join school_years y on y.id = :yearId where s.campus_id = :campusId"
                    + " and s.staff_role = 'VICE_PRINCIPAL' and s.valid_from <= y.end_date"
                    + " and (s.valid_to is null or s.valid_to >= y.start_date) order by 1",
            nativeQuery = true)
    List<String> findYearVicePrincipalNames(@Param("campusId") Long campusId, @Param("yearId") Long yearId);

    /** Trẻ gắn với điểm trường ở bất kỳ năm nào: đăng ký vào, từng học lớp, hoặc đã xếp cho năm sau. */
    @Query(
            value = "select count(*) from (select id from children where intake_campus_id = :campusId"
                    + " union select e.child_id from enrollments e join classrooms c on c.id = e.classroom_id"
                    + " where c.campus_id = :campusId"
                    + " union select child_id from planned_admissions where campus_id = :campusId) t",
            nativeQuery = true)
    long countChildrenEver(@Param("campusId") Long campusId);

    /** Nhân sự từng/đang phân công ở điểm trường (cả lịch sử), kèm vai trò. */
    @Query(
            value = "select distinct u.full_name as fullName, s.staff_role as staffRole from staff_assignments s"
                    + " join user_accounts u on u.id = s.user_id where s.campus_id = :campusId"
                    + " order by s.staff_role, u.full_name",
            nativeQuery = true)
    List<StaffName> findStaffNamesEver(@Param("campusId") Long campusId);

    /** Trẻ đã tiếp nhận cho năm học ở điểm trường, chưa hủy / chưa kích hoạt (họ tên chỉ để báo lý do chặn). */
    @Query(
            value = "select ch.full_name from planned_admissions p join children ch on ch.id = p.child_id"
                    + " where p.campus_id = :campusId and p.school_year_id = :yearId"
                    + " and p.status in ('PENDING', 'PLACED') order by ch.full_name",
            nativeQuery = true)
    List<String> findYearAdmissionNames(@Param("campusId") Long campusId, @Param("yearId") Long yearId);

    /** Nhân sự phân công ở điểm trường trong khoảng của năm học (theo điểm trường hoặc theo lớp của điểm trường). */
    @Query(
            value = "select distinct u.full_name as fullName, s.staff_role as staffRole from staff_assignments s"
                    + " join user_accounts u on u.id = s.user_id join school_years y on y.id = :yearId"
                    + " left join classrooms c on c.id = s.classroom_id"
                    + " where coalesce(s.campus_id, c.campus_id) = :campusId"
                    + " and (s.school_year_id = :yearId or (s.school_year_id is null"
                    + " and s.valid_from <= y.end_date and (s.valid_to is null or s.valid_to >= y.start_date)))"
                    + " order by s.staff_role, u.full_name",
            nativeQuery = true)
    List<StaffName> findYearStaffNames(@Param("campusId") Long campusId, @Param("yearId") Long yearId);

    @Query(
            value = "select count(*) from school_year_room_members"
                    + " where campus_id = :campusId and school_year_id = :yearId",
            nativeQuery = true)
    long countYearRooms(@Param("campusId") Long campusId, @Param("yearId") Long yearId);

    /** Sổ suất ăn và dữ liệu kho bếp của điểm trường trong khoảng của năm học. */
    @Query(
            value = "select (select count(*) from meal_counts where campus_id = :campusId and school_year_id = :yearId)"
                    + " + (select count(*) from stock_movements m join school_years y on y.id = :yearId"
                    + " where m.campus_id = :campusId and m.meal_date between y.start_date and y.end_date)"
                    + " + (select count(*) from stock_daily_demands d join school_years y on y.id = :yearId"
                    + " where d.campus_id = :campusId and d.meal_date between y.start_date and y.end_date)",
            nativeQuery = true)
    long countYearKitchenData(@Param("campusId") Long campusId, @Param("yearId") Long yearId);

    interface StaffName {
        String getFullName();

        String getStaffRole();
    }

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
