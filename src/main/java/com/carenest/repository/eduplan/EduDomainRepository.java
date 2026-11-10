package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduDomainRepository extends JpaRepository<EduDomain, Long> {

    List<EduDomain> findAllByActiveTrueOrderBySortOrderAsc();

    List<EduDomain> findAllByOrderBySortOrderAsc();
}
