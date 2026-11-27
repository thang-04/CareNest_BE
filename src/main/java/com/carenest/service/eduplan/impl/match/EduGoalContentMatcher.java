package com.carenest.service.eduplan.impl.match;

import com.carenest.dto.eduplan.CurriculumContentResponse;
import com.carenest.dto.eduplan.CurriculumGoalResponse;
import com.carenest.dto.eduplan.EduAiResponse;
import com.carenest.service.eduplan.EduCurriculumService;
import com.carenest.service.eduplan.EduPreviousYearContentService;
import com.carenest.service.eduplan.impl.rag.EduRagRetriever;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ghép nội dung cho mục tiêu (EDU-106 kế hoạch năm, EDU-115 kế hoạch chủ đề): gom ứng viên 4 nguồn, mỗi mục tiêu lấy top K
 * qua {@link EduCandidateRanker}, dựng prompt, rồi lọc output AI. Chữ nội dung lấy từ nguồn theo mã, không tin chữ AI; câu AI
 * tự viết chỉ nhận cho mục tiêu không chọn được ứng viên nào.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EduGoalContentMatcher {

    public static final String CURRICULUM_C = "CURRICULUM_C";
    public static final String CURRICULUM_D = "CURRICULUM_D";
    public static final String PREVIOUS_YEAR = "PREVIOUS_YEAR";
    public static final String RAG = "RAG";
    public static final String AI = "AI";

    static final int TOP_K = 12;
    static final int GOALS_PER_CALL = 15;
    public static final int WRITE_MAX = 3;
    static final int TEXT_MAX = 300;
    static final int FROM_MAX = 120;
    static final int MIN_SCORE = 2;
    private static final List<String> PRIORITY = List.of(CURRICULUM_C, CURRICULUM_D, PREVIOUS_YEAR, RAG);

    public static final String SYSTEM =
            "Bạn là chuyên gia giáo dục mầm non Việt Nam, chọn NỘI DUNG giáo dục cho từng MỤC TIÊU."
                    + "\nQuy tắc:\n"
                    + "- Mỗi mục tiêu có danh sách options riêng; chỉ chọn trong options của CHÍNH mục tiêu đó, dùng đúng key.\n"
                    + "- Chấm mức phù hợp trực tiếp với mục tiêu: 3 rất phù hợp, 2 phù hợp, 1 liên quan xa, 0 không. Chỉ trả"
                    + " lựa chọn 2–3 điểm. Chọn nhầm tệ hơn bỏ sót.\n"
                    + "- Mỗi lựa chọn ghi from = cụm từ NGUYÊN VĂN trong câu mục tiêu làm căn cứ.\n"
                    + "- Có limit thì chỉ trả tối đa limit câu hợp nhất cho mục tiêu đó. current là các câu đã gợi ý trước:"
                    + " không chọn hay viết câu giống hoặc gần nghĩa.\n"
                    + "- Chỉ khi một mục tiêu không có lựa chọn nào đạt 2 điểm mới viết tối đa 3 câu nội dung mới vào written:"
                    + " ngắn, cụ thể, phù hợp độ tuổi, cùng giọng văn với options; kèm from như trên.\n"
                    + "- Có tình hình lớp thì ưu tiên nội dung hợp tình hình lớp. Không nhắc tên trẻ.\n"
                    + "Chỉ trả JSON: {\"matches\":[{\"goalId\":1,\"key\":\"C12\",\"score\":3,\"from\":\"cụm nguyên văn\"}],"
                    + "\"written\":[{\"goalId\":1,\"text\":\"câu mới\",\"from\":\"cụm nguyên văn\"}]}";

    private final EduCurriculumService curriculumService;
    private final EduPreviousYearContentService previousYearService;
    private final EduRagRetriever ragRetriever;
    private final EduCandidateRanker ranker;
    private final ObjectMapper objectMapper;

    public record Candidate(String key, String text, String source, Long domainId, EduAiResponse.RagRef ref) {}

    /**
     * Mục tiêu cần gợi ý; {@code have} = chữ nội dung đang có (không gợi ý lại). EDU-115 viết lại: {@code replace} là các dòng
     * sẽ bị thay (gửi AI để viết lại), {@code limit} là số câu tối đa nhận.
     */
    public record Goal(
            Long id,
            Long domainId,
            String domainName,
            String text,
            List<String> have,
            List<String> replace,
            int limit) {
        public Goal(Long id, Long domainId, String domainName, String text, List<String> have) {
            this(id, domainId, domainName, text, have, List.of(), Integer.MAX_VALUE);
        }
    }

    public record Ask(
            Long domainId,
            String domainName,
            List<Goal> goals,
            Map<Long, List<Candidate>> options,
            String prompt,
            boolean rewrite) {}

    public static final String REWRITE_SYSTEM =
            "Bạn là chuyên gia giáo dục mầm non Việt Nam, VIẾT LẠI nội dung giáo dục"
                    + " cho phù hợp TÌNH HÌNH LỚP (classSituation).\nQuy tắc:\n"
                    + "- current là các dòng sẽ bị thay; viết lại bám tình hình lớp, vẫn phục vụ đúng mục tiêu, số dòng bằng limit.\n"
                    + "- Câu trong options hợp tình hình lớp thì chọn (matches, score 2–3); còn lại viết mới (written).\n"
                    + "- Mỗi dòng ghi from = cụm từ NGUYÊN VĂN trong câu mục tiêu làm căn cứ.\n"
                    + "- Câu ngắn, cụ thể, phù hợp độ tuổi. Không nhắc tên trẻ. Không lặp dòng đang có.\n"
                    + "Chỉ trả JSON: {\"matches\":[{\"goalId\":1,\"key\":\"C12\",\"score\":3,\"from\":\"cụm nguyên văn\"}],"
                    + "\"written\":[{\"goalId\":1,\"text\":\"câu mới\",\"from\":\"cụm nguyên văn\"}]}";

    /** Ứng viên 4 nguồn cùng nhóm tuổi; trùng chữ giữ nguồn ưu tiên C > D > năm trước > RAG. Nguồn lỗi bị bỏ (AI-03). */
    public List<Candidate> candidates(Long ageGroupId, Long schoolYearId) {
        List<Candidate> all = new ArrayList<>();
        for (CurriculumContentResponse c : curriculumService.getContents(ageGroupId))
            if (c.id() != null && notBlank(c.text()))
                all.add(new Candidate("C" + c.id(), c.text().strip(), CURRICULUM_C, c.domainId(), null));
        for (CurriculumGoalResponse g : curriculumService.getGoals(ageGroupId)) {
            List<String> lines = g.contents() == null ? List.of() : g.contents();
            for (int i = 0; i < lines.size(); i++)
                if (g.id() != null && notBlank(lines.get(i)))
                    all.add(new Candidate(
                            "D" + g.id() + "_" + i, lines.get(i).strip(), CURRICULUM_D, g.domainId(), null));
        }
        try {
            var previous = previousYearService.previousYearContents(schoolYearId, ageGroupId);
            for (int i = 0; i < previous.size(); i++)
                all.add(new Candidate(
                        "Y" + i,
                        previous.get(i).text(),
                        PREVIOUS_YEAR,
                        previous.get(i).domainId(),
                        null));
        } catch (RuntimeException ex) {
            log.warn(
                    "[EduGoalContentMatcher]|candidates|previous|ERROR|type={}",
                    ex.getClass().getSimpleName());
        }
        try {
            for (EduRagRetriever.Hit h : ragRetriever.active(List.of("mt"))) {
                String[] parts = h.content().split("\n", 2);
                if (parts.length < 2) continue;
                String title = parts[0].replaceFirst("^YCCĐ [^:]*:\\s*", "").strip();
                String[] items = parts[1].replaceFirst("^\\s*Nội dung:\\s*", "").split(";");
                for (int i = 0; i < items.length; i++)
                    if (notBlank(items[i]))
                        all.add(new Candidate(
                                "R" + h.id() + "_" + i,
                                sentence(items[i]),
                                RAG,
                                null,
                                new EduAiResponse.RagRef(h.id(), clip(title, 120), null)));
            }
        } catch (RuntimeException ex) {
            log.warn(
                    "[EduGoalContentMatcher]|candidates|rag|ERROR|type={}",
                    ex.getClass().getSimpleName());
        }
        Map<String, Candidate> byText = new LinkedHashMap<>();
        for (Candidate c : all) {
            String k = c.domainId() + "|" + EduTextMatch.key(c.text());
            Candidate old = byText.get(k);
            if (old == null || PRIORITY.indexOf(c.source()) < PRIORITY.indexOf(old.source())) byText.put(k, c);
        }
        return new ArrayList<>(byText.values());
    }

    /** Chia theo lĩnh vực (tối đa {@link #GOALS_PER_CALL} mục tiêu một lần gọi); mỗi mục tiêu kèm top K ứng viên của nó. */
    public List<Ask> asks(List<Goal> goals, List<Candidate> all, String situation) {
        Map<Long, List<Goal>> byDomain = new LinkedHashMap<>();
        goals.forEach(g ->
                byDomain.computeIfAbsent(g.domainId(), d -> new ArrayList<>()).add(g));
        List<Ask> asks = new ArrayList<>();
        for (var e : byDomain.entrySet()) {
            // RAG không gắn lĩnh vực ⇒ vào mọi lĩnh vực; xếp hạng sẽ lọc
            List<Candidate> pool = all.stream()
                    .filter(c -> c.domainId() == null || Objects.equals(c.domainId(), e.getKey()))
                    .toList();
            List<Goal> list = e.getValue();
            for (int from = 0; from < list.size(); from += GOALS_PER_CALL) {
                List<Goal> part = list.subList(from, Math.min(list.size(), from + GOALS_PER_CALL));
                List<List<Integer>> top = ranker.topEach(
                        part.stream().map(Goal::text).toList(),
                        pool.stream().map(Candidate::text).toList(),
                        TOP_K * 2);
                Map<Long, List<Candidate>> options = new LinkedHashMap<>();
                List<Map<String, Object>> body = new ArrayList<>();
                for (int i = 0; i < part.size(); i++) {
                    Goal g = part.get(i);
                    Set<String> have = keys(g.have());
                    List<Candidate> mine = top.get(i).stream()
                            .map(pool::get)
                            .filter(c -> !have.contains(EduTextMatch.key(c.text())))
                            .limit(TOP_K)
                            .toList();
                    options.put(g.id(), mine);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("goalId", g.id());
                    item.put("text", g.text());
                    if (!g.replace().isEmpty()) item.put("current", g.replace());
                    if (g.limit() != Integer.MAX_VALUE) item.put("limit", g.limit());
                    item.put(
                            "options",
                            mine.stream()
                                    .map(c -> Map.of("key", c.key(), "text", c.text()))
                                    .toList());
                    body.add(item);
                }
                Map<String, Object> prompt = new LinkedHashMap<>();
                prompt.put("domain", nz(part.getFirst().domainName()));
                if (notBlank(situation)) prompt.put("classSituation", situation.strip());
                prompt.put("goals", body);
                asks.add(new Ask(
                        e.getKey(),
                        part.getFirst().domainName(),
                        part,
                        options,
                        objectMapper.writeValueAsString(prompt),
                        notBlank(situation)));
            }
        }
        return asks;
    }

    /**
     * Lọc output AI: key phải thuộc options của đúng mục tiêu, điểm ≥ 2, from có trong câu mục tiêu (bỏ qua khác dấu cách,
     * dấu gạch), không trùng nội dung đang có. Câu viết mới chỉ nhận khi mục tiêu không còn lựa chọn hợp lệ nào.
     */
    public List<EduAiResponse.GoalContentMatch> verify(Ask ask, JsonNode out) {
        Map<Long, Goal> goals = new LinkedHashMap<>();
        ask.goals().forEach(g -> goals.put(g.id(), g));
        Map<Long, Set<String>> taken = new LinkedHashMap<>();
        goals.values().forEach(g -> taken.put(g.id(), keys(g.have())));
        List<EduAiResponse.GoalContentMatch> result = new ArrayList<>();
        Set<Long> picked = new HashSet<>();
        for (JsonNode m : byScore(out.path("matches"))) {
            Goal g = goals.get(m.path("goalId").asLong(-1));
            if (g == null || m.path("score").asInt(0) < MIN_SCORE) continue;
            String key = m.path("key").asString("");
            String from = m.path("from").asString("").strip();
            Candidate c = ask.options().get(g.id()).stream()
                    .filter(x -> x.key().equals(key))
                    .findFirst()
                    .orElse(null);
            if (c == null
                    || count(result, g.id()) >= g.limit()
                    || !EduTextMatch.containsPhrase(g.text(), from)
                    || !taken.get(g.id()).add(EduTextMatch.key(c.text()))) continue;
            picked.add(g.id());
            result.add(new EduAiResponse.GoalContentMatch(g.id(), c.text(), c.source(), c.ref(), clip(from, FROM_MAX)));
        }
        Map<Long, Integer> written = new LinkedHashMap<>();
        for (JsonNode w : out.path("written")) {
            Goal g = goals.get(w.path("goalId").asLong(-1));
            // Gợi ý thường chỉ viết khi không chọn được ứng viên; viết lại theo tình hình lớp thì luôn nhận
            if (g == null || (!ask.rewrite() && picked.contains(g.id())) || count(result, g.id()) >= g.limit())
                continue;
            String text = sentence(clip(w.path("text").asString(""), TEXT_MAX));
            String from = w.path("from").asString("").strip();
            if (!notBlank(text)
                    || !EduTextMatch.containsPhrase(g.text(), from)
                    || written.getOrDefault(g.id(), 0) >= (ask.rewrite() ? g.limit() : WRITE_MAX)
                    || !taken.get(g.id()).add(EduTextMatch.key(text))) continue;
            written.merge(g.id(), 1, Integer::sum);
            result.add(new EduAiResponse.GoalContentMatch(g.id(), text, AI, null, clip(from, FROM_MAX)));
        }
        return result;
    }

    // Cắt theo limit phải giữ câu điểm cao: sắp theo điểm giảm dần, cùng điểm giữ thứ tự AI trả
    private static List<JsonNode> byScore(JsonNode matches) {
        List<JsonNode> list = new ArrayList<>();
        matches.forEach(list::add);
        list.sort(java.util.Comparator.comparingInt(
                        (JsonNode m) -> m.path("score").asInt(0))
                .reversed());
        return list;
    }

    private static long count(List<EduAiResponse.GoalContentMatch> matches, Long goalId) {
        return matches.stream().filter(m -> m.goalId().equals(goalId)).count();
    }

    private static Set<String> keys(List<String> texts) {
        Set<String> out = new HashSet<>();
        if (texts != null) texts.forEach(t -> out.add(EduTextMatch.key(t)));
        return out;
    }

    // Câu nội dung trong bảng: viết hoa chữ đầu, kết thúc bằng dấu chấm như mục C của Bộ
    static String sentence(String text) {
        String t = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) return t;
        t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        return t.matches(".*[.!?…]$") ? t : t + ".";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
