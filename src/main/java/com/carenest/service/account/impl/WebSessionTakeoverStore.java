package com.carenest.service.account.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * AUTH-28: lượt đăng nhập Web đang chờ người dùng chọn dùng ở đâu. Chỉ giữ id, user, sid Keycloak và trạng thái — refresh
 * token của lượt chờ nằm trong cookie niêm phong của trình duyệt mới. Bộ nhớ trong: restart API thì lượt chờ mất,
 * người dùng đăng nhập lại.
 */
@Component
public class WebSessionTakeoverStore {
    private final ConcurrentHashMap<String, Takeover> takeovers = new ConcurrentHashMap<>();

    public Takeover create(String id, String userId, String sessionId, Instant now, Instant expiresAt) {
        Takeover takeover = new Takeover(id, userId, sessionId, now, expiresAt, false);
        takeovers.put(id, takeover);
        return takeover;
    }

    /** Lượt chờ mới nhất của user mà phiên đang dùng cần được hỏi (chưa bị từ chối, chưa hết hạn). */
    public Optional<Takeover> pendingFor(String userId, Instant now) {
        return takeovers.values().stream()
                .filter(t -> t.userId().equals(userId) && !t.rejected() && now.isBefore(t.expiresAt()))
                .max(Comparator.comparing(Takeover::requestedAt));
    }

    /** Phiên đang dùng chọn ở lại: đánh dấu từ chối (giữ tới hạn để trình duyệt mới biết lý do). */
    public Optional<Takeover> reject(String id, String userId, Instant now) {
        Takeover[] rejected = new Takeover[1];
        takeovers.computeIfPresent(id, (key, t) -> {
            if (!t.userId().equals(userId) || t.rejected() || !now.isBefore(t.expiresAt())) return t;
            rejected[0] = t.withRejected();
            return rejected[0];
        });
        return Optional.ofNullable(rejected[0]);
    }

    /** Trình duyệt mới xác nhận/hủy: lấy và xóa lượt chờ một lần duy nhất (chống xác nhận và từ chối chạy đua). */
    public Optional<Takeover> take(String id) {
        return Optional.ofNullable(takeovers.remove(id));
    }

    /** Lượt chờ khác của cùng user bị bỏ khi một lượt được xác nhận (phiên Keycloak của chúng bị hủy cùng lúc). */
    public void dropOthers(String userId, String keepId) {
        takeovers.values().removeIf(t -> t.userId().equals(userId) && !t.id().equals(keepId));
    }

    public List<Takeover> drainExpired(Instant now) {
        List<Takeover> expired = new ArrayList<>();
        takeovers.values().removeIf(t -> {
            if (now.isBefore(t.expiresAt())) return false;
            expired.add(t);
            return true;
        });
        return expired;
    }

    public record Takeover(
            String id, String userId, String sessionId, Instant requestedAt, Instant expiresAt, boolean rejected) {
        Takeover withRejected() {
            return new Takeover(id, userId, sessionId, requestedAt, expiresAt, true);
        }
    }
}
