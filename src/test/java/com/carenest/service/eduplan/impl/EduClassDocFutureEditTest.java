package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.exception.GlobalException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class EduClassDocFutureEditTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDate monday = LocalDate.of(2026, 11, 9);

    @Test
    void futureDayCanChangeButTodayAndSharedWeeklyContentStayLocked() {
        ObjectNode old = week();
        ObjectNode future = old.deepCopy();
        ((ObjectNode) future.path("days").get(2)).put("aims", "Mới");
        assertThatCode(() -> EduClassDocFutureEdit.validateWeek(old, future, monday, monday.plusDays(1)))
                .doesNotThrowAnyException();
        ObjectNode today = old.deepCopy();
        ((ObjectNode) today.path("days").get(1)).put("aims", "Mới");
        assertThatThrownBy(() -> EduClassDocFutureEdit.validateWeek(old, today, monday, monday.plusDays(1)))
                .isInstanceOf(GlobalException.class);
        future.put("prep", "Thay chuẩn bị cả tuần");
        assertThatThrownBy(() -> EduClassDocFutureEdit.validateWeek(old, future, monday, monday.plusDays(1)))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    void gridFutureCellsCanChangeButCannotMovePastDaysToFutureDates() {
        ObjectNode old = week();
        ObjectNode future = old.deepCopy();
        ((tools.jackson.databind.node.ArrayNode) future.path("grid").path("hoc").path("days")).set(4, mapper.valueToTree("Mới"));
        assertThatCode(() -> EduClassDocFutureEdit.validateWeek(old, future, monday, monday.plusDays(1)))
                .doesNotThrowAnyException();
        ((ObjectNode) future.path("days").get(0)).put("date", monday.plusWeeks(1).toString());
        assertThatThrownBy(() -> EduClassDocFutureEdit.validateWeek(old, future, monday, monday.plusDays(1)))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    void unknownDateAndStartDayAreLockedButFutureThemeIsEditable() {
        assertThatThrownBy(() -> EduClassDocFutureEdit.requireFuture(null, monday)).isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> EduClassDocFutureEdit.requireFuture(monday, monday)).isInstanceOf(GlobalException.class);
        assertThatCode(() -> EduClassDocFutureEdit.requireFuture(monday.plusDays(1), monday)).doesNotThrowAnyException();
    }

    private ObjectNode week() {
        ObjectNode doc = mapper.createObjectNode();
        doc.put("prep", "Cũ");
        var days = doc.putArray("days");
        for (int i = 0; i < 5; i++) days.addObject().put("date", monday.plusDays(i).toString()).put("aims", "Cũ");
        var grid = doc.putObject("grid").putObject("hoc");
        grid.put("common", "Chung");
        var cells = grid.putArray("days");
        for (int i = 0; i < 5; i++) cells.add("Cũ");
        return doc;
    }
}
