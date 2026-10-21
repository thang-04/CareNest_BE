package com.carenest.repository.organization;

import com.carenest.entity.organization.AgeGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgeGroupRepository extends JpaRepository<AgeGroup, Long> {

    List<AgeGroup> findAllByOrderBySortOrderAsc();
}
