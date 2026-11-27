package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EduClassDocRevisionTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant released = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    @DisplayName(
            "EDU-116: chỉ phiên bản điều chỉnh mở sau lần ban hành gần nhất mới còn đang dở; lấy người mở lần cuối")
    void openRevisionIsTheLatestAfterRelease() {
        var history = mapper.readTree("""
                [{"action":"revise_future","actorId":1,"at":"2026-08-20T00:00:00Z"},
                 {"action":"approve","actorId":2,"at":"2026-09-01T00:00:00Z"}]""");
        // Điều chỉnh đã ban hành xong thì không còn gì để xóa
        assertThat(EduClassDocServiceImpl.openRevision(history, released)).isNull();
        var open = mapper.readTree("""
                [{"action":"revise_future","actorId":1,"at":"2026-08-20T00:00:00Z"},
                 {"action":"revise_future","actorId":3,"at":"2026-09-02T00:00:00Z"},
                 {"action":"week_changed","actorId":4,"at":"2026-09-03T00:00:00Z"}]""");
        assertThat(EduClassDocServiceImpl.openRevision(open, released)
                        .path("actorId")
                        .asInt())
                .isEqualTo(3);
    }
}
