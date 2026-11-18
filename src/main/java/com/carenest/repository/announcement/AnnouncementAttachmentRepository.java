package com.carenest.repository.announcement;

import com.carenest.entity.announcement.AnnouncementAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementAttachmentRepository extends JpaRepository<AnnouncementAttachment, Long> {

    List<AnnouncementAttachment> findByAnnouncementIdOrderByIdAsc(Long announcementId);

    List<AnnouncementAttachment> findByAnnouncementIdIn(Collection<Long> announcementIds);

    List<AnnouncementAttachment> findByScheduleIdOrderByIdAsc(Long scheduleId);

    List<AnnouncementAttachment> findByScheduleIdIn(Collection<Long> scheduleIds);

    boolean existsByFileKey(String fileKey);
}
