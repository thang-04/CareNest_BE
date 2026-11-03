package com.carenest.repository.meals;

import com.carenest.entity.meals.FreshCheck;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreshCheckRepository extends JpaRepository<FreshCheck, Long> {

    // Tối đa 2 lần kiểm/ngày, nạp kèm dòng để dựng cả hai lần một lượt
    @EntityGraph(attributePaths = "lines")
    List<FreshCheck> findByCampusIdAndCheckDate(Long campusId, LocalDate checkDate);

    @EntityGraph(attributePaths = "lines")
    Optional<FreshCheck> findByCampusIdAndCheckDateAndRound(Long campusId, LocalDate checkDate, short round);

    /**
     * Khóa dòng điểm trường tới hết transaction: ghi kiểm nhận cùng (điểm trường, ngày, lần) chạy tuần tự nên bấm
     * lưu hai lần không đụng khóa duy nhất.
     */
    @Query(value = "select id from campuses where id = :campusId for update", nativeQuery = true)
    Long lockCampus(@Param("campusId") Long campusId);
}
