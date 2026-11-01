package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduCurriculumSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduCurriculumSourceRepository extends JpaRepository<EduCurriculumSource, Long> {

    List<EduCurriculumSource> findAllByActiveTrueOrderByIssuedDateDesc();
}
