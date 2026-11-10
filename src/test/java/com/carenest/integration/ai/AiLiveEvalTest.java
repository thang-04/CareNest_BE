package com.carenest.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.integration.ai.dto.SummaryDraftInput;
import com.carenest.integration.ai.dto.SummaryDraftResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eval chạy tay với provider thật (không chạy trong CI): bật bằng AI_LIVE_EVAL=true và các biến AI_* như .env.
 * Dữ liệu là facts giả lập (src/test/resources/ai-eval). Guard của ExternalAiClient là tiêu chí chấm: không bịa
 * evidence/số liệu, không từ chẩn đoán, đúng schema. Ghi kết quả từng case để so sánh model.
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "AI_LIVE_EVAL", matches = "true")
class AiLiveEvalTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * Chạy mỗi case AI_LIVE_EVAL_RUNS lần (mặc định 1) qua đúng pipeline thật (prompt + guard), ghi kết quả thô ra
     * target/ai-eval/runs-*.json. Không assert pass/fail ở đây: tỷ lệ qua guard là một chỉ số, được chấm cùng các
     * luật khác bởi scripts/ai-eval/evaluate_summaries.py.
     */
    @Test
    void recordSummaryDraftsForEvaluation() throws IOException {
        ExternalAiClient client = new ExternalAiClient(properties(), MAPPER, new PromptTemplateLoader());
        int runs = Integer.parseInt(env("AI_LIVE_EVAL_RUNS", "1"));
        List<Map<String, Object>> records = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            for (EvalCase evalCase : loadCases()) {
                pauseForRateLimit();
                records.add(runCase(client, evalCase, run));
            }
        }
        writeReport(records);
        assertThat(records).hasSize(runs * loadCases().size());
    }

    private static Map<String, Object> runCase(ExternalAiClient client, EvalCase evalCase, int run) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("case", evalCase.name());
        record.put("run", run);
        long startedAt = System.nanoTime();
        try {
            SummaryDraftResult result = client.draftChildSummary(evalCase.input());
            record.put("status", "GUARD_PASS");
            record.put("content", result.content());
            log.info("EVAL PASS | {} | run {}", evalCase.name(), run);
        } catch (AiClientException ex) {
            record.put("status", "FAILED");
            record.put("reason", ex.getReason().name());
            record.put("message", ex.getMessage());
            log.warn("EVAL FAIL | {} | run {} | {} | {}", evalCase.name(), run, ex.getReason(), ex.getMessage());
        }
        record.put("latencyMs", (System.nanoTime() - startedAt) / 1_000_000);
        return record;
    }

    @Test
    void embeddingRanksRelatedTextHigher() {
        ExternalEmbeddingClient client = new ExternalEmbeddingClient(properties());
        List<float[]> vectors = client.embedBatch(List.of(
                "Chỉ số phát triển cảm xúc và quan hệ xã hội của trẻ 48-60 tháng",
                "Trẻ biết thể hiện cảm xúc phù hợp, chơi hòa thuận với bạn bè",
                "Quy trình nhập kho thực phẩm và kiểm tra hóa đơn nhà cung cấp"));
        double related = cosine(vectors.get(0), vectors.get(1));
        double unrelated = cosine(vectors.get(0), vectors.get(2));
        log.info(
                "EMBED | model={} | dims={} | related={} | unrelated={}",
                client.modelName(),
                vectors.getFirst().length,
                related,
                unrelated);
        assertThat(related).isGreaterThan(unrelated);
    }

    // Ghi UTF-8 ra target/ai-eval/ để GV/nhóm đọc nội dung nháp (console Windows làm vỡ dấu tiếng Việt)
    private static void writeReport(List<Map<String, Object>> report) throws IOException {
        Path file = Path.of(
                "target",
                "ai-eval",
                "runs-" + env("AI_LLM_MODEL", "model").replaceAll("[^A-Za-z0-9.-]", "_") + "-"
                        + ExternalAiClient.SUMMARY_PROMPT.replace('/', '_') + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        log.info("EVAL REPORT | {}", file.toAbsolutePath());
    }

    private static void pauseForRateLimit() {
        try {
            Thread.sleep(Duration.ofSeconds(Long.parseLong(env("AI_LIVE_EVAL_PAUSE_SECONDS", "4"))));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<EvalCase> loadCases() throws IOException {
        try (InputStream in = AiLiveEvalTest.class.getResourceAsStream("/ai-eval/summary-cases.json")) {
            return MAPPER.readValue(in, new TypeReference<List<EvalCase>>() {});
        }
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static AiProperties properties() {
        return new AiProperties(
                true,
                new AiProperties.Llm(
                        env("AI_LLM_PROVIDER", "live"),
                        env("AI_LLM_BASE_URL", ""),
                        env("AI_LLM_MODEL", ""),
                        env("AI_LLM_API_KEY", ""),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        1,
                        Duration.ofSeconds(5),
                        env("AI_LLM_REASONING_EFFORT", null)),
                new AiProperties.Embedding(
                        env("AI_EMBEDDING_PROVIDER", "live"),
                        env("AI_EMBEDDING_BASE_URL", ""),
                        env("AI_EMBEDDING_MODEL", ""),
                        env("AI_EMBEDDING_API_KEY", ""),
                        Integer.parseInt(env("AI_EMBEDDING_DIMENSIONS", "0")),
                        32,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30)),
                new AiProperties.Rag(4, 0.3, "HAI_PHONG"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    // category, expect: dùng bởi scripts/ai-eval/evaluate_summaries.py
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EvalCase(String name, SummaryDraftInput input) {}
}
