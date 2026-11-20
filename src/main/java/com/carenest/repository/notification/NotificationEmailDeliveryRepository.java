package com.carenest.repository.notification;

import com.carenest.entity.notification.NotificationEmailDelivery;
import com.carenest.utils.enums.PushState;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface NotificationEmailDeliveryRepository extends JpaRepository<NotificationEmailDelivery, Long> {

    @Query(
            value = "SELECT id FROM notification_email_deliveries WHERE state = 'PENDING'"
                    + " AND (next_attempt_at IS NULL OR next_attempt_at <= :now) ORDER BY id LIMIT :limit",
            nativeQuery = true)
    List<Long> findDueIds(@Param("now") Instant now, @Param("limit") int limit);

    /** Người nhận email: chỉ tài khoản đang hoạt động có email. */
    interface Recipient {
        String getEmail();

        String getFullName();
    }

    // Đọc thẳng bảng tài khoản thay vì gọi AccountService: module account đã phụ thuộc notification (tránh vòng)
    @Query(
            value = "SELECT email, full_name AS fullName FROM user_accounts"
                    + " WHERE id = :userId AND status = 'ACTIVE' AND email IS NOT NULL AND btrim(email) <> ''",
            nativeQuery = true)
    Optional<Recipient> findRecipient(@Param("userId") Long userId);

    /** Khóa một dòng còn chờ; dòng máy khác đang gửi thì bỏ qua (SKIP LOCKED) ⇒ mỗi email chỉ gửi một lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT d FROM NotificationEmailDelivery d WHERE d.id = :id AND d.state = :state")
    Optional<NotificationEmailDelivery> lockForSend(@Param("id") Long id, @Param("state") PushState state);
}
