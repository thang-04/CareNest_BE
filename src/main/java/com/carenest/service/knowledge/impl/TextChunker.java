package com.carenest.service.knowledge.impl;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Cắt văn bản thành chunk theo đoạn văn, gộp đoạn ngắn tới tối đa {@link #MAX_CHARS} ký tự. Đoạn dài hơn bị cắt
 * theo cửa sổ trượt có chồng lấn {@link #OVERLAP_CHARS} ký tự, ưu tiên cắt tại khoảng trắng.
 */
final class TextChunker {

    static final int MAX_CHARS = 800;
    static final int OVERLAP_CHARS = 100;

    private TextChunker() {}

    static String clean(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\u00A0]+", " ")
                .strip();
    }

    static List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawParagraph : clean(text).split("\\n\\s*\\n")) {
            String paragraph = rawParagraph.strip();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (paragraph.length() > MAX_CHARS) {
                flush(current, chunks);
                chunks.addAll(splitLong(paragraph));
                continue;
            }
            if (!current.isEmpty() && current.length() + 2 + paragraph.length() > MAX_CHARS) {
                flush(current, chunks);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        flush(current, chunks);
        return chunks;
    }

    private static List<String> splitLong(String paragraph) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(paragraph.length(), start + MAX_CHARS);
            if (end < paragraph.length()) {
                int space = paragraph.lastIndexOf(' ', end);
                if (space > start + OVERLAP_CHARS) {
                    end = space;
                }
            }
            parts.add(paragraph.substring(start, end).strip());
            if (end == paragraph.length()) {
                break;
            }
            start = Math.max(start + 1, end - OVERLAP_CHARS);
        }
        return parts;
    }

    private static void flush(StringBuilder current, List<String> chunks) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }
}
