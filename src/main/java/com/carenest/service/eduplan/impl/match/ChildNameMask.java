package com.carenest.service.eduplan.impl.match;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Thay tên trẻ của lớp trong câu giáo viên gõ bằng "trẻ" (EDU-115, AI-04). Khớp họ tên đầy đủ, hai chữ cuối (không phân
 * biệt hoa thường) và riêng tên gọi khi viết hoa chữ đầu. ponytail: tên gọi trùng từ thường viết hoa đầu câu (vd "An
 * toàn") cũng bị thay — chấp nhận vì thay thừa không lộ dữ liệu.
 */
public final class ChildNameMask {

    private ChildNameMask() {}

    public static String mask(String text, Collection<String> fullNames) {
        if (text == null || text.isBlank()) return "";
        String out = Normalizer.normalize(text, Normalizer.Form.NFC);
        Set<String> phrases = new LinkedHashSet<>();
        Set<String> given = new LinkedHashSet<>();
        for (String name : fullNames) {
            if (name == null || name.isBlank()) continue;
            String[] w = Normalizer.normalize(name, Normalizer.Form.NFC).strip().split("\\s+");
            phrases.add(String.join(" ", w));
            if (w.length > 2) phrases.add(w[w.length - 2] + " " + w[w.length - 1]);
            given.add(w[w.length - 1]);
        }
        // Cụm dài thay trước để không còn sót họ
        for (String p : phrases.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList()) out = replaceWord(out, p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        for (String g : given) {
            String capital = Character.toUpperCase(g.charAt(0)) + g.substring(1);
            out = replaceWord(out, capital, 0);
        }
        return out;
    }

    private static String replaceWord(String text, String word, int flags) {
        String regex = "(?<![\\p{L}\\p{M}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{M}\\p{N}])";
        return Pattern.compile(regex, flags).matcher(text).replaceAll("trẻ");
    }
}
