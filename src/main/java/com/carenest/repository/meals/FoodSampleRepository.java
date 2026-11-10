package com.carenest.repository.meals;

import com.carenest.entity.meals.FoodSample;
import com.carenest.utils.enums.FoodSampleDisposalStatus;
import com.carenest.utils.enums.FoodSampleStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodSampleRepository extends JpaRepository<FoodSample, Long> {

    List<FoodSample> findByCampusIdAndSampleDateBetweenOrderBySampleDateDescSampledAtDescIdDesc(
            Long campusId, LocalDate from, LocalDate to);

    boolean existsByCampusIdAndSampleDate(Long campusId, LocalDate sampleDate);

    long countByCampusIdAndStatus(Long campusId, FoodSampleStatus status);

    long countByCampusIdAndDisposalStatus(Long campusId, FoodSampleDisposalStatus disposalStatus);

    // Bảng campuses thuộc module organization, chỉ đọc: kiểm tồn tại để báo 404 thay vì trả rỗng
    @Query(value = "select count(*) > 0 from campuses where id = :campusId", nativeQuery = true)
    boolean campusExists(@Param("campusId") Long campusId);
}
