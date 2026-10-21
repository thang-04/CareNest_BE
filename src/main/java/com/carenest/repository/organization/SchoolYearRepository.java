package com.carenest.repository.organization;

import com.carenest.entity.organization.SchoolYear;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolYearRepository extends JpaRepository<SchoolYear, Long> {

    List<SchoolYear> findAllByOrderByStartDateDesc();
}
