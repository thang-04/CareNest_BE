package com.carenest.repository.meals;

import com.carenest.entity.meals.Food;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, Long> {

    // Danh mục vài trăm dòng: lấy hết một lần, lọc ở service
    List<Food> findAllByOrderByNameAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Food> findByCodeIn(Collection<String> codes);
}
