package com.carenest.service.observation.impl;

import com.carenest.dto.observation.EvaluationDtos.GoalCount;
import com.carenest.dto.observation.EvaluationDtos.StructuredSummary;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dựng phần người dùng của prompt nhận xét kỳ (OBS-34): số liệu do code đếm và nhận xét của GV đã lọc tên, ngày sinh
 * (AI-04, AI-08). Không có họ tên, mã trẻ, ghi chú sức khỏe; AI chỉ được dùng đúng các số trong chuỗi này.
 */
final class PeriodCommentPrompt {

    // Trần để vừa hạn mức token của provider và để chọn đều theo thời gian khi kỳ dài
    static final int MAX_FINALS = 8;
    static final int MAX_WEEKLY = 12;
    static final int MAX_DAILY = 20;
    static final int MAX_NOTE_CHARS = 250;
    static final int MAX_GOALS = 8;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Map<MealSession, String> MEALS =
            Map.of(MealSession.MORNING, "bữa sáng", MealSession.LUNCH, "bữa trưa", MealSession.AFTERNOON, "bữa chiều");
    private static final Map<Emotion, String> EMOTION = Map.of(
            Emotion.HAPPY, "vui vẻ", Emotion.CALM, "bình thường", Emotion.SAD, "buồn", Emotion.UPSET, "quấy khóc");
    private static final Map<NapLevel, String> NAP =
            Map.of(NapLevel.NGU_TOT, "ngủ tốt", NapLevel.NGU_IT, "ngủ ít", NapLevel.KHONG_NGU, "không ngủ");
    private static final Map<Participation, String> PARTICIPATION = Map.of(
            Participation.TICH_CUC, "tích cực",
            Participation.BINH_THUONG, "bình thường",
            Participation.CHUA_TICH_CUC, "chưa tích cực");
    private static final Map<ObservedBehavior, String> BEHAVIOR = Map.of(
            ObservedBehavior.HOP_TAC_TOT, "hợp tác tốt",
            ObservedBehavior.GIUP_DO_BAN, "giúp đỡ bạn",
            ObservedBehavior.CHUA_HOP_TAC, "chưa hợp tác",
            ObservedBehavior.TRANH_GIANH, "tranh giành với bạn",
            ObservedBehavior.KHONG_TAP_TRUNG, "không tập trung",
            ObservedBehavior.RUT_RE, "rụt rè",
            ObservedBehavior.KHAC, "khác");

    private PeriodCommentPrompt() {}

    static String render(PeriodCommentFacts f) {
        StringBuilder b = new StringBuilder();
        b.append("Kỳ nhận xét: ").append(period(f)).append(".\n\n");
        b.append("Số liệu kỳ:\n");
        b.append("- Đi học ").append(f.presentDays()).append(" ngày.\n");
        StructuredSummary st = f.structured();
        b.append("- Cô đánh giá hằng ngày ").append(st.observedDays()).append(" ngày.\n");
        b.append("- Cờ bé ngoan: ").append(f.flagCount()).append(".\n");
        if (st.observedDays() == 0) {
            b.append("- Kỳ này chưa có đánh giá hằng ngày nào được ghi nhận.\n");
        } else {
            counts(b, "Cảm xúc (số ngày)", st.emotion(), EMOTION);
            counts(b, "Tham gia hoạt động (số ngày)", st.participation(), PARTICIPATION);
            counts(b, "Ngủ trưa (số ngày)", st.nap(), NAP);
            counts(b, "Hành vi (số lượt)", st.behaviors(), BEHAVIOR);
            meals(b, st);
            goals(b, st);
        }
        notes(b, "Nhận xét kỳ nhỏ hơn cô đã xác nhận", f, f.finals(), MAX_FINALS);
        notes(b, "Nhận xét tuần cô đã gửi phụ huynh", f, f.weekly(), MAX_WEEKLY);
        notes(b, "Nhận xét hằng ngày của cô", f, f.daily(), MAX_DAILY);
        return b.toString();
    }

