package com.carenest.service.eduplan.impl.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Đo bước tìm ứng viên trên dữ liệu thật: 482 đoạn MT của kho RAG là cặp "mục tiêu → nội dung" do trường soạn. Ẩn nội dung,
 * tìm lại trong toàn bộ nội dung cùng độ tuổi; recall@K = tỉ lệ nội dung đúng lọt top K. Chặn tụt so với cách chấm cũ.
 */
class EduTextMatchEvalTest {

    private static final int K = 15;

    record Gold(String goal, Set<Integer> answers) {}

    @Test
    void bm25RecallBeatsOldKeywordScoring() throws Exception {
        Map<String, List<String>> poolByAge = new LinkedHashMap<>();
        Map<String, List<Gold>> goldByAge = new LinkedHashMap<>();
        JsonMapper mapper = JsonMapper.builder().build();
        try (var in = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/edu/rag/chunks.jsonl"), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                JsonNode o = mapper.readTree(line);
                if (!"mt".equals(o.path("type").asString(""))) continue;
                String[] parts = o.path("text").asString("").split("\n", 2);
                if (parts.length < 2) continue;
                String goal = parts[0].replaceFirst("^YCCĐ [^:]*:\\s*", "");
                String age = o.path("meta").path("age").asString("");
                List<String> pool = poolByAge.computeIfAbsent(age, a -> new ArrayList<>());
                Set<Integer> answers = new HashSet<>();
                for (String item : parts[1].replaceFirst("^Nội dung:\\s*", "").split(";")) {
                    if (item.isBlank()) continue;
                    answers.add(pool.size());
                    pool.add(item.strip());
                }
                goldByAge.computeIfAbsent(age, a -> new ArrayList<>()).add(new Gold(goal, answers));
            }
        }
        double oldHit = 0, newHit = 0, total = 0;
        for (var e : goldByAge.entrySet()) {
            List<String> pool = poolByAge.get(e.getKey());
            for (Gold g : e.getValue()) {
                total += g.answers().size();
                oldHit += hits(top(oldScores(g.goal(), pool)), g.answers());
                newHit += hits(EduTextMatch.fuse(K, List.of(EduTextMatch.bm25(g.goal(), pool))), g.answers());
            }
        }
        double oldRecall = oldHit / total, newRecall = newHit / total;
        System.out.printf(
                "[EduTextMatchEval] answers=%d recall@%d old=%.3f bm25=%.3f%n", (int) total, K, oldRecall, newRecall);
        assertThat(newRecall).isGreaterThanOrEqualTo(oldRecall);
    }

    // Chép cách chấm từ khóa cũ của EduRagRetriever.byKeyword: tỉ lệ từ đơn + cặp từ của câu hỏi có trong đoạn
    private static double[] oldScores(String query, List<String> pool) {
        String[] words = oldNormalize(query).split(" ");
        Set<String> grams = new HashSet<>(List.of(words));
        for (int i = 0; i + 1 < words.length; i++) grams.add(words[i] + " " + words[i + 1]);
        double[] s = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            String c = " " + oldNormalize(pool.get(i)) + " ";
            s[i] = grams.stream().filter(g -> c.contains(" " + g + " ")).count() / (double) Math.max(1, grams.size());
        }
        return s;
    }

    private static String oldNormalize(String text) {
        return text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
    }

    private static List<Integer> top(double[] s) {
        return IntStream.range(0, s.length)
                .boxed()
                .filter(i -> s[i] > 0)
                .sorted((a, b) -> Double.compare(s[b], s[a]))
                .limit(K)
                .toList();
    }

    private static long hits(List<Integer> top, Set<Integer> answers) {
        return top.stream().filter(answers::contains).count();
    }
}
