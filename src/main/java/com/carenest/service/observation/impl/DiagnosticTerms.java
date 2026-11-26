package com.carenest.service.observation.impl;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Lọc thuật ngữ chẩn đoán trong chữ giáo viên nhập (OBS-17), so khớp theo từng từ: chữ gõ KHÔNG dấu ("tu ky") vẫn bị
 * bắt; chữ có dấu phải đúng dấu của thuật ngữ, nên "thiếu năng lượng" không bị chặn nhầm vì "thiểu năng". Riêng
 * "kỷ"/"kỉ"/"kĩ" coi là một. Hạ chữ thường, bỏ qua ký tự không phải chữ hoặc số.
 */
final class DiagnosticTerms {
    /** Thuật ngữ chẩn đoán; thêm dạng viết khác của cùng một từ ở đây. */
    private static final List<String> EXTRA =
            List.of("autism", "asd", "bại não", "bai nao", "tâm thần", "tam than", "down");

    private DiagnosticTerms() {}

    /** Các thuật ngữ (dạng gốc có dấu trong danh sách) xuất hiện trong {@code text}. */
    static List<String> matches(String text) {
        List<String> words = words(text);
        if (words.isEmpty()) {
            return List.of();
        }
        return java.util.stream.Stream.concat(DailyAssessmentServiceImpl.DIAGNOSTIC_TERMS.stream(), EXTRA.stream())
                .filter(term -> containsTerm(words, words(term)))
                .toList();
    }

    /** Chữ thường, NFC, tách theo ký tự không phải chữ/số (dấu gõ rời được ghép lại trước khi tách). */
    private static List<String> words(String text) {
        if (text == null) {
            return List.of();
        }
        String composed = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(composed.split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static boolean containsTerm(List<String> words, List<String> term) {
        for (int start = 0; start + term.size() <= words.size(); start++) {
            boolean all = true;
            for (int i = 0; i < term.size() && all; i++) {
                all = sameWord(words.get(start + i), term.get(i));
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** Cùng từ khi giống hệt, hoặc gõ không dấu; có dấu khác thì là từ khác. "ky"/"ki" coi là một. */
    private static boolean sameWord(String word, String term) {
        if (word.equals(term)) {
            return true;
        }
        String foldedWord = fold(word);
        String foldedTerm = fold(term);
        if (foldedTerm.equals("ky") && foldedWord.equals("ki")) {
            return true;
        }
        return foldedWord.equals(foldedTerm) && foldedWord.equals(word);
    }

    /** Bỏ dấu, đ→d. */
    private static String fold(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
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
}
