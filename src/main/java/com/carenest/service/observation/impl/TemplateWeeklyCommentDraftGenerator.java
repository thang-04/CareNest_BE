package com.carenest.service.observation.impl;

import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.Participation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Nháp nhận xét tuần theo mẫu câu (chạy được khi AI tắt): 1–2 đoạn văn xuôi về đi học, ăn ngủ, điều con làm tốt, điều con
 * còn lúng túng và gợi ý cho gia đình. Chỉ mô tả điều cô ghi nhận, không kết luận sức khỏe.
 */
@Component
class TemplateWeeklyCommentDraftGenerator implements WeeklyCommentDraftGenerator {
    private static final int GOALS_IN_SENTENCE = 2;

    @Override
    public String write(WeekFacts f) {
        List<String> first = new ArrayList<>();
        first.add(attendance(f));
        String daily = daily(f);
        if (!daily.isEmpty()) {
            first.add(daily);
        }
        String met = join(f.metGoals());
        if (!met.isEmpty()) {
            first.add("Trong các hoạt động, con làm tốt " + met + ".");
        }
        if (f.flagCount() > 0) {
            first.add(
                    f.weeklyTicket()
                            ? "Con được " + f.flagCount() + " cờ bé ngoan và nhận phiếu Bé ngoan của tuần."
                            : "Con được " + f.flagCount() + " cờ bé ngoan.");
        }
        List<String> second = new ArrayList<>();
        String unmet = join(f.unmetGoals());
        if (!unmet.isEmpty()) {
            second.add("Con còn lúng túng ở " + unmet + ", bố mẹ có thể cùng con ôn lại ở nhà qua trò chơi nhẹ nhàng.");
        }
        second.add("Bố mẹ hãy trò chuyện với con về những điều con đã học trong tuần để con thêm tự tin nhé.");
        return String.join(" ", first) + "\n\n" + String.join(" ", second);
    }

    private static String attendance(WeekFacts f) {
        if (f.presentDays() == 0) {
            return "Tuần này con chưa đi học ngày nào.";
        }
        String days = f.presentDays() >= f.schoolDays()
                ? "Tuần này con đi học đều cả " + f.presentDays() + " ngày"
                : "Tuần này con đi học " + f.presentDays() + "/" + f.schoolDays() + " ngày";
        return days + (mostly(f.emotion(), Emotion.HAPPY, f.presentDays()) ? ", vào lớp vui vẻ." : ".");
    }

    private static String daily(WeekFacts f) {
        List<String> parts = new ArrayList<>();
        if (mostly(f.participation(), Participation.TICH_CUC, f.presentDays())) {
            parts.add("tham gia hoạt động tích cực");
        }
        if (f.lunchDays() > 0) {
            parts.add(
                    f.lunchFinished() == f.lunchDays()
                            ? "ăn hết suất trưa các ngày"
                            : "ăn hết suất trưa " + f.lunchFinished() + "/" + f.lunchDays() + " ngày");
        }
        if (mostly(f.nap(), NapLevel.NGU_TOT, f.presentDays())) {
            parts.add("ngủ trưa ngon");
        }
        return parts.isEmpty() ? "" : "Con " + String.join(", ", parts) + ".";
    }

    // Phần lớn số ngày đi học có ghi nhận này
    private static <E> boolean mostly(Map<E, Integer> counts, E value, int days) {
        return days > 0 && counts.getOrDefault(value, 0) * 2 > days;
    }

    private static String join(List<String> goals) {
        List<String> picked = goals.stream()
                .map(TemplateWeeklyCommentDraftGenerator::goalText)
                .filter(g -> !g.isEmpty())
                .limit(GOALS_IN_SENTENCE)
                .toList();
        return picked.isEmpty() ? "" : "\"" + String.join("\", \"", picked) + "\"";
    }

    // Tên mục tiêu dạng "MT12 – Nội dung": phụ huynh đọc nội dung, bỏ mã MT
    static String goalText(String name) {
        int dash = name.indexOf(" – ");
        if (dash < 0) {
            return "";
        }
        String text = (dash >= 0 ? name.substring(dash + 3) : name).trim();
        return text.length() > 1 ? Character.toLowerCase(text.charAt(0)) + text.substring(1) : text;
    }
}
