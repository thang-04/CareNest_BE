package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduClassDocRelease;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduClassDocReleaseRepository extends JpaRepository<EduClassDocRelease, Long> {

    List<EduClassDocRelease> findAllByClassDocIdInOrderByReleasedAtAscIdAsc(Collection<Long> classDocIds);
}
