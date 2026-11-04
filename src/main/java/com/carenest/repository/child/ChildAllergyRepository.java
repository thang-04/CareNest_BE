package com.carenest.repository.child;

import com.carenest.entity.child.ChildAllergy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildAllergyRepository extends JpaRepository<ChildAllergy, Long> {

    List<ChildAllergy> findAllByChildIdOrderByIdAsc(Long childId);
}
