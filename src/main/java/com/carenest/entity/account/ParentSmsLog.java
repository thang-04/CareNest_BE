package com.carenest.entity.account;

import com.carenest.utils.enums.SmsPurpose;
import com.carenest.utils.enums.SmsStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lần gửi SMS tài khoản phụ huynh (L-15). Không lưu nội dung tin, không lưu mật khẩu. */
@Getter
@Entity
@Table(name = "parent_sms_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParentSmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 10)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private SmsPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private SmsStatus status;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    public static ParentSmsLog of(Long userId, String phone, SmsPurpose purpose, SmsStatus status, Instant sentAt) {
        ParentSmsLog log = new ParentSmsLog();
        log.userId = userId;
        log.phone = phone;
        log.purpose = purpose;
        log.status = status;
        log.sentAt = sentAt;
        return log;
    }
}
