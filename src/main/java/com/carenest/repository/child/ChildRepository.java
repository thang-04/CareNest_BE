package com.carenest.repository.child;

import com.carenest.entity.child.Child;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildRepository extends JpaRepository<Child, Long> {

    /**
     * Danh sách trẻ (D-4, G94-180/CHD-09): id trẻ và lớp hiển thị, đã lọc phạm vi và bộ lọc, sắp theo tên lớp (trẻ chưa có
     * lớp trước) rồi họ tên. Lớp hiển thị: có {@code yearId} ⇒ lớp cuối của trẻ trong năm học đó; không có ⇒ lớp đang học
     * hoặc lớp cuối của trẻ đã nghỉ; trẻ chờ xếp lớp không có lớp. Có năm học: trẻ không có lớp trong năm đó chỉ hiện khi
     * chờ xếp lớp và năm đang hoạt động, hoặc đã nghỉ và ngày nghỉ thuộc năm đó (năm gần nhất đã bắt đầu tính tới ngày
     * nghỉ). Điểm trường hiển thị = điểm trường của lớp, không có lớp ⇒ điểm trường tiếp nhận (CMR-04).
     *
     * <p>G94-180/CHD-14: lớp hiển thị ưu tiên lớp đã bắt đầu tới :today (lịch chuyển chưa tới ngày không đổi lớp
     * hiển thị); trẻ chưa có lớp nào bắt đầu thì là lớp sắp vào. Trẻ chờ xếp lớp (mới tiếp nhận hoặc mở lại hồ sơ) không
     * mang lớp đã đóng trước :today (kể cả khi có {@code yearId}); chỉ giữ lớp chưa đóng hoặc lớp dự kiến sắp vào.
     *
     * <p>Phạm vi: {@code wholeSchool}, hoặc điểm trường hiển thị thuộc {@code campusIds}, hoặc lớp hiển thị thuộc
     * {@code classIds}. Tập rỗng truyền {@code [-1]} (IN () không hợp lệ).
     */
    @Query(value = """
                    SELECT c.id AS childId, yc.classroom_id AS classroomId
                    FROM children c
                    LEFT JOIN LATERAL (
                        SELECT e.classroom_id
                        FROM enrollments e
                        JOIN classrooms k ON k.id = e.classroom_id
                        WHERE e.child_id = c.id
                          AND CASE WHEN CAST(:yearId AS BIGINT) IS NULL
                                   THEN c.status <> 'PENDING_PLACEMENT'
                                   ELSE k.school_year_id = CAST(:yearId AS BIGINT) END
                          AND (c.status <> 'PENDING_PLACEMENT' OR e.valid_to IS NULL OR e.valid_to >= :today)
                        ORDER BY CASE WHEN e.valid_from <= :today THEN 0 ELSE 1 END, e.valid_from DESC, e.id DESC
                        LIMIT 1
                    ) yc ON TRUE
                    LEFT JOIN classrooms cl ON cl.id = yc.classroom_id
                    WHERE c.status IN (:statuses)
                      AND (CAST(:yearId AS BIGINT) IS NULL
                           OR yc.classroom_id IS NOT NULL
                           OR (c.status = 'PENDING_PLACEMENT' AND EXISTS (
                                SELECT 1 FROM school_years y
                                WHERE y.id = CAST(:yearId AS BIGINT) AND y.status = 'ACTIVE')
                                AND NOT EXISTS (
                                SELECT 1 FROM planned_admissions pa
                                WHERE pa.child_id = c.id AND pa.status IN ('PENDING', 'PLACED')))
                           OR (c.status = 'PENDING_PLACEMENT' AND EXISTS (
                                SELECT 1 FROM planned_admissions pa
                                WHERE pa.child_id = c.id AND pa.school_year_id = CAST(:yearId AS BIGINT)
                                  AND pa.status IN ('PENDING', 'PLACED')))
                           OR (c.status = 'LEFT' AND (
                                SELECT y.id FROM school_years y
                                WHERE y.start_date <= c.left_on
                                ORDER BY y.start_date DESC, y.id DESC
                                LIMIT 1) = CAST(:yearId AS BIGINT)))
                      AND (:wholeSchool
                           OR COALESCE(cl.campus_id, c.intake_campus_id) IN (:campusIds)
                           OR yc.classroom_id IN (:classIds))
                      AND (CAST(:campusId AS BIGINT) IS NULL
                           OR COALESCE(cl.campus_id, c.intake_campus_id) = CAST(:campusId AS BIGINT))
                      AND (CAST(:classId AS BIGINT) IS NULL OR yc.classroom_id = CAST(:classId AS BIGINT))
                      AND (:pattern = '' OR lower(c.full_name) LIKE :pattern OR lower(c.code) LIKE :pattern)
                    ORDER BY cl.name NULLS FIRST, c.full_name, c.id
                    """, nativeQuery = true)
    List<ChildListRow> findListRows(
            @Param("yearId") Long yearId,
            @Param("today") LocalDate today,
            @Param("statuses") Collection<String> statuses,
            @Param("wholeSchool") boolean wholeSchool,
            @Param("campusIds") Collection<Long> campusIds,
            @Param("classIds") Collection<Long> classIds,
            @Param("campusId") Long campusId,
            @Param("classId") Long classId,
            @Param("pattern") String pattern);

    // Ứng viên trùng hồ sơ (CHD-04): cùng ngày sinh và có phụ huynh mang một trong các CCCD; họ tên so ở service
    @Query("""
            select c from Child c
            where c.dateOfBirth = :dateOfBirth
              and (:excludeId is null or c.id <> :excludeId)
              and exists (select 1 from ChildGuardian g where g.childId = c.id and g.citizenId in :citizenIds)
            order by c.id
            """)
    List<Child> findDuplicateCandidates(
            @Param("dateOfBirth") LocalDate dateOfBirth,
            @Param("citizenIds") Collection<String> citizenIds,
            @Param("excludeId") Long excludeId);

    // CHD-13: trẻ không báo ăn trong danh sách id
    @Query("select c.id from Child c where c.id in :childIds and c.mealRegistered = false")
    List<Long> findIdsWithoutMeals(@Param("childIds") Collection<Long> childIds);

    // G94-180/CHD-03: trẻ đã cho nghỉ, nghỉ từ đúng ngày :date (cho nghỉ từ ngày học kế tiếp) ⇒ khóa phụ huynh ngày đó
    @Query("select c.id from Child c where c.status = com.carenest.utils.enums.ChildStatus.LEFT and c.leftOn = :date"
            + " order by c.id")
    List<Long> findIdsLeavingOn(@Param("date") LocalDate date);

    /** Một dòng danh sách trẻ: trẻ và lớp hiển thị (null khi không có lớp). */
    interface ChildListRow {
        Long getChildId();

        Long getClassroomId();
    }
}
