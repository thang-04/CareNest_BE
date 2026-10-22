package com.carenest.repository.child;

import com.carenest.entity.child.ClassPlacement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassPlacementRepository extends JpaRepository<ClassPlacement, Long> {

    List<ClassPlacement> findAllBySchoolYearId(Long schoolYearId);
}
