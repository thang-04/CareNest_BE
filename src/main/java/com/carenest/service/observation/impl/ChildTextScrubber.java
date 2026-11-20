package com.carenest.service.observation.impl;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lọc nhận xét tự do của GV trước khi gửi AI (AI-04, AI-08): thay họ tên / tên gọi của chính trẻ bằng ký hiệu, bỏ ngày
 * sinh và số điện thoại. Chỉ biết tên của trẻ đang nhận xét; tên bạn khác do GV rà ở bản nháp (OBS-34).
 */
final class ChildTextScrubber {

    static final String NAME_TOKEN = "{TEN}";

    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d .\\-]{7,}\\d");
    private static final Pattern DOUBLE_TOKEN = Pattern.compile("(\\{TEN}[\\s,]*){2,}");

    private ChildTextScrubber() {}

    /** Tên gọi: hai chữ cuối của họ tên (như bản nháp mẫu câu). */
    static String givenName(String fullName) {
        List<String> parts = words(fullName);
        return String.join(" ", parts.subList(Math.max(0, parts.size() - 2), parts.size()));
    }

    static String scrub(String text, String fullName, LocalDate dateOfBirth) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String clean = Normalizer.normalize(text, Normalizer.Form.NFC);
        clean = PHONE.matcher(clean).replaceAll(" ");
        if (dateOfBirth != null) {
            clean = removeBirthDate(clean, dateOfBirth);
        }
        clean = replaceNames(clean, fullName);
        clean = DOUBLE_TOKEN.matcher(clean).replaceAll(NAME_TOKEN + " ");
        return clean.replaceAll("\\s+", " ").trim();
    }

    // Cụm họ tên đầy đủ, cụm tên gọi (2 chữ cuối), rồi tên cuối đứng riêng; họ và tên đệm đứng riêng giữ nguyên (hay
    // trùng từ thường)
    private static String replaceNames(String text, String fullName) {
        List<String> parts = words(fullName);
        if (parts.isEmpty()) {
            return text;
        }
        List<String> phrases = new ArrayList<>();
        phrases.add(String.join(" ", parts));
        phrases.add(givenName(fullName));
        String last = parts.getLast();
        if (last.length() >= 2) {
            phrases.add(last);
        }
        phrases.sort(Comparator.comparingInt(String::length).reversed());
        String folded = fold(text);
        List<int[]> spans = new ArrayList<>();
        for (String phrase : phrases) {
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])"
                    + Pattern.quote(fold(phrase)).replace(" ", "\\E\\s+\\Q")
                    + "(?![\\p{L}\\p{N}])");
            Matcher m = p.matcher(folded);
            while (m.find()) {
                spans.add(new int[] {m.start(), m.end()});
            }
        }
        spans.sort(Comparator.comparingInt((int[] s) -> s[0]).thenComparingInt(s -> -s[1]));
        StringBuilder out = new StringBuilder();
        int at = 0;
        for (int[] s : spans) {
            if (s[0] < at) {
                continue;
            }
            out.append(text, at, s[0]).append(NAME_TOKEN);
            at = s[1];
        }
        return out.append(text, at, text.length()).toString();
    }

    private static String removeBirthDate(String text, LocalDate dob) {
        String d = Integer.toString(dob.getDayOfMonth());
        String m = Integer.toString(dob.getMonthValue());
        String y = Integer.toString(dob.getYear());
        String dd = String.format("%02d", dob.getDayOfMonth());
        String mm = String.format("%02d", dob.getMonthValue());
        // d/m/yyyy, dd/mm/yyyy và các dấu / - . ; có hoặc không có năm
        Pattern p = Pattern.compile("(?<!\\d)0?" + d + "[/\\-.]0?" + m + "(?:[/\\-.]" + y + ")?(?!\\d)|" + y + "[/\\-.]"
                + mm + "[/\\-.]" + dd);
        return p.matcher(text).replaceAll(" ");
    }

    // Chữ thường, bỏ dấu; mỗi ký tự NFC giữ một ký tự nên vị trí khớp với văn bản gốc
    private static String fold(String text) {
        StringBuilder b = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'đ' || c == 'Đ') {
                b.append('d');
                continue;
            }
            String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            b.append(Character.toLowerCase(decomposed.charAt(0)));
        }
        return b.toString();
    }

    private static List<String> words(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return List.of();
        }
        return List.of(
                Normalizer.normalize(fullName.trim(), Normalizer.Form.NFC).split("\\s+"));
    }
}
