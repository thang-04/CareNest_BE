package com.carenest.service.observation.impl;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Lọc thuật ngữ chẩn đoán trong chữ giáo viên nhập (OBS-17): so khớp sau khi chuẩn hóa, bỏ dấu, hạ chữ thường để "tự
 * kỷ", "tự kĩ", "tu ky", "TỰ KỶ" và dạng gõ rời dấu đều bị bắt. Đã kiểm theo ranh giới từ để bớt chặn nhầm.
 */
final class DiagnosticTerms {
    /** Thuật ngữ chẩn đoán; thêm dạng viết khác của cùng một từ ở đây. */
    private static final List<String> EXTRA = List.of("autism", "asd", "bai nao", "tam than", "down");

    private DiagnosticTerms() {}

    /** Chữ đã chuẩn hóa: NFD, bỏ dấu, đ→d, hạ chữ thường. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String folded = Normalizer.normalize(text, Normalizer.Form.NFD)
                .chars()
                .filter(c -> Character.getType(c) != Character.NON_SPACING_MARK)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return Normalizer.normalize(folded, Normalizer.Form.NFC);
    }

    /** Các thuật ngữ (dạng gốc có dấu trong danh sách) xuất hiện trong {@code text}. */
    static List<String> matches(String text) {
        String normalized = " " + normalize(text).replaceAll("[^a-z0-9]+", " ") + " ";
        return java.util.stream.Stream.concat(DailyAssessmentServiceImpl.DIAGNOSTIC_TERMS.stream(), EXTRA.stream())
                .filter(term -> {
                    String key =
                            " " + normalize(term).replaceAll("[^a-z0-9]+", " ").trim() + " ";
                    return normalized.contains(key) || foldedVariant(normalized, key);
                })
                .toList();
    }

    /** Ném lỗi nếu chữ nhận xét có thuật ngữ chẩn đoán (OBS-17); dùng cho ghi chú gửi phụ huynh. */
    static void requireClean(String text) {
        matches(text).stream().findFirst().ifPresent(term -> {
            throw com.carenest.exception.GlobalException.of(
                    com.carenest.utils.ApiCode.EVALUATION_DIAGNOSTIC_TERM, term);
        });
    }

    static boolean containsAny(String text) {
        return !matches(text).isEmpty();
    }

    // "tự kỉ" cùng nghĩa "tự kỷ": coi i/y ở cuối từ là một
    private static boolean foldedVariant(String normalized, String key) {
        return key.contains(" tu ky ") && normalized.contains(" tu ki ");
    }
}
