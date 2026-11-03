package com.carenest.repository.meals;

import com.carenest.entity.meals.Food;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, Long> {

    // Danh mục vài trăm dòng: lấy hết kèm chất dị ứng một lần, lọc ở service
    @EntityGraph(attributePaths = "allergens")
    List<Food> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "allergens")
    Optional<Food> findWithAllergensById(Long id);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Food> findByCodeIn(Collection<String> codes);
}
