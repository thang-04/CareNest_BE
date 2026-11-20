package com.carenest.service.meals.impl;

import static com.carenest.service.meals.impl.MenuFixtures.CA;
import static com.carenest.service.meals.impl.MenuFixtures.COM;
import static com.carenest.service.meals.impl.MenuFixtures.MONDAY;
import static com.carenest.service.meals.impl.MenuFixtures.SUA;
import static com.carenest.service.meals.impl.MenuFixtures.THIT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Bước 5, 9: guard cấu trúc output model và làm sạch lý do (AI-01, NUT-05, AC-2). */
class MenuAiOutputParserTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final MenuContext ctx = MenuFixtures.ctx();
    private final MenuAiPromptBuilder.Aliases aliases = MenuAiPromptBuilder.aliases(ctx);
    private final Set<LocalDate> week = new LinkedHashSet<>(ctx.servedDates());

    @Test
    @DisplayName("Output đúng dạng, đủ ngày, đủ ô ⇒ map bí danh về món thật, lý do đã làm sạch")
    void validOutputMapsAliasesToDishes() {
        MenuAiOutputParser.Parsed parsed = parse(week(alias(COM), alias(THIT), alias(SUA), "Đổi món mặn mỗi ngày."));

        assertThat(parsed.valid()).isTrue();
        assertThat(parsed.grid().dish(MONDAY, MenuFixtures.LUNCH_MAIN.key())).isEqualTo(THIT);
        assertThat(parsed.grid().reasons().get(MONDAY)).isEqualTo("Đổi món mặn mỗi ngày.");
    }

    @Test
    @DisplayName("Bọc trong ```json vẫn đọc được")
    void fencedJsonIsAccepted() {
        assertThat(parse("```json\n" + week(alias(COM), alias(THIT), alias(SUA), "") + "\n```")
                        .valid())
                .isTrue();
    }

    @Test
    @DisplayName("AC-2: JSON hỏng, bí danh lạ, món sai ô, thiếu ô, thiếu ngày, ô lạ ⇒ không dùng output")
    void structuralErrorsAreRejected() {
        assertThat(parse("không phải json").valid()).isFalse();
        assertThat(parse("{\"menu\":[]}").valid()).isFalse();
        assertThat(parse(week("D99", alias(THIT), alias(SUA), "")).valid()).isFalse();
        // Món mặn đặt vào ô cơm
        assertThat(parse(week(alias(CA), alias(THIT), alias(SUA), "")).valid()).isFalse();
        String missingSlot = "{\"days\":["
                + ctx.servedDates().stream()
                        .map(d -> "{\"date\":\"" + d + "\",\"items\":[{\"slot\":\"LUNCH.1\",\"dish\":\"" + alias(COM)
                                + "\"}]}")
                        .collect(Collectors.joining(","))
                + "]}";
        assertThat(parse(missingSlot).error()).contains("thiếu ô");
        String oneDay = "{\"days\":[" + day(MONDAY, alias(COM), alias(THIT), alias(SUA), "") + "]}";
        assertThat(parse(oneDay).error()).contains("thiếu ngày");
        String strangeSlot = week(alias(COM), alias(THIT), alias(SUA), "").replace("AFTERNOON.1", "DINNER.1");
        assertThat(parse(strangeSlot).error()).contains("ô lạ");
    }

    @Test
    @DisplayName("Vòng sửa chỉ yêu cầu một ngày ⇒ trả ngày khác là sai")
    void repairRoundAcceptsOnlyRequestedDays() {
        String tuesday = "{\"days\":[" + day(MONDAY.plusDays(1), alias(COM), alias(CA), alias(SUA), "") + "]}";

        assertThat(MenuAiOutputParser.parse(tuesday, ctx, aliases, Set.of(MONDAY.plusDays(1)), MAPPER)
                        .valid())
                .isTrue();
        assertThat(MenuAiOutputParser.parse(tuesday, ctx, aliases, Set.of(MONDAY), MAPPER)
                        .valid())
                .isFalse();
    }

    @Test
    @DisplayName("FR-08: lý do bỏ chữ số, bỏ câu có thuật ngữ y khoa, tối đa 150 ký tự")
    void reasonIsSanitized() {
        assertThat(MenuAiOutputParser.sanitizeReason(
                        "Thịt gà 120 kcal, đủ 15% đạm. Tốt cho trẻ bị thiếu máu. Hợp mùa."))
                .doesNotContainPattern("\\d")
                .doesNotContain("thiếu máu")
                .contains("Hợp mùa.");
        assertThat(MenuAiOutputParser.sanitizeReason("a".repeat(400))).hasSize(MenuAiOutputParser.REASON_MAX);
        assertThat(MenuAiOutputParser.sanitizeReason(null)).isEmpty();
    }

    private MenuAiOutputParser.Parsed parse(String content) {
        return MenuAiOutputParser.parse(content, ctx, aliases, week, MAPPER);
    }

    private String alias(long dishId) {
        return aliases.toAlias().get(dishId);
    }

    private String week(String carb, String main, String snack, String reason) {
        List<String> days = ctx.servedDates().stream()
                .map(d -> day(d, carb, main, snack, reason))
                .toList();
        return "{\"days\":[" + String.join(",", days) + "]}";
    }

    private static String day(LocalDate date, String carb, String main, String snack, String reason) {
        return "{\"date\":\"" + date + "\",\"items\":[{\"slot\":\"LUNCH.1\",\"dish\":\"" + carb
                + "\"},{\"slot\":\"LUNCH.2\",\"dish\":\"" + main + "\"},{\"slot\":\"AFTERNOON.1\",\"dish\":\"" + snack
                + "\"}],\"reason\":\"" + reason + "\"}";
    }
}
