package com.carenest.service.observation.impl;

import com.carenest.dto.observation.EvaluationDtos.GoalCount;
import com.carenest.dto.observation.EvaluationDtos.GoalSummary;
import com.carenest.dto.observation.EvaluationDtos.HealthSummary;
import com.carenest.dto.observation.EvaluationDtos.StructuredSummary;
import com.carenest.service.observation.impl.PeriodCommentFacts.Note;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bộ case giả của eval nhận xét kỳ (OBS-34, AI-07): từng ngày thô trong {@code ai-eval/period-comment-cases.json}, số
 * {@code expect} do script sinh tính độc lập với Java để test đối chiếu. Tên, ngày sinh, số điện thoại đều giả.
 */
final class PeriodCommentEvalCases {

    private static final DateFormats FORMATS = new DateFormats();

    record EvalCase(String id, String title, JsonNode raw, PeriodCommentFacts facts, List<String> otherNames) {}

    private PeriodCommentEvalCases() {}

    static List<EvalCase> load() {
        try (InputStream in = PeriodCommentEvalCases.class.getResourceAsStream("/ai-eval/period-comment-cases.json")) {
            JsonNode root = JsonMapper.builder().build().readTree(in);
            List<EvalCase> cases = new ArrayList<>();
            for (JsonNode c : root.get("cases")) {
                List<String> others = new ArrayList<>();
                c.get("otherNames").forEach(n -> others.add(n.asString()));
                cases.add(new EvalCase(c.get("id").asString(), c.get("title").asString(), c, facts(c), others));
            }
            return cases;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static PeriodCommentFacts facts(JsonNode c) {
        int present = 0;
        int flags = 0;
        int observed = 0;
        Map<Emotion, Integer> emotion = new EnumMap<>(Emotion.class);
        Map<NapLevel, Integer> nap = new EnumMap<>(NapLevel.class);
        Map<Participation, Integer> participation = new EnumMap<>(Participation.class);
        Map<ObservedBehavior, Integer> behaviors = new EnumMap<>(ObservedBehavior.class);
        Map<EatingLevel, Integer> lunch = new EnumMap<>(EatingLevel.class);
        Map<String, int[]> goals = new LinkedHashMap<>();
        Map<String, String> goalNames = goalNames();
        goalNames.values().forEach(n -> goals.put(n, new int[2]));
        List<Note> daily = new ArrayList<>();
        for (JsonNode d : c.get("days")) {
            present += d.get("present").asBoolean() ? 1 : 0;
            if (!d.get("observed").asBoolean()) {
                continue;
            }
            observed++;
            flags += d.get("flag").asBoolean() ? 1 : 0;
            emotion.merge(Emotion.valueOf(d.get("emotion").asString()), 1, Integer::sum);
            nap.merge(NapLevel.valueOf(d.get("nap").asString()), 1, Integer::sum);
            participation.merge(Participation.valueOf(d.get("participation").asString()), 1, Integer::sum);
            lunch.merge(EatingLevel.valueOf(d.get("lunch").asString()), 1, Integer::sum);
            d.get("behaviors").forEach(b -> behaviors.merge(ObservedBehavior.valueOf(b.asString()), 1, Integer::sum));
            d.get("met").forEach(g -> goals.get(goalNames.get(g.asString()))[0]++);
            d.get("notMet").forEach(g -> goals.get(goalNames.get(g.asString()))[1]++);
            if (d.has("comment")) {
                daily.add(new Note(
                        "Ngày " + FORMATS.dayMonth(LocalDate.parse(d.get("date").asString())),
                        d.get("comment").asString()));
            }
        }
        Map<String, GoalCount> byGoal = new LinkedHashMap<>();
        goals.forEach((name, v) -> byGoal.put(name, new GoalCount(v[0], 0, v[1])));
        Map<MealSession, Map<EatingLevel, Integer>> meals = new EnumMap<>(MealSession.class);
        if (!lunch.isEmpty()) {
            meals.put(MealSession.LUNCH, lunch);
        }
        StructuredSummary st = new StructuredSummary(
                observed,
                new HealthSummary(0, 0, Map.of(), Map.of(), Map.of()),
                meals,
                nap,
                emotion,
                behaviors,
                participation,
                new GoalSummary(byGoal));
        return new PeriodCommentFacts(
                EvaluationPeriodType.valueOf(c.get("type").asString()),
                LocalDate.parse(c.get("start").asString()),
                LocalDate.parse(c.get("end").asString()),
                c.get("childName").asString(),
                LocalDate.parse(c.get("dateOfBirth").asString()),
                present,
                flags,
                st,
                notes(c.get("finals")),
                notes(c.get("weekly")),
                daily);
    }

    private static List<Note> notes(JsonNode array) {
        List<Note> notes = new ArrayList<>();
        array.forEach(
                n -> notes.add(new Note(n.get("label").asString(), n.get("text").asString())));
        return notes;
    }

    static Map<String, String> goalNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("g3", "MT3 – Trẻ nhảy bật xa");
        names.put("g7", "MT7 – Trẻ kể chuyện theo tranh");
        names.put("g9", "MT9 – Trẻ nhận biết màu sắc");
        return names;
    }

    private static final class DateFormats {
        String dayMonth(LocalDate date) {
            return String.format("%02d/%02d", date.getDayOfMonth(), date.getMonthValue());
        }
    }
}
