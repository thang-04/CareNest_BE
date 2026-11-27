package com.carenest.service.eduplan.impl.match;

import com.carenest.integration.ai.EmbeddingClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bước tìm ứng viên dùng chung (EDU-106/114/115): mỗi câu hỏi lấy top K tài liệu theo BM25 trộn cosine (RRF). Vector của
 * câu danh mục/kho mẫu giữ trong bộ nhớ theo model + chữ, embed một lần; AI tắt, lỗi hoặc hết ngân sách ⇒ chỉ BM25 (AI-03).
 * Chỉ nhận chữ chương trình, mẫu công khai và câu kế hoạch, không dữ liệu trẻ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EduCandidateRanker {

    private static final String LOG_PREFIX = "[EduCandidateRanker]";
    // ponytail: xóa sạch khi đầy thay cho LRU; danh mục Bộ + kho mẫu ~5.000 câu nên hiếm khi chạm trần
    static final int CACHE_MAX = 20_000;

    private final EmbeddingClient embeddingClient;
    private final Map<String, float[]> cache = new ConcurrentHashMap<>();

    /** Chỉ số tài liệu tốt nhất cho từng câu hỏi (cùng thứ tự đầu vào), tối đa {@code k} mỗi câu. */
    public List<List<Integer>> topEach(List<String> queries, List<String> docs, int k) {
        List<float[]> qv = null;
        List<float[]> dv = null;
        if (!queries.isEmpty() && !docs.isEmpty() && embeddingClient.isAvailable()) {
            try {
                Instant until = Instant.now().plus(embeddingClient.budget());
                dv = vectors(docs, until);
                qv = embeddingClient.embed(queries, remaining(until));
            } catch (RuntimeException ex) {
                log.warn(
                        "{}|topEach|vector|ERROR|type={}",
                        LOG_PREFIX,
                        ex.getClass().getSimpleName());
                qv = null;
            }
        }
        List<List<Integer>> out = new ArrayList<>(queries.size());
        for (int i = 0; i < queries.size(); i++) {
            double[] lexical = EduTextMatch.bm25(queries.get(i), docs);
            double[] semantic = qv == null ? null : EduTextMatch.cosine(qv.get(i), dv);
            List<Integer> top = new ArrayList<>(EduTextMatch.fuse(k, java.util.Arrays.asList(lexical, semantic)));
            // Không có vector thì câu khớp nghĩa nhưng khác chữ bị điểm 0: điền thêm theo thứ tự gốc cho đủ K để AI vẫn
            // thấy
            for (int d = 0; d < docs.size() && top.size() < k; d++) if (!top.contains(d)) top.add(d);
            out.add(top);
        }
        return out;
    }

    private List<float[]> vectors(List<String> docs, Instant until) {
        String model = embeddingClient.modelName();
        List<String> missing = new ArrayList<>(new LinkedHashSet<>(
                docs.stream().filter(d -> !cache.containsKey(model + "|" + d)).toList()));
        if (!missing.isEmpty()) {
            List<float[]> got = embeddingClient.embed(missing, remaining(until));
            if (cache.size() + missing.size() > CACHE_MAX) cache.clear();
            for (int i = 0; i < missing.size(); i++) cache.put(model + "|" + missing.get(i), got.get(i));
        }
        return docs.stream().map(d -> cache.get(model + "|" + d)).toList();
    }

    private static Duration remaining(Instant until) {
        Duration left = Duration.between(Instant.now(), until);
        return left.isNegative() ? Duration.ZERO : left;
    }
}
