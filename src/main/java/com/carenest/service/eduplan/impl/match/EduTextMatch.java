package com.carenest.service.eduplan.impl.match;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Phần so chữ dùng chung cho các luồng AI ghép kế hoạch giáo dục (EDU-106, EDU-114, EDU-115): chuẩn hóa câu, kiểm căn cứ,
 * xếp ứng viên bằng BM25 trên âm tiết + cặp âm tiết, trộn với thứ hạng vector bằng RRF. Không gọi mạng.
 */
public final class EduTextMatch {

    // Tham số BM25 chuẩn; RRF_K = 60 theo Cormack 2009
    static final double K1 = 1.2;
    static final double B = 0.75;
    static final int RRF_K = 60;

    private EduTextMatch() {}

    /** Chữ thường, NFC, mọi loại gạch/khoảng trắng về một dấu cách, bỏ dấu câu ở hai đầu cụm. */
    public static String normalize(String text) {
        if (text == null) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        s = s.replaceAll("[\\p{Pd}\\u2212]", " - ").replaceAll("\\s+", " ");
        return s.strip();
    }

    /** Khóa so trùng / so chứa: bỏ hết ký tự không phải chữ, số (giữ dấu tiếng Việt). */
    public static String key(String text) {
        return normalize(text).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    /** {@code phrase} có nằm trong {@code text} không, bỏ qua khác biệt hoa thường, dấu cách, dấu gạch, dấu câu. */
    public static boolean containsPhrase(String text, String phrase) {
        String p = key(phrase);
        return !p.isEmpty() && (" " + key(text) + " ").contains(" " + p + " ");
    }

    /** Âm tiết + cặp âm tiết liền nhau; dùng cho BM25. */
    static List<String> terms(String text) {
        String k = key(text);
        if (k.isEmpty()) return List.of();
        String[] words = k.split(" ");
        List<String> out = new ArrayList<>(words.length * 2);
        for (int i = 0; i < words.length; i++) {
            out.add(words[i]);
            if (i + 1 < words.length) out.add(words[i] + " " + words[i + 1]);
        }
        return out;
    }

    /** Điểm BM25 của từng tài liệu với câu hỏi; IDF tính trên chính tập tài liệu (vài trăm câu). */
    public static double[] bm25(String query, List<String> docs) {
        int n = docs.size();
        double[] scores = new double[n];
        if (n == 0) return scores;
        List<Map<String, Integer>> tf = new ArrayList<>(n);
        Map<String, Integer> df = new HashMap<>();
        double total = 0;
        int[] len = new int[n];
        for (int i = 0; i < n; i++) {
            List<String> t = terms(docs.get(i));
            len[i] = t.size();
            total += t.size();
            Map<String, Integer> m = new HashMap<>();
            t.forEach(x -> m.merge(x, 1, Integer::sum));
            m.keySet().forEach(x -> df.merge(x, 1, Integer::sum));
            tf.add(m);
        }
        double avg = total / n;
        Set<String> q = new java.util.LinkedHashSet<>(terms(query));
        for (String term : q) {
            Integer d = df.get(term);
            if (d == null) continue;
            double idf = Math.log(1 + (n - d + 0.5) / (d + 0.5));
            for (int i = 0; i < n; i++) {
                Integer f = tf.get(i).get(term);
                if (f == null) continue;
                double norm = avg == 0 ? 1 : len[i] / avg;
                scores[i] += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * norm));
            }
        }
        return scores;
    }

    /** Thứ hạng (0 = tốt nhất) theo điểm giảm dần; điểm ≤ 0 xếp sau cùng như nhau. */
    static int[] ranks(double[] scores) {
        Integer[] order = IntStream.range(0, scores.length).boxed().toArray(Integer[]::new);
        java.util.Arrays.sort(order, Comparator.comparingDouble((Integer i) -> -scores[i]));
        int[] rank = new int[scores.length];
        for (int r = 0; r < order.length; r++) rank[order[r]] = scores[order[r]] > 0 ? r : Integer.MAX_VALUE;
        return rank;
    }

    /**
     * Trộn các bảng điểm bằng Reciprocal Rank Fusion; bảng null bị bỏ (vd. vector khi AI tắt). Trả chỉ số tài liệu theo thứ
     * tự tốt nhất, tối đa {@code k}, chỉ tài liệu có điểm dương ở ít nhất một bảng.
     */
    public static List<Integer> fuse(int k, Collection<double[]> tables) {
        Map<Integer, Double> fused = new LinkedHashMap<>();
        for (double[] t : tables) {
            if (t == null) continue;
            int[] r = ranks(t);
            for (int i = 0; i < r.length; i++)
                if (r[i] != Integer.MAX_VALUE) fused.merge(i, 1.0 / (RRF_K + r[i] + 1), Double::sum);
        }
        return fused.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(k)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Cosine của câu hỏi với từng tài liệu; vector thiếu ⇒ 0. */
    public static double[] cosine(float[] query, List<float[]> docs) {
        double[] out = new double[docs.size()];
        if (query == null) return out;
        double qn = norm(query);
        for (int i = 0; i < docs.size(); i++) {
            float[] d = docs.get(i);
            if (d == null || d.length != query.length || qn == 0) continue;
            double dot = 0;
            for (int j = 0; j < d.length; j++) dot += query[j] * d[j];
            double dn = norm(d);
            out[i] = dn == 0 ? 0 : dot / (qn * dn);
        }
        return out;
    }

    private static double norm(float[] v) {
        double s = 0;
        for (float x : v) s += x * x;
        return Math.sqrt(s);
    }
}
