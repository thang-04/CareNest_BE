package com.carenest.repository.meals;

import com.carenest.entity.meals.FoodPrice;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodPriceRepository extends JpaRepository<FoodPrice, Long> {

    List<FoodPrice> findByFoodIdInOrderByFoodIdAscEffectiveFromAsc(Collection<Long> foodIds);

    List<FoodPrice> findAllByOrderByFoodIdAscEffectiveFromAsc();

    List<FoodPrice> findByFoodIdOrderByEffectiveFromDesc(Long foodId);

    boolean existsByFoodIdAndEffectiveFrom(Long foodId, LocalDate effectiveFrom);

    boolean existsByFoodIdAndEffectiveFromAndIdNot(Long foodId, LocalDate effectiveFrom, Long id);

    List<FoodPrice> findBySupplierId(Long supplierId);
}