    private static String period(PeriodCommentFacts f) {
        if (f.type() == EvaluationPeriodType.MONTH) {
            return "tháng " + MONTH.format(f.start());
        }
        String range = "từ " + DAY.format(f.start()) + " đến " + DAY.format(f.end());
        return (f.type() == EvaluationPeriodType.SEMESTER ? "học kỳ " : "cả năm học ") + range;
    }

    private static <E extends Enum<E>> void counts(
            StringBuilder b, String title, Map<E, Integer> counts, Map<E, String> labels) {
        String text = counts.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<E, Integer>comparingByValue().reversed())
                .map(e -> labels.get(e.getKey()) + " " + e.getValue())
                .collect(Collectors.joining("; "));
        if (!text.isEmpty()) {
            b.append("- ").append(title).append(": ").append(text).append(".\n");
        }
    }

    private static void meals(StringBuilder b, StructuredSummary st) {
        List<String> parts = new ArrayList<>();
        for (MealSession session : MealSession.values()) {
            Map<EatingLevel, Integer> c = st.meals().get(session);
            if (c == null || c.isEmpty()) {
                continue;
            }
            parts.add(MEALS.get(session) + " (ăn hết " + c.getOrDefault(EatingLevel.AN_HET, 0) + ", ăn ít "
                    + c.getOrDefault(EatingLevel.AN_IT, 0) + ", không ăn "
                    + c.getOrDefault(EatingLevel.KHONG_AN, 0) + ")");
        }
        if (!parts.isEmpty()) {
            b.append("- Ăn uống (số ngày): ").append(String.join("; ", parts)).append(".\n");
        }
    }

    private static void goals(StringBuilder b, StructuredSummary st) {
        List<String> met = new ArrayList<>();
        List<String> support = new ArrayList<>();
        st.goals().byGoal().entrySet().stream()
                .sorted(Map.Entry.<String, GoalCount>comparingByValue((x, y) -> Integer.compare(
                                y.dat() + y.chuaDat() + y.canCoGang(), x.dat() + x.chuaDat() + x.canCoGang()))
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> {
                    String text = TemplateWeeklyCommentDraftGenerator.goalText(e.getKey());
                    GoalCount c = e.getValue();
                    int need = c.chuaDat() + c.canCoGang();
                    if (text.isEmpty()) {
                        return;
                    }
                    if (need > c.dat() && support.size() < MAX_GOALS) {
                        support.add(text);
                    } else if (need <= c.dat() && c.dat() > 0 && met.size() < MAX_GOALS) {
                        met.add(text);
                    }
                });
        if (!met.isEmpty()) {
            b.append("- Mục tiêu bé đạt: ").append(String.join("; ", met)).append(".\n");
        }
        if (!support.isEmpty()) {
            b.append("- Mục tiêu cần hỗ trợ thêm: ")
                    .append(String.join("; ", support))
                    .append(".\n");
        }
    }

    // Nhận xét của GV: lọc tên / ngày sinh, cắt độ dài, chọn đều theo thời gian khi vượt trần
    private static void notes(
            StringBuilder b, String title, PeriodCommentFacts f, List<PeriodCommentFacts.Note> notes, int max) {
        List<PeriodCommentFacts.Note> picked = sample(notes, max);
        List<String> lines = new ArrayList<>();
        for (PeriodCommentFacts.Note n : picked) {
            String text = ChildTextScrubber.scrub(n.text(), f.childFullName(), f.dateOfBirth());
            if (text.isEmpty()) {
                continue;
            }
            lines.add("- " + n.label() + ": " + cut(text));
        }
        if (!lines.isEmpty()) {
            b.append('\n')
                    .append(title)
                    .append(":\n")
                    .append(String.join("\n", lines))
                    .append('\n');
        }
    }

    static <T> List<T> sample(List<T> items, int max) {
        if (items.size() <= max) {
            return items;
        }
        List<T> out = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            out.add(items.get((int) Math.round(i * (items.size() - 1.0) / (max - 1))));
        }
        return out;
    }

    private static String cut(String text) {
        return text.length() <= MAX_NOTE_CHARS
                ? text
                : text.substring(0, MAX_NOTE_CHARS).trim() + "…";
    }
}
