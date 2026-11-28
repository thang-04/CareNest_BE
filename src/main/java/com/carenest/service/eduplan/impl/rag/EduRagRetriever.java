package com.carenest.service.eduplan.impl.rag;

import com.carenest.dto.eduplan.EduAiResponse;
import com.carenest.entity.eduplan.EduRagChunk;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.EmbeddingClient;
import com.carenest.repository.eduplan.EduRagChunkRepository;
import com.carenest.repository.eduplan.EduRagChunkRepository.SimilarRow;
import com.carenest.utils.ApiCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bước R của RAG: tìm đoạn tham khảo gần câu hỏi. Có vector ⇒ cosine top 20; giáo án web (WEB) nhân 0,8 vì kém tin cậy
 * hơn tài liệu trường; khớp độ tuổi/mã trong câu hỏi được cộng nhẹ. Không lọc cứng theo tuổi/chủ đề/môn (Tiền chốt
 * 25/11). AI tắt, lỗi hoặc chưa có vector ⇒ tìm theo từ khóa (AI-03). Chỉ đọc kho đoạn công khai, không dữ liệu trẻ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EduRagRetriever {

    private static final String LOG_PREFIX = "[EduRagRetriever]";
    public static final double DEFAULT_MIN_SIM = 0.5;
    static final double WEB_FACTOR = 0.8;
    static final double HINT_BONUS = 0.05;
    private static final int CANDIDATES = 20;
    private static final int MAX_K = 20;
    private static final int QUERY_MAX = 500;

    private static final Pattern AGE_RANGE = Pattern.compile("(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s*(tuổi|tháng)");
    // Mã MT/YCCĐ dạng "MT12", "12.3": phải có chữ+số hoặc dấu chấm, tránh bắt số thường ("ném bằng 1 tay")
    private static final Pattern CODE = Pattern.compile("\\b(?:[\\p{L}]+\\d+(?:\\.\\d+)*|\\d+(?:\\.\\d+)+)\\b");

    private final EduRagChunkRepository repository;
    private final EmbeddingClient embeddingClient;
    private final EduRagIndexer indexer;
    private final ObjectMapper objectMapper;

    public record Hit(String id, String type, String source, String content, double sim, JsonNode meta) {}

    /** API thử tìm kiếm: {@code type} rỗng ⇒ mọi loại; loại lạ ⇒ 400. */
    public List<EduAiResponse.RagHit> search(String query, String type, Integer k) {
        Set<String> types;
        if (type == null || type.isBlank()) {
            types = EduRagSeedLoader.TYPES;
        } else if (EduRagSeedLoader.TYPES.contains(type.trim())) {
            types = Set.of(type.trim());
        } else {
            throw GlobalException.of(ApiCode.BAD_REQUEST);
        }
        int limit = k == null ? 5 : Math.clamp(k, 1, MAX_K);
        return retrieve(query, types, limit, DEFAULT_MIN_SIM).stream()
                .map(h -> new EduAiResponse.RagHit(
                        h.id(),
                        h.type(),
                        h.source(),
                        h.content(),
                        h.sim(),
                        new EduAiResponse.RagMeta(
                                text(h.meta(), "url"), text(h.meta(), "theme"), text(h.meta(), "age"))))
                .toList();
    }

    public List<Hit> retrieve(String query, Collection<String> types, int k, double minSim) {
        String q = query == null ? "" : query.strip();
        if (q.length() > QUERY_MAX) {
            q = q.substring(0, QUERY_MAX);
        }
        if (q.isEmpty() || types.isEmpty()) {
            return List.of();
        }
        List<Hit> candidates = byVector(q, types);
        boolean vector = !candidates.isEmpty();
        if (!vector) {
            candidates = byKeyword(q, types);
        }
        List<Hit> hits = rank(q, candidates, k, minSim);
        log.info("{}|retrieve|vector={}|candidates={}|hits={}", LOG_PREFIX, vector, candidates.size(), hits.size());
        return hits;
    }

    private List<Hit> byVector(String query, Collection<String> types) {
        if (!embeddingClient.isAvailable()) {
            return List.of();
        }
        try {
            // Còn đoạn chưa có vector ⇒ embed tiếp trong ngân sách của lượt tìm (lượt khác đang chạy thì bỏ qua)
            indexer.indexStale(embeddingClient.budget());
            float[] vector = embeddingClient
                    .embed(List.of(query), embeddingClient.budget())
                    .getFirst();
            List<SimilarRow> rows =
                    repository.similarTo(EduRagIndexer.literal(vector), embeddingClient.modelName(), types, CANDIDATES);
            return rows.stream()
                    .map(r -> new Hit(
                            r.getId(),
                            r.getChunkType(),
                            r.getSource(),
                            r.getContent(),
                            r.getSim() == null ? 0 : r.getSim(),
                            meta(r.getMeta())))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("{}|byVector|ERROR|type={}", LOG_PREFIX, ex.getClass().getSimpleName());
            return List.of();
        }
    }

    // ponytail: quét ~1.000 đoạn trong bộ nhớ; chuyển sang pg_trgm/full-text nếu kho lớn lên nhiều
    List<Hit> byKeyword(String query, Collection<String> types) {
        Set<String> grams = grams(normalize(query));
        if (grams.isEmpty()) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (EduRagChunk chunk : repository.findByStatusAndChunkTypeIn("ACTIVE", types)) {
            String content = " " + normalize(chunk.getContent()) + " ";
            long matched =
                    grams.stream().filter(g -> content.contains(" " + g + " ")).count();
            if (matched > 0) {
                hits.add(new Hit(
                        chunk.getId(),
                        chunk.getChunkType(),
                        chunk.getSource(),
                        chunk.getContent(),
                        (double) matched / grams.size(),
                        meta(chunk.getMeta())));
            }
        }
        return hits;
    }

    /** Điểm cuối = sim × 0,8 nếu WEB, + thưởng khớp tuổi/mã; bỏ dưới {@code minSim}; lấy top k. */
    static List<Hit> rank(String query, List<Hit> candidates, int k, double minSim) {
        String ageHint = ageHint(query);
        Set<String> codes = codes(query);
        return candidates.stream()
                .map(h -> new Hit(h.id(), h.type(), h.source(), h.content(), score(h, ageHint, codes), h.meta()))
                .filter(h -> h.sim() >= minSim)
                .sorted(Comparator.comparingDouble(Hit::sim).reversed())
                .limit(k)
                .toList();
    }

    static double score(Hit hit, String ageHint, Set<String> codes) {
        double score = "WEB".equals(hit.source()) ? hit.sim() * WEB_FACTOR : hit.sim();
        if (ageHint != null && ageHint.equals(ageDigits(text(hit.meta(), "age")))) {
            score += HINT_BONUS;
        }
        String code = (text(hit.meta(), "code") + " " + text(hit.meta(), "yccd")).toLowerCase(Locale.ROOT);
        if (codes.stream().anyMatch(c -> code.contains(c))) {
            score += HINT_BONUS;
        }
        return score;
    }

    /** Độ tuổi trong câu hỏi, rút về chữ số ("4-5 tuổi" ⇒ "45", "lớp lá" ⇒ "56"); null khi không có. */
    static String ageHint(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        Matcher m = AGE_RANGE.matcher(q);
        if (m.find()) {
            return m.group(1) + m.group(2);
        }
        if (q.contains("nhà trẻ")) return "2436";
        if (q.contains("lớp mầm") || q.contains("mẫu giáo bé")) return "34";
        if (q.contains("chồi") || q.contains("mẫu giáo nhỡ")) return "45";
        if (q.contains("lớp lá") || q.contains("mẫu giáo lớn")) return "56";
        return null;
    }

    static String ageDigits(String age) {
        String digits = age.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    static Set<String> codes(String query) {
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = CODE.matcher(query.toLowerCase(Locale.ROOT));
        while (m.find()) {
            codes.add(m.group());
        }
        return codes;
    }

    /** Từ đơn + cặp từ liền nhau của câu hỏi; điểm từ khóa = tỉ lệ số gram có trong đoạn. */
    static Set<String> grams(String normalized) {
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split(" ");
        Set<String> grams = new LinkedHashSet<>(List.of(words));
        for (int i = 0; i + 1 < words.length; i++) {
            grams.add(words[i] + " " + words[i + 1]);
        }
        return grams;
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private JsonNode meta(String json) {
        try {
            return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (RuntimeException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static String text(JsonNode meta, String field) {
        return meta == null ? "" : meta.path(field).asString("");
    }
}
