package com.carenest.service.observation.impl;

import com.carenest.integration.ai.AiClient;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.Participation;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Nháp nhận xét tuần do AI viết (AI-03): chỉ gửi số liệu tuần và tên mục tiêu, không họ tên, không mã trẻ; AI lỗi, tắt
 * hoặc trả từ chẩn đoán thì ném {@link IllegalStateException} để service viết theo mẫu câu.
 */
@Component
@RequiredArgsConstructor
class AiWeeklyCommentDraftGenerator implements WeeklyCommentDraftGenerator {
    private static final int MAX_TOKENS = 600;
    private static final String SYSTEM_PROMPT =
            "Bạn là giáo viên mầm non viết nhận xét tuần gửi phụ huynh. Viết tiếng Việt,"
                    + " 1–2 đoạn văn xuôi ngắn, giọng ấm áp, gọi trẻ là \"con\". Không chia mục, không tiêu đề, không gạch đầu dòng."
                    + " Chỉ dựa trên số liệu được cung cấp, không bịa thêm sự việc, không nhận xét hay kết luận về sức khỏe,"
                    + " không dùng từ chẩn đoán. Nêu tình hình đi học, vui chơi, ăn ngủ, điều con làm tốt, điều con còn lúng túng"
                    + " (nếu có), và kết bằng 1–2 câu gợi ý bố mẹ cùng con làm ở nhà.";

    private final AiClient aiClient;

    boolean isEnabled() {
        return aiClient.isEnabled();
    }

    @Override
    public String write(WeekFacts f) {
        if (!aiClient.isEnabled()) {
            throw new IllegalStateException("ai-disabled");
        }
        String text = aiClient.complete(new AiClient.AiRequest("weekly-comment", SYSTEM_PROMPT, prompt(f), MAX_TOKENS))
                .text();
        String clean = text == null ? "" : text.trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (clean.isEmpty()
                || DailyAssessmentServiceImpl.DIAGNOSTIC_TERMS.stream().anyMatch(lower::contains)) {
            throw new IllegalStateException("ai-unusable");
        }
        return clean;
    }

    private static String prompt(WeekFacts f) {
        StringBuilder b = new StringBuilder("Số liệu tuần của con:\n");
        b.append("- Đi học ")
                .append(f.presentDays())
                .append('/')
                .append(f.schoolDays())
                .append(" ngày học.\n");
        b.append("- Cảm xúc khi đến lớp (số ngày): ")
                .append(counts(f.emotion()))
                .append('\n');
        b.append("- Mức tham gia hoạt động (số ngày): ")
                .append(counts(f.participation()))
                .append('\n');
        b.append("- Ngủ trưa (số ngày): ").append(counts(f.nap())).append('\n');
        b.append("- Ăn hết suất trưa ")
                .append(f.lunchFinished())
                .append('/')
                .append(f.lunchDays())
                .append(" ngày.\n");
        b.append("- Mục tiêu đạt: ").append(goals(f.metGoals())).append('\n');
        b.append("- Mục tiêu còn lúng túng: ").append(goals(f.unmetGoals())).append('\n');
        b.append("- Cờ bé ngoan: ").append(f.flagCount());
        b.append(f.weeklyTicket() ? ", được phiếu Bé ngoan tuần.\n" : ".\n");
        return b.toString();
    }

    private static String counts(Map<?, Integer> m) {
        if (m.isEmpty()) {
            return "chưa ghi nhận";
        }
        StringBuilder b = new StringBuilder();
        m.forEach((k, v) -> b.append(label(k)).append(' ').append(v).append("; "));
        return b.toString().trim();
    }

    private static String label(Object k) {
        if (k instanceof Emotion e) {
            return switch (e) {
                case HAPPY -> "vui vẻ";
                case CALM -> "bình thường";
                case SAD -> "buồn";
                case UPSET -> "quấy khóc";
            };
        }
        if (k instanceof Participation p) {
            return switch (p) {
                case TICH_CUC -> "tích cực";
                case BINH_THUONG -> "bình thường";
                case CHUA_TICH_CUC -> "chưa tích cực";
            };
        }
        if (k instanceof NapLevel n) {
            return switch (n) {
                case NGU_TOT -> "ngủ tốt";
                case NGU_IT -> "ngủ ít";
                case KHONG_NGU -> "không ngủ";
            };
        }
        return String.valueOf(k);
    }

    private static String goals(java.util.List<String> names) {
        java.util.List<String> texts = names.stream()
                .map(TemplateWeeklyCommentDraftGenerator::goalText)
                .filter(t -> !t.isEmpty())
                .toList();
        return texts.isEmpty() ? "không có" : String.join("; ", texts);
    }
}
