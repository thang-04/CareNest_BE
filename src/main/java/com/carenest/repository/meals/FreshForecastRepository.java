package com.carenest.repository.meals;

import com.carenest.entity.meals.FreshForecast;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreshForecastRepository extends JpaRepository<FreshForecast, Long> {

    @EntityGraph(attributePaths = "lines")
    Optional<FreshForecast> findWithLinesById(Long id);

    List<FreshForecast> findByCampusIdOrderByCreatedAtDescIdDesc(Long campusId);

    /** Các bản có kỳ chứa ngày, mới lập nhất trước (NUT-12: lần 1 kiểm theo bản đầu tiên). */
    @Query("select f from FreshForecast f where f.campusId = :campusId and f.periodFrom <= :date"
            + " and f.periodTo >= :date order by f.createdAt desc, f.id desc")
    List<FreshForecast> findCovering(@Param("campusId") Long campusId, @Param("date") LocalDate date);

    @Query("select l.forecast.id as forecastId, count(distinct l.foodId) as foodCount from FreshForecastLine l"
            + " where l.forecast.id in :forecastIds group by l.forecast.id")
    List<FoodCount> countFoods(@Param("forecastIds") Collection<Long> forecastIds);

    // Bảng campuses thuộc module organization, chỉ đọc: ghi dự trù, kiểm nhận cho điểm trường không có ⇒ báo 404 thay
    // vì lỗi khóa ngoại
    @Query(value = "select count(*) > 0 from campuses where id = :campusId", nativeQuery = true)
    boolean campusExists(@Param("campusId") Long campusId);

    interface FoodCount {
        Long getForecastId();

        long getFoodCount();
    }
}
