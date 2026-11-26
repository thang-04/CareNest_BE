package com.carenest.repository.meals;

import com.carenest.entity.meals.Food;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodRepository extends JpaRepository<Food, Long> {

    // Danh mục vài trăm dòng: lấy hết một lần, lọc ở service
    List<Food> findAllByOrderByNameAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Food> findByCodeIn(Collection<String> codes);

    // NUT-32: đã có dòng phiếu nhập kho (kể cả nháp, đã hủy) hoặc dòng kiểm nhận hàng tươi
    @Query(
            value = "SELECT food_id FROM stock_receipt_lines WHERE food_id IN (:ids)"
                    + " UNION SELECT food_id FROM fresh_check_lines WHERE food_id IN (:ids)",
            nativeQuery = true)
    List<Long> findIdsWithStockHistory(@Param("ids") Collection<Long> ids);
}
