package com.carenest.repository.child;

import com.carenest.entity.child.ChildAllergy;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildAllergyRepository extends JpaRepository<ChildAllergy, Long> {

    List<ChildAllergy> findAllByChildIdOrderByIdAsc(Long childId);

    @Query("select distinct a.childId from ChildAllergy a where a.childId in :childIds")
    List<Long> findChildIdsWithAllergy(@Param("childIds") Collection<Long> childIds);
}
