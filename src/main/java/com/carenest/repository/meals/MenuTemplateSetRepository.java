package com.carenest.repository.meals;

import com.carenest.entity.meals.MenuTemplateSet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuTemplateSetRepository extends JpaRepository<MenuTemplateSet, Long> {

    @EntityGraph(attributePaths = "items")
    List<MenuTemplateSet> findAllByOrderByMenuBlockAscNameAsc();

    @EntityGraph(attributePaths = "items")
    Optional<MenuTemplateSet> findWithItemsById(Long id);
}
