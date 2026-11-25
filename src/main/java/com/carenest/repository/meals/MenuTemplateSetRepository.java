package com.carenest.repository.meals;

import com.carenest.entity.meals.MenuTemplateSet;
import com.carenest.utils.enums.CatalogStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuTemplateSetRepository extends JpaRepository<MenuTemplateSet, Long> {

    @EntityGraph(attributePaths = "items")
    List<MenuTemplateSet> findAllByOrderByMenuBlockAscNameAsc();

    @EntityGraph(attributePaths = "items")
    Optional<MenuTemplateSet> findWithItemsById(Long id);

    // Thực đơn mẫu đang dùng có chứa món: hộp xác nhận ngừng dùng món liệt kê nơi đang dùng
    @Query("select distinct s from MenuTemplateSet s join s.items i"
            + " where i.dishId = :dishId and s.status = :status order by s.name")
    List<MenuTemplateSet> findUsingDish(@Param("dishId") Long dishId, @Param("status") CatalogStatus status);
}
