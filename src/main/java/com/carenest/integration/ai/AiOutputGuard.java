package com.carenest.integration.ai;

import com.carenest.integration.ai.dto.SummaryDraftContent;
import com.carenest.integration.ai.dto.SummaryDraftContent.EvidencedPoint;
import com.carenest.integration.ai.dto.SummaryDraftInput;
import com.carenest.integration.ai.dto.SummaryDraftInput.ReferenceSnippet;
import com.carenest.utils.enums.AiErrorReason;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Kiểm tra output LLM bằng code (ADR-0007: rule do code enforce, không chỉ dựa vào prompt). Output không qua guard
 * thì không bao giờ được lưu. Thông báo lỗi không chứa nội dung output để không lọt dữ liệu trẻ vào log.
 */
class AiOutputGuard {

    static final int MAX_SUMMARY_LENGTH = 1500;
    static final int MAX_POINT_LENGTH = 300;
    static final int MAX_POINTS = 5;

    // Từ ngữ chẩn đoán y khoa/tâm lý bị cấm (HLT-04, mục 7 yêu cầu AI); so khớp sau khi lower-case
    private static final List<String> BANNED_TERMS = List.of(
            "tự kỷ",
            "tăng động",
            "giảm chú ý",
            "rối loạn",
            "chậm phát triển",
            "khuyết tật",
            "trầm cảm",
            "chẩn đoán",
            "bệnh lý",
            "autis",
            "adhd",
            "asperger",
            "disorder",
            "diagnos",
            "depress");

    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

    private final ObjectMapper objectMapper;

    AiOutputGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param raw nội dung message LLM trả về
     * @param input facts đã gửi, dùng để đối chiếu evidence và nguồn
     * @param inputJson input đã serialize; mọi con số trong output phải xuất hiện ở đây
     */
    SummaryDraftContent check(String raw, SummaryDraftInput input, String inputJson) {
        SummaryDraftContent content = parse(raw);
        List<EvidencedPoint> strengths = orEmpty(content.strengths());
        List<EvidencedPoint> followUps = orEmpty(content.areasForFollowUp());
        List<Long> sources = orEmpty(content.sources());

        requireValid(content.summary() != null && !content.summary().isBlank(), "summary is blank");
        requireValid(content.summary().length() <= MAX_SUMMARY_LENGTH, "summary too long");
        requireValid(strengths.size() <= MAX_POINTS && followUps.size() <= MAX_POINTS, "too many points");

        Set<String> allowedEvidence = allowedEvidence(input);
        Stream.concat(strengths.stream(), followUps.stream()).forEach(point -> checkPoint(point, allowedEvidence));

        Set<Long> allowedSources = orEmpty(input.references()).stream()
                .map(ReferenceSnippet::chunkId)
                .collect(Collectors.toSet());
        requireValid(allowedSources.containsAll(sources), "source not in references");

        String allText = Stream.concat(
                        Stream.of(content.summary()),
                        Stream.concat(strengths.stream(), followUps.stream()).map(EvidencedPoint::text))
                .collect(Collectors.joining("\n"));
        requireValid(findBannedTerm(allText) == null, "diagnostic term");
        requireValid(numbersOf(inputJson).containsAll(numbersOf(allText)), "number not in facts");

        return new SummaryDraftContent(content.summary().strip(), strengths, followUps, sources);
    }

    static String findBannedTerm(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return BANNED_TERMS.stream().filter(lower::contains).findFirst().orElse(null);
    }

    private SummaryDraftContent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("empty output");
        }
        try {
            return objectMapper.readValue(stripCodeFence(raw), SummaryDraftContent.class);
        } catch (JacksonException ex) {
            throw invalid("output is not valid JSON");
        }
    }

    private static void checkPoint(EvidencedPoint point, Set<String> allowedEvidence) {
        requireValid(point != null && point.text() != null && !point.text().isBlank(), "blank point");
        requireValid(point.text().length() <= MAX_POINT_LENGTH, "point too long");
        List<String> evidence = orEmpty(point.evidenceIds());
        requireValid(!evidence.isEmpty(), "point without evidence");
        requireValid(allowedEvidence.containsAll(evidence), "evidence not in input");
    }

    private static Set<String> allowedEvidence(SummaryDraftInput input) {
        Set<String> refs = new HashSet<>();
        orEmpty(input.criteria()).forEach(criteria -> refs.add(criteria.ref()));
        orEmpty(input.notes()).forEach(note -> refs.add(note.ref()));
        return refs;
    }

    private static Set<String> numbersOf(String text) {
        Set<String> numbers = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            // Bỏ số 0 đứng đầu để "05" trong ngày tháng khớp với "5" trong câu văn
            String digits = matcher.group().replaceFirst("^0+(?=\\d)", "");
            numbers.add(digits);
        }
        return numbers;
    }

    // Model suy luận (Qwen3, MiniMax…) có thể trả <think>…</think> trước JSON; một số model bọc JSON trong ```json```
    private static String stripCodeFence(String raw) {
        String trimmed = THINK_BLOCK.matcher(raw).replaceAll("").strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        return firstNewline < 0 || lastFence <= firstNewline ? trimmed : trimmed.substring(firstNewline + 1, lastFence);
    }

    private static void requireValid(boolean condition, String reason) {
        if (!condition) {
            throw invalid(reason);
        }
    }

    private static AiClientException invalid(String reason) {
        return new AiClientException(AiErrorReason.OUTPUT_INVALID, "AI output rejected: " + reason, null);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }
}
