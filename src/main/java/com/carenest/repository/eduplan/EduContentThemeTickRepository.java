package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduContentThemeTick;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduContentThemeTickRepository extends JpaRepository<EduContentThemeTick, EduContentThemeTick.Key> {

    List<EduContentThemeTick> findAllByThemeIdIn(Collection<Long> themeIds);
}
