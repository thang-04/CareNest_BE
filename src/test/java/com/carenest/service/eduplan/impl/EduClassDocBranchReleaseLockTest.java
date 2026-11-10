package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.exception.GlobalException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Spec 002 FR-024: chủ đề nhánh chưa ban hành lưu được cả tuần đã qua; chủ đề đã ban hành vẫn khóa phần đã qua. */
class EduClassDocBranchReleaseLockTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDate past = LocalDate.of(2026, 9, 7);
    private final LocalDate today = LocalDate.of(2026, 11, 10);
    private final Map<LocalDate, Long> weeks = Map.of(past, 31L);

    @Test
    void firstSaveOfUnreleasedThemeAcceptsPastWeeks() {
        ObjectNode empty = mapper.createObjectNode();
        ObjectNode next = mapper.createObjectNode();
        next.putObject("names").put(past.toString(), "Bé đến trường");
        assertThat(EduClassDocFutureEdit.validateBranch(empty, next, weeks, today, Set.of())).containsExactly(31L);
    }

    @Test
    void releasedThemeStillLocksPastWeeks() {
        ObjectNode before = mapper.createObjectNode();
        before.putObject("names").put(past.toString(), "Cũ");
        ObjectNode next = mapper.createObjectNode();
        next.putObject("names").put(past.toString(), "Mới");
        assertThatThrownBy(() -> EduClassDocFutureEdit.validateBranch(before, next, weeks, today, Set.of(31L)))
                .isInstanceOf(GlobalException.class);
    }
}
