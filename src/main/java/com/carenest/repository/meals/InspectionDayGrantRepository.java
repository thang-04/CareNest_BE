package com.carenest.repository.meals;

import com.carenest.entity.meals.InspectionDayGrant;
import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionTab;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionDayGrantRepository extends JpaRepository<InspectionDayGrant, Long> {

    /** Dòng đang mở (PENDING/APPROVED) của phạm vi; chỉ có tối đa một nhờ unique index từng phần. */
    Optional<InspectionDayGrant> findByCampusIdAndSheetDateAndTabAndStatusIn(
            Long campusId, LocalDate sheetDate, InspectionTab tab, Collection<InspectionGrantStatus> statuses);

    /** Mọi dòng của điểm trường–ngày, mới nhất trước: mỗi mẫu lấy dòng đầu để hiện trạng thái. */
    List<InspectionDayGrant> findByCampusIdAndSheetDateOrderByIdDesc(Long campusId, LocalDate sheetDate);

    /** Chỉ lấy điểm trường (không nạp entity) để khóa điểm trường trước rồi mới đọc dòng, tránh đọc trạng thái cũ. */
    @Query("select g.campusId from InspectionDayGrant g where g.id = :id")
    Optional<Long> findCampusId(@Param("id") Long id);

    /** Hàng chờ của HT; {@code campusId} rỗng = mọi điểm trường. */
    @Query("select g from InspectionDayGrant g where g.status = :status"
            + " and (:campusId is null or g.campusId = :campusId) order by g.requestedAt asc, g.id asc")
    List<InspectionDayGrant> findQueue(
            @Param("status") InspectionGrantStatus status, @Param("campusId") Long campusId, Pageable pageable);
}
