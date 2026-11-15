package com.carenest.repository.notification;

import com.carenest.entity.notification.Notification;
import com.carenest.utils.enums.PushState;
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

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    List<Notification> findByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Id các dòng chờ đẩy đã đến hạn (không khóa; worker khóa từng dòng khi xử lý). */
    @Query(
            value = "SELECT id FROM notifications WHERE push_state = 'PENDING'"
                    + " AND (push_next_at IS NULL OR push_next_at <= :now) ORDER BY id LIMIT :limit",
            nativeQuery = true)
    List<Long> findDuePushIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query(
            value = "SELECT id FROM notifications WHERE push_state = 'PENDING'"
                    + " AND id > :after AND id <= :ceiling"
                    + " AND (push_next_at IS NULL OR push_next_at <= :now OR created_at <= :cutoff)"
                    + " ORDER BY id LIMIT :limit",
            nativeQuery = true)
    List<Long> findDuePushIdsAfter(
            @Param("now") Instant now,
            @Param("cutoff") Instant cutoff,
            @Param("after") long after,
            @Param("ceiling") long ceiling,
            @Param("limit") int limit);

    @Query(value = "SELECT COALESCE(MAX(id), 0) FROM notifications WHERE push_state = 'PENDING'", nativeQuery = true)
    long findPushCeiling();

    /**
     * Khóa một dòng còn chờ đẩy; dòng đang được máy khác xử lý thì bỏ qua (SKIP LOCKED) ⇒ mỗi dòng chỉ một máy đẩy
     * (FR-003).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.pushState = :state")
    Optional<Notification> lockForPush(@Param("id") Long id, @Param("state") PushState state);

    @Query("SELECT n.userId FROM Notification n WHERE n.announcementId = :announcementId")
    List<Long> findUserIdsByAnnouncementId(@Param("announcementId") Long announcementId);

    boolean existsByUserIdAndAnnouncementId(Long userId, Long announcementId);

    boolean existsByUserIdAndTypeAndLink(Long userId, com.carenest.utils.enums.NotificationType type, String link);
}
