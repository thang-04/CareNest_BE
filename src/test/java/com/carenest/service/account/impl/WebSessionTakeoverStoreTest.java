package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebSessionTakeoverStoreTest {
    private final WebSessionTakeoverStore store = new WebSessionTakeoverStore();
    private final Instant now = Instant.parse("2026-11-16T03:00:00Z");

    @Test
    @DisplayName("AUTH-28: hết 2 phút chờ thì phiên đang dùng không còn bị hỏi và lượt chờ được dọn")
    void expiredTakeoverIsNoLongerPendingAndIsDrained() {
        store.create("t1", "kc-user", "new-sid", now, now.plusSeconds(120));
        assertThat(store.pendingFor("kc-user", now.plusSeconds(119))).isPresent();
        assertThat(store.pendingFor("kc-user", now.plusSeconds(120))).isEmpty();
        assertThat(store.drainExpired(now.plusSeconds(120)))
                .extracting(WebSessionTakeoverStore.Takeover::sessionId)
                .containsExactly("new-sid");
        assertThat(store.take("t1")).isEmpty();
    }

    @Test
    @DisplayName("AUTH-28: xác nhận lấy lượt chờ đi rồi thì phiên cũ không từ chối được nữa (một bên thắng)")
    void takenTakeoverCannotBeRejected() {
        store.create("t1", "kc-user", "new-sid", now, now.plusSeconds(120));
        assertThat(store.take("t1")).isPresent();
        assertThat(store.reject("t1", "kc-user", now)).isEmpty();
    }

    @Test
    @DisplayName("AUTH-28: một lượt được xác nhận thì các lượt chờ khác của cùng tài khoản bị bỏ")
    void confirmingOneDropsOtherPendingTakeoversOfSameUser() {
        store.create("t1", "kc-user", "sid-b", now, now.plusSeconds(120));
        store.create("t2", "kc-user", "sid-c", now.plusSeconds(1), now.plusSeconds(121));
        store.create("t3", "kc-other", "sid-x", now, now.plusSeconds(120));
        store.dropOthers("kc-user", "t2");
        assertThat(store.take("t1")).isEmpty();
        assertThat(store.take("t3")).isPresent();
    }
}
