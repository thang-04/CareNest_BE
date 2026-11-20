package com.carenest.service.observation.impl;

import com.carenest.service.observation.impl.PeriodCommentEvalCases.EvalCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Ghi CSV cho người kiểm tính lại bằng bảng tính (OBS-34): {@code days.csv} là từng ngày thô của từng case, {@code
 * facts.csv} là số hệ thống đưa vào prompt (đếm bằng code) kèm số kỳ vọng tính độc lập. LLM chỉ viết lời, không tính số.
 */
final class PeriodCommentEvalReport {

    private PeriodCommentEvalReport() {}

    static void writeFacts(Path dir, List<EvalCase> cases) throws IOException {
        Files.createDirectories(dir);
        List<String> days = new ArrayList<>(List.of(
                "case,date,present,observed,emotion,nap,lunch,participation,behaviors,flag,met,not_met,comment_chars"));
        List<String> facts = new ArrayList<>(List.of("case,metric,in_prompt,expected_independent,match"));
        for (EvalCase c : cases) {
            for (JsonNode d : c.raw().get("days")) {
                boolean obs = d.get("observed").asBoolean();
                days.add(String.join(
                        ",",
                        c.id(),
                        d.get("date").asString(),
                        d.get("present").asString(),
                        d.get("observed").asString(),
                        obs ? d.get("emotion").asString() : "",
                        obs ? d.get("nap").asString() : "",
                        obs ? d.get("lunch").asString() : "",
                        obs ? d.get("participation").asString() : "",
                        obs ? join(d.get("behaviors")) : "",
                        obs ? d.get("flag").asString() : "",
                        obs ? join(d.get("met")) : "",
                        obs ? join(d.get("notMet")) : "",
                        d.has("comment")
                                ? Integer.toString(d.get("comment").asString().length())
                                : "0"));
            }
            JsonNode expect = c.raw().get("expect");
            metric(
                    facts,
                    c,
                    "present_days",
                    c.facts().presentDays(),
                    expect.get("presentDays").asInt());
            metric(
                    facts,
                    c,
                    "observed_days",
                    c.facts().structured().observedDays(),
                    expect.get("observedDays").asInt());
            metric(
                    facts,
                    c,
                    "flag_count",
                    c.facts().flagCount(),
                    expect.get("flagCount").asInt());
            metric(
                    facts,
                    c,
                    "lunch_an_het",
                    c.facts().structured().meals().values().stream()
                            .mapToInt(m -> m.getOrDefault(com.carenest.utils.enums.EatingLevel.AN_HET, 0))
                            .sum(),
                    expect.get("lunch").has("AN_HET")
                            ? expect.get("lunch").get("AN_HET").asInt()
                            : 0);
            c.facts().structured().goals().byGoal().forEach((name, g) -> {
                JsonNode e = expect.get("goalsDatChuaDat").get(name);
                metric(facts, c, "goal_dat:" + name, g.dat(), e.get(0).asInt());
                metric(facts, c, "goal_chua_dat:" + name, g.chuaDat(), e.get(1).asInt());
            });
        }
        // BOM để Excel đọc đúng tiếng Việt khi mở trực tiếp
        Files.writeString(dir.resolve("days.csv"), "﻿" + String.join("\n", days) + "\n");
        Files.writeString(dir.resolve("facts.csv"), "﻿" + String.join("\n", facts) + "\n");
    }

    private static void metric(List<String> out, EvalCase c, String name, int inPrompt, int expected) {
        out.add(String.join(
                ",",
                c.id(),
                "\"" + name + "\"",
                Integer.toString(inPrompt),
                Integer.toString(expected),
                inPrompt == expected ? "KHỚP" : "LỆCH"));
    }

    private static String join(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return String.join("|", values);
    }
}
