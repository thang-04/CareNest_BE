package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.EduConfigResponse.WeekRow;
import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class EduActivityCardsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<WeekRow> rows = List.of(
            new WeekRow("tro_chuyen", "Trò chuyện", true, true, null),
            new WeekRow("hoc", "Hoạt động học", false, false, null),
            new WeekRow("goc", "Hoạt động góc", true, true, null),
            new WeekRow("chieu", "Hoạt động chiều", false, false, null));
    private final Set<String> slots = Set.of("tro_chuyen", "hoc", "goc", "chieu");

    private ObjectNode json(String raw) {
        return (ObjectNode) mapper.readTree(raw.replace('\'', '"'));
    }

    @Test
    void legacyLineBecomesCardWithGoalHashId() {
        ObjectNode content = json("{'rows':{'7':{'activities':'- HĐ học: Đi bước lùi'}}}");
        ArrayNode cards = EduActivityCards.normalizeTheme(content, rows, null);
        assertThat(cards).hasSize(1);
        JsonNode card = cards.get(0);
        assertThat(card.path("id").asString()).isEqualTo("7#0");
        assertThat(card.path("slot").asString()).isEqualTo("hoc");
        assertThat(card.path("name").asString()).isEqualTo("HĐ học: Đi bước lùi");
        assertThat(card.path("goalIds").get(0).asLong()).isEqualTo(7L);
        assertThat(card.path("legacy").asBoolean()).isTrue();
    }

    @Test
    void headingChildrenInheritSlotAndUnknownHeadIsBlank() {
        ObjectNode content = json("{'rows':{'3':{'activities':'Hoạt động góc:\\n+ Xây nhà\\nKể chuyện bé nghe'}}}");
        ArrayNode cards = EduActivityCards.normalizeTheme(content, rows, Set.of(3L));
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).path("slot").asString()).isEqualTo("goc");
        assertThat(cards.get(0).path("name").asString()).isEqualTo("Hoạt động góc: Xây nhà");
        assertThat(cards.get(0).path("id").asString()).isEqualTo("3#0");
        assertThat(cards.get(1).path("slot").asString()).isEmpty();
        assertThat(cards.get(1).path("id").asString()).isEqualTo("3#1");
    }

    @Test
    void existingActivitiesAreKeptAndOtherGoalsSkipped() {
        ObjectNode saved = json("{'activities':[{'id':'c-1','slot':'hoc','name':'X','goalIds':['3']}],"
                + "'rows':{'3':{'activities':'HĐ học: Y'}}}");
        assertThat(EduActivityCards.normalizeTheme(saved, rows, null)).isSameAs(saved.get("activities"));
        ObjectNode old = json("{'rows':{'3':{'activities':'HĐ học: Y'},'9':{'activities':'HĐ học: Z'}}}");
        assertThat(EduActivityCards.normalizeTheme(old, rows, Set.of(3L))).hasSize(1);
    }

    @Test
    void validateFollowsR8() {
        Set<Long> goals = Set.of(3L, 4L);
        EduActivityCards.validate(
                mapper.readTree("[{\"id\":\"c-1\",\"slot\":\"hoc\",\"name\":\"Bật xa\",\"goalIds\":[\"3\",4],"
                        + "\"contentRefs\":[\"3:0\"]},{\"id\":\"3#0\",\"slot\":\"\",\"name\":\"Cũ\",\"goalIds\":[3],"
                        + "\"legacy\":true}]"),
                goals,
                slots);
        for (String bad : List.of(
                "[{\"id\":\"a\",\"slot\":\"hoc\",\"name\":\" \",\"goalIds\":[3]}]",
                "[{\"id\":\"a\",\"slot\":\"\",\"name\":\"X\",\"goalIds\":[3]}]",
                "[{\"id\":\"a\",\"slot\":\"bay\",\"name\":\"X\",\"goalIds\":[3]}]",
                "[{\"id\":\"a\",\"slot\":\"hoc\",\"name\":\"X\",\"goalIds\":[9]}]",
                "[{\"id\":\"a\",\"slot\":\"hoc\",\"name\":\"X\",\"goalIds\":[3],\"contentRefs\":[\"4:0\"]}]",
                "[{\"id\":\"a\",\"slot\":\"hoc\",\"name\":\"X\",\"goalIds\":[3]},{\"id\":\"a\",\"slot\":\"hoc\",\"name\":\"Y\"}]",
                "[{\"id\":\"a\",\"slot\":\"hoc\",\"name\":\"" + "x".repeat(301) + "\",\"goalIds\":[3]}]"))
            assertThatThrownBy(() -> EduActivityCards.validate(mapper.readTree(bad), goals, slots))
                    .isInstanceOf(GlobalException.class)
                    .extracting(ex -> ((GlobalException) ex).getApiCode())
                    .isEqualTo(ApiCode.INVALID_VALUE);
    }

    @Test
    void resolveGoalsByCardThenSnapshotThenLegacyGoalId() {
        Map<String, JsonNode> cards = EduActivityCards.cards(
                mapper.readTree("[{\"id\":\"c-1\",\"goalIds\":[\"3\",\"4\"]}]"),
                mapper.readTree("{\"cards\":[{\"id\":\"d-1\",\"goalIds\":[5]}]}"));
        assertThat(EduActivityCards.resolveGoals(json("{'cardId':'c-1','goalIds':[9]}"), cards))
                .containsExactly(3L, 4L);
        assertThat(EduActivityCards.resolveGoals(json("{'cardId':'d-1'}"), cards))
                .containsExactly(5L);
        assertThat(EduActivityCards.resolveGoals(json("{'cardId':'gone','goalIds':['8']}"), cards))
                .containsExactly(8L);
        assertThat(EduActivityCards.resolveGoals(json("{'id':'7#0','goalId':'7'}"), cards))
                .containsExactly(7L);
        assertThat(EduActivityCards.resolveGoals(json("{'text':'gõ tay'}"), cards))
                .isEmpty();
    }

    @Test
    void goalLeftThemeIsDroppedButCardKept() {
        ArrayNode cards =
                (ArrayNode) mapper.readTree("[{\"id\":\"c\",\"goalIds\":[3,4],\"contentRefs\":[\"3:0\",\"4:1\"]}]");
        EduActivityCards.dropGoalsOutside(cards, Set.of(3L), Set.of(3L, 4L));
        assertThat(cards.get(0).path("goalIds")).hasSize(1);
        assertThat(cards.get(0).path("contentRefs")).hasSize(1);
    }
}
