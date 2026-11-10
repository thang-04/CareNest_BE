package com.carenest.entity.notification;

import com.carenest.utils.enums.DevicePlatform;
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

/** Thiết bị nhận thông báo đẩy của người dùng (token của dịch vụ đẩy); đăng nhập người khác trên máy đó thì chuyển. */
@Getter
@Entity
@Table(name = "device_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static DeviceToken of(Long userId, String token, DevicePlatform platform, Instant at) {
        DeviceToken d = new DeviceToken();
        d.userId = userId;
        d.token = token;
        d.platform = platform;
        d.createdAt = at;
        d.updatedAt = at;
        return d;
    }

    /** Máy đăng nhập lại (có thể người khác): gắn token cho người dùng hiện tại. */
    public void assignTo(Long userId, DevicePlatform platform, Instant at) {
        this.userId = userId;
        this.platform = platform;
        this.updatedAt = at;
    }
}
