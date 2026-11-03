package com.carenest.repository.meals;

import com.carenest.entity.meals.Supplier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByOrderByNameAsc();
}
