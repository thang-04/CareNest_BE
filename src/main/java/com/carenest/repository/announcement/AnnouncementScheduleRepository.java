package com.carenest.repository.announcement;

import com.carenest.entity.announcement.AnnouncementSchedule;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AnnouncementScheduleRepository extends JpaRepository<AnnouncementSchedule, Long> {

    List<AnnouncementSchedule> findAllByOrderByIdAsc();

    boolean existsByTemplateId(Long templateId);

    @Query("SELECT s.id FROM AnnouncementSchedule s WHERE s.active = true AND s.nextRunAt <= :now ORDER BY s.nextRunAt")
    List<Long> findDueIds(@Param("now") Instant now, Pageable pageable);

    /** Khóa lịch đến hạn; máy khác đang giữ thì bỏ qua (SKIP LOCKED). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT s FROM AnnouncementSchedule s WHERE s.id = :id AND s.active = true AND s.nextRunAt <= :now")
    Optional<AnnouncementSchedule> lockDue(@Param("id") Long id, @Param("now") Instant now);
}
