package com.carenest.repository.organization;

import com.carenest.entity.organization.Campus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusRepository extends JpaRepository<Campus, Long> {

    List<Campus> findAllByOrderByCodeAsc();

    List<Campus> findByIdInOrderByCodeAsc(Collection<Long> ids);
}
