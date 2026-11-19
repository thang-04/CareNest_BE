package com.carenest.repository.meals;

import com.carenest.entity.meals.InspectionSheet;
import com.carenest.utils.enums.InspectionTab;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionSheetRepository extends JpaRepository<InspectionSheet, Long> {

    List<InspectionSheet> findByCampusIdAndSheetDate(Long campusId, LocalDate sheetDate);

    Optional<InspectionSheet> findByCampusIdAndSheetDateAndTab(Long campusId, LocalDate sheetDate, InspectionTab tab);

    /**
     * Khóa dòng điểm trường tới hết transaction: hai người cùng lưu, ký hoặc sửa lại một mẫu chạy tuần tự, người sau
     * thấy trạng thái mới (kể cả khi biên bản chưa có dòng để khóa).
     */
    @Query(value = "select id from campuses where id = :campusId for update", nativeQuery = true)
    Long lockCampus(@Param("campusId") Long campusId);

    /** Biên bản chứa ảnh chữ ký: để người được xem biên bản tải ảnh (INS-03). */
    @Query(
            value = "select count(*) > 0 from inspection_sheets s where s.campus_id = :campusId"
                    + " and (s.signature ->> 'imageKey' = :imageKey"
                    + " or s.history @> cast(:historyProbe as jsonb))",
            nativeQuery = true)
    boolean existsWithImage(
            @Param("campusId") Long campusId,
            @Param("imageKey") String imageKey,
            @Param("historyProbe") String historyProbe);
}
