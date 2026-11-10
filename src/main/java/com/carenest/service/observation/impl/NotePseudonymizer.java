package com.carenest.service.observation.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ẩn danh ghi chú trước khi gửi ra AI bên ngoài (AI-04): tên trẻ đang nhận xét ⇒ "Trẻ", tên trẻ khác cùng lớp ⇒
 * "bạn", số điện thoại/email ⇒ "[đã ẩn]". Họ tên đầy đủ so khớp không phân biệt hoa thường; tên gọi (từ cuối)
 * phải viết hoa để không thay nhầm từ thường (vd. tên "An" và cụm "an toàn").
 */
final class NotePseudonymizer {

    static final String CHILD_ALIAS = "Trẻ";
    static final String CLASSMATE_ALIAS = "bạn";
    static final String REDACTED = "[đã ẩn]";

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?84|0)(?:[\\s.-]?\\d){8,10}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final String NOT_LETTER_BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_LETTER_AFTER = "(?![\\p{L}\\p{N}])";

    private final List<Replacement> replacements = new ArrayList<>();

    NotePseudonymizer(String childFullName, Collection<String> classmateFullNames) {
        addName(childFullName, CHILD_ALIAS);
        classmateFullNames.forEach(name -> addName(name, CLASSMATE_ALIAS));
        // Chuỗi dài thay trước để "Nguyễn Văn An" không bị thay dở thành "Nguyễn Văn Trẻ"
        replacements.sort(Comparator.comparingInt(Replacement::length).reversed());
    }

    String apply(String text) {
        if (text == null) {
            return null;
        }
        String result = EMAIL.matcher(text).replaceAll(REDACTED);
        result = PHONE.matcher(result).replaceAll(REDACTED);
        for (Replacement replacement : replacements) {
            result = replacement.pattern().matcher(result).replaceAll(Matcher.quoteReplacement(replacement.alias()));
        }
        return result;
    }

    private void addName(String fullName, String alias) {
        if (fullName == null || fullName.isBlank()) {
            return;
        }
        String trimmed = fullName.strip().replaceAll("\\s+", " ");
        replacements.add(new Replacement(
                Pattern.compile(
                        NOT_LETTER_BEFORE + Pattern.quote(trimmed) + NOT_LETTER_AFTER,
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                alias,
                trimmed.length()));
        String givenName = trimmed.substring(trimmed.lastIndexOf(' ') + 1);
        if (givenName.length() >= 2 && !givenName.equals(trimmed)) {
            replacements.add(new Replacement(
                    Pattern.compile(NOT_LETTER_BEFORE + Pattern.quote(givenName) + NOT_LETTER_AFTER),
                    alias,
                    givenName.length()));
        }
    }

    private record Replacement(Pattern pattern, String alias, int length) {}
}
