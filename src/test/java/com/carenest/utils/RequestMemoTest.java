package com.carenest.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestMemoTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void inRequest(String method) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(method, "/x")));
    }

    @Test
    void getRequestLoadsEachKeyOnceIncludingNull() {
        inRequest("GET");
        AtomicInteger loads = new AtomicInteger();
        for (int i = 0; i < 5; i++) RequestMemo.get("a", () -> loads.incrementAndGet() > 0);
        for (int i = 0; i < 3; i++)
            RequestMemo.get("n", () -> {
                loads.incrementAndGet();
                return null;
            });
        assertThat(loads).hasValue(2);
    }

    @Test
    void writeRequestAndNoRequestAlwaysLoad() {
        AtomicInteger loads = new AtomicInteger();
        inRequest("POST");
        RequestMemo.get("a", loads::incrementAndGet);
        RequestMemo.get("a", loads::incrementAndGet);
        RequestContextHolder.resetRequestAttributes();
        RequestMemo.get("a", loads::incrementAndGet);
        assertThat(loads).hasValue(3);
    }

    @Test
    void newRequestStartsEmpty() {
        AtomicInteger loads = new AtomicInteger();
        inRequest("GET");
        RequestMemo.get("a", loads::incrementAndGet);
        inRequest("GET");
        RequestMemo.get("a", loads::incrementAndGet);
        assertThat(loads).hasValue(2);
    }
}
