package com.carenest.repository.meals;

import com.carenest.entity.meals.Dish;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DishRepository extends JpaRepository<Dish, Long> {

    // Danh sách kèm định lượng, thực phẩm, dị ứng trong một truy vấn: FE lập thực đơn cần đủ để tính, tránh 1 + N
    @EntityGraph(attributePaths = {"ingredients", "ingredients.food", "ingredients.food.allergens"})
    List<Dish> findAllByOrderByNameAsc();

    // Chi tiết món cần thực phẩm và chất dị ứng của từng ô định lượng để tính dinh dưỡng, giá
    @EntityGraph(attributePaths = {"ingredients", "ingredients.food", "ingredients.food.allergens"})
    Optional<Dish> findWithIngredientsById(Long id);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
