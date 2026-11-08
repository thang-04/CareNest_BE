package com.carenest.repository.meals;

import com.carenest.entity.meals.Allergen;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergenRepository extends JpaRepository<Allergen, Long> {

    List<Allergen> findAllByOrderBySortOrderAsc();
}
