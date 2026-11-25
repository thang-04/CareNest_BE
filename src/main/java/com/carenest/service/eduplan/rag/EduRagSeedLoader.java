package com.carenest.service.eduplan.rag;

import com.carenest.entity.eduplan.EduRagChunk;
import com.carenest.repository.eduplan.EduRagChunkRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Nạp kho đoạn RAG lúc khởi động: mỗi file seed nạp một lần khi bảng chưa có đoạn nào của nguồn đó. Chỉ lưu văn bản +
 * hash, chưa gọi AI; vector do {@link EduRagIndexer} embed lười ở luồng nền (AI tắt thì bỏ qua, AI-03).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EduRagSeedLoader implements ApplicationRunner {

    private static final String LOG_PREFIX = "[EduRagSeedLoader]";
    static final Set<String> TYPES = Set.of("mt", "activity", "lesson");
    // Lần index đầu ~1.000 đoạn (~20 lô; Gemini miễn phí ~100 lượt/phút): cho ngân sách dài ở luồng nền
    private static final Duration STARTUP_INDEX_BUDGET = Duration.ofMinutes(20);

    private final EduRagChunkRepository repository;
    private final EduRagIndexer indexer;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        loadIfMissing("SEED", "edu/rag/chunks.jsonl");
        loadIfMissing("WEB", "edu/rag/web_chunks.jsonl");
        // Gặp giới hạn tần suất thì chờ 1 phút rồi embed tiếp, tới khi hết kho hoặc hết ngân sách
        Thread.ofVirtual()
                .name("edu-rag-index")
                .start(() -> indexer.indexStale(STARTUP_INDEX_BUDGET, Duration.ofSeconds(60)));
    }

    void loadIfMissing(String source, String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists() || repository.existsBySource(source)) {
            log.info("{}|load|source={}|skipped|exists={}", LOG_PREFIX, source, resource.exists());
            return;
        }
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<EduRagChunk> chunks = parse(source, reader.lines().toList());
            repository.saveAll(chunks);
            log.info("{}|load|source={}|count={}", LOG_PREFIX, source, chunks.size());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Mỗi dòng {id,type,text,meta}; dòng rỗng bỏ qua, type lạ hoặc thiếu id/text ⇒ lỗi để phát hiện file hỏng sớm. */
    List<EduRagChunk> parse(String source, List<String> lines) {
        List<EduRagChunk> chunks = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = objectMapper.readTree(line);
            String id = node.path("id").asString("");
            String type = node.path("type").asString("");
            String text = node.path("text").asString("");
            if (id.isBlank() || text.isBlank() || !TYPES.contains(type)) {
                throw new IllegalStateException("Invalid RAG chunk line in " + source + ": id=" + id);
            }
            JsonNode meta = node.path("meta");
            String metaJson = meta.isObject() ? objectMapper.writeValueAsString(meta) : "{}";
            chunks.add(EduRagChunk.seed(id, type, source, text, metaJson, EduRagIndexer.sha256(text)));
        }
        return chunks;
    }
}
