package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.exception.GlobalException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class EduDayTemplateTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode json(String text) {
        return (ObjectNode) mapper.readTree(text);
    }

    @Test
    void weekGocRowIsBuiltFromCornerTable() {
        ObjectNode week = json("""
                {"grid":{"goc":{"allWeek":false,"common":"tự gõ","days":["a","","","",""]}},
                 "corners":[{"id":"c1","name":" Góc phân vai ","content":"Bán hàng"},
                            {"id":"c2","name":"","content":""},
                            {"id":"c3","name":"Góc xây dựng","content":""}]}
                """);

        EduDayTemplate.normalizeWeek(week);

        assertThat(week.path("grid").path("goc").path("allWeek").asBoolean()).isTrue();
        assertThat(week.path("grid").path("goc").path("common").asString())
                .isEqualTo("Góc phân vai: Bán hàng\nGóc xây dựng");
    }

    @Test
    void weekWithoutCornerTableKeepsGocRow() {
        ObjectNode week = json("{\"grid\":{\"goc\":{\"allWeek\":false,\"common\":\"cũ\"}}}");

        EduDayTemplate.normalizeWeek(week);

        assertThat(week.path("grid").path("goc").path("common").asString()).isEqualTo("cũ");
    }

    @Test
    void weekRejectsTooLongTextAndCornerWithoutId() {
        String longText = "x".repeat(EduDayTemplate.TEXT_MAX + 1);
        assertThatThrownBy(() -> EduDayTemplate.normalizeWeek(json("{\"prep\":{\"teacher\":\"" + longText + "\"}}")))
                .isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> EduDayTemplate.normalizeWeek(json("{\"corners\":[{\"name\":\"Góc\"}]}")))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    void dayAcceptsTheFourSectionsAndReview() {
        ObjectNode day = json("""
                {"days":[{"date":"2026-11-09",
                  "sections":{"hoc":{"aims":"a","prep":"b","rows":[{"id":"r1","title":"Kết thúc","teacher":"t","child":"c","note":""}]},
                              "ngoai_troi":{"rows":[]},"goc":{},"chieu":{}},
                  "review":{"health":"tốt","knowledge":"","attitude":""}}]}
                """);

        assertThatCode(() -> EduDayTemplate.validateDay(day)).doesNotThrowAnyException();
    }

    @Test
    void dayRejectsUnknownSectionTooLongCellAndBadRows() {
        assertThatThrownBy(() -> EduDayTemplate.validateDay(json("{\"days\":[{\"sections\":{\"an_ngu\":{}}}]}")))
                .isInstanceOf(GlobalException.class);
        String longName = "x".repeat(EduDayTemplate.NAME_MAX + 1);
        assertThatThrownBy(() -> EduDayTemplate.validateDay(
                        json("{\"days\":[{\"sections\":{\"hoc\":{\"rows\":[{\"title\":\"" + longName + "\"}]}}}]}")))
                .isInstanceOf(GlobalException.class);
        assertThatThrownBy(() ->
                        EduDayTemplate.validateDay(json("{\"days\":[{\"sections\":{\"hoc\":{\"rows\":\"x\"}}}]}")))
                .isInstanceOf(GlobalException.class);
        String longText = "x".repeat(EduDayTemplate.TEXT_MAX + 1);
        assertThatThrownBy(() ->
                        EduDayTemplate.validateDay(json("{\"days\":[{\"review\":{\"health\":\"" + longText + "\"}}]}")))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    void dayAcceptsRemovedPartsButRejectsUnknownPart() {
        assertThatCode(() -> EduDayTemplate.validateDay(
                        json("{\"days\":[{\"sections\":{\"chieu\":{\"off\":[\"aims\",\"rows\"],\"rows\":[]}}}]}")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> EduDayTemplate.validateDay(
                        json("{\"days\":[{\"sections\":{\"chieu\":{\"off\":[\"review\"]}}}]}")))
                .isInstanceOf(GlobalException.class);
    }
}
