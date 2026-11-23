package com.carenest.service.observation.impl;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiểm đầu ra AI của nhận xét kỳ (OBS-34, AI-10, OBS-17) trước khi lưu thành nháp: chỉ chấp nhận văn xuôi ngắn, không
 * từ chẩn đoán, và mọi con số phải có trong đầu vào (AI không tự tính). Không đạt ⇒ ném lỗi để dùng mẫu câu (AI-03).
 */
final class PeriodCommentGuard {

    static final int MIN_CHARS = 60;
    static final int MAX_CHARS = 700;

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern INTERNAL_CODE = Pattern.compile("(?i)\\bMT\\s*\\d+\\b");

    private PeriodCommentGuard() {}

    /** Trả nội dung đã chuẩn hóa và ghép tên gọi; ném {@link IllegalStateException} khi không dùng được. */
    static String check(String output, String userPrompt, String givenName) {
        String text = output == null ? "" : output.strip();
        if (text.length() < MIN_CHARS) {
            throw unusable("too-short");
        }
        if (text.length() > MAX_CHARS) {
            throw unusable("too-long");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (DailyAssessmentServiceImpl.DIAGNOSTIC_TERMS.stream().anyMatch(lower::contains)) {
            throw unusable("diagnostic-term");
        }
        if (INTERNAL_CODE.matcher(text).find() || lower.contains("chủ đề") || lower.contains("mục tiêu")) {
            throw unusable("internal-label");
        }
        if (text.contains("```") || text.contains("**") || text.lines().anyMatch(PeriodCommentGuard::isMarkup)) {
            throw unusable("markup");
        }
        Set<String> allowed = numbersIn(userPrompt);
        Matcher m = DIGITS.matcher(text);
        while (m.find()) {
            if (!allowed.contains(m.group())) {
                throw unusable("number-not-in-input");
            }
        }
        String named = text.replace(ChildTextScrubber.NAME_TOKEN, givenName == null ? "" : givenName)
                .replaceAll("[ \\t]+", " ")
                .strip();
        if (named.contains("{") || named.contains("}")) {
            throw unusable("placeholder");
        }
        return named;
    }

    static Set<String> numbersIn(String text) {
        Set<String> numbers = new HashSet<>();
        Matcher m = DIGITS.matcher(text);
        while (m.find()) {
            numbers.add(m.group());
        }
        return numbers;
    }

    private static boolean isMarkup(String line) {
        String t = line.stripLeading();
        return t.startsWith("#") || t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ");
    }

    private static IllegalStateException unusable(String reason) {
        return new IllegalStateException("ai-unusable:" + reason);
    }
}
