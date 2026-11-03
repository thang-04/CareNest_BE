package com.carenest.repository.meals;

import com.carenest.entity.meals.MealPhoto;
import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealPhotoRepository extends JpaRepository<MealPhoto, Long> {

    Optional<MealPhoto> findByCampusIdAndPhotoDateAndSession(Long campusId, LocalDate photoDate, MealSession session);

    List<MealPhoto> findByCampusIdAndPhotoDateOrderBySessionAsc(Long campusId, LocalDate photoDate);

    List<MealPhoto> findByCampusIdAndPhotoDateBetweenOrderByPhotoDateAscSessionAsc(
            Long campusId, LocalDate from, LocalDate to);

    /** Khóa dòng điểm trường tới hết transaction: đăng ảnh cùng (điểm trường, ngày, bữa) chạy tuần tự. */
    @Query(value = "select id from campuses where id = :campusId for update", nativeQuery = true)
    Long lockCampus(@Param("campusId") Long campusId);
}
