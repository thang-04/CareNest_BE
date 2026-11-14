package com.carenest.repository.announcement;

import com.carenest.entity.announcement.AnnouncementTemplate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementTemplateRepository extends JpaRepository<AnnouncementTemplate, Long> {

    List<AnnouncementTemplate> findAllByOrderByNameAscIdAsc();

    /** PHT: mẫu toàn trường + mẫu của điểm trường mình. */
    @Query("SELECT t FROM AnnouncementTemplate t WHERE t.campusId IS NULL OR t.campusId IN :campusIds"
            + " ORDER BY t.name, t.id")
    List<AnnouncementTemplate> findVisible(@Param("campusIds") Collection<Long> campusIds);
}
