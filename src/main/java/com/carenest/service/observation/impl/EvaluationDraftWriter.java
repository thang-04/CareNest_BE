package com.carenest.service.observation.impl;

import com.carenest.dto.observation.EvaluationDtos.GoalCount;
import com.carenest.dto.observation.EvaluationDtos.StructuredSummary;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bản nháp đánh giá tháng / cuối năm viết theo mẫu từ số liệu đánh giá hằng ngày (chưa nối dịch vụ AI). Câu chữ chỉ mô tả
 * điều cô ghi nhận, không kết luận sức khỏe, không dùng từ chẩn đoán; mục tiêu chưa dạy không tính là cần hỗ trợ.
 */
final class EvaluationDraftWriter {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Set<ObservedBehavior> POSITIVE =
            Set.of(ObservedBehavior.HOP_TAC_TOT, ObservedBehavior.GIUP_DO_BAN);
    private static final Map<HealthSymptom, String> SIGNS = Map.of(
            HealthSymptom.FEVER, "sốt",
            HealthSymptom.COUGH, "ho",
            HealthSymptom.RUNNY_NOSE, "sổ mũi",
            HealthSymptom.FLU, "cảm cúm",
            HealthSymptom.TIRED, "mệt mỏi",
            HealthSymptom.OTHER, "khác");
    private static final Map<MealSession, String> MEALS =
            Map.of(MealSession.MORNING, "bữa sáng", MealSession.LUNCH, "bữa trưa", MealSession.AFTERNOON, "bữa chiều");
    private static final Map<NapLevel, String> NAP =
            Map.of(NapLevel.NGU_TOT, "ngủ tốt", NapLevel.NGU_IT, "ngủ ít", NapLevel.KHONG_NGU, "không ngủ");
    private static final Map<Emotion, String> EMOTION = Map.of(
            Emotion.HAPPY, "vui vẻ", Emotion.CALM, "bình thường", Emotion.SAD, "buồn", Emotion.UPSET, "quấy khóc");
    private static final Map<ObservedBehavior, String> BEHAVIOR = Map.of(
            ObservedBehavior.HOP_TAC_TOT, "hợp tác tốt",
            ObservedBehavior.GIUP_DO_BAN, "giúp đỡ bạn",
            ObservedBehavior.CHUA_HOP_TAC, "chưa hợp tác",
            ObservedBehavior.TRANH_GIANH, "tranh giành / đánh bạn",
            ObservedBehavior.KHONG_TAP_TRUNG, "không tập trung",
            ObservedBehavior.RUT_RE, "rụt rè",
            ObservedBehavior.KHAC, "khác");
    private static final Map<Participation, String> PARTICIPATION = Map.of(
            Participation.TICH_CUC, "tích cực",
            Participation.BINH_THUONG, "bình thường",
            Participation.CHUA_TICH_CUC, "chưa tích cực");

    private EvaluationDraftWriter() {}

    static String write(
            EvaluationPeriodType type,
            String childFullName,
            LocalDate start,
            int presentDays,
            int flagCount,
            StructuredSummary st,
            int confirmedMonths) {
        String name = givenName(childFullName);
        boolean year = type == EvaluationPeriodType.YEAR;
        String when = year ? "năm học vừa qua" : "tháng " + MONTH.format(start);
        List<String> lines = new ArrayList<>();
        lines.add("Trong " + when + ", bé " + name + " đi học " + presentDays + " ngày, cô đánh giá hằng ngày "
                + st.observedDays() + " ngày và bé nhận " + flagCount + " cờ bé ngoan.");
        if (year && confirmedMonths > 0) {
            lines.add("Bản nháp tổng hợp cùng " + confirmedMonths + " đánh giá tháng cô đã xác nhận.");
        }
        if (st.observedDays() == 0) {
            lines.add("Kỳ này chưa có đánh giá hằng ngày nào được ghi nhận, cô bổ sung nhận xét về bé.");
            return String.join("\n", lines);
        }
        List<String> met = new ArrayList<>();
        List<String> notYet = new ArrayList<>();
        st.goals().byGoal().forEach((goal, c) -> {
            String shortName = goal.split(" – ")[0];
            if (support(c) > c.dat()) {
                notYet.add(shortName);
            } else if (c.dat() > 0) {
                met.add(shortName);
            }
        });
        if (!met.isEmpty()) {
            lines.add("Kiến thức, kỹ năng: bé đạt " + String.join(", ", met) + ".");
        }
        if (!notYet.isEmpty()) {
            lines.add("Cần tiếp tục hỗ trợ: " + String.join(", ", notYet)
                    + ", cô tạo thêm cơ hội để bé luyện tập trong các hoạt động.");
        }
        if (met.isEmpty() && notYet.isEmpty()) {
            lines.add("Kiến thức, kỹ năng: kỳ này chưa có mục tiêu giáo án được đánh giá.");
        }
        Emotion mood = top(st.emotion());
        lines.add("Cảm xúc: phần lớn thời gian bé " + (mood == null ? "bình thường" : EMOTION.get(mood)) + ".");
        lines.addAll(detailLines(st));
        List<String> tips = new ArrayList<>();
        if (!notYet.isEmpty()) {
            tips.add("cùng bé ôn lại các nội dung cần hỗ trợ ở trên");
        }
        if (mealIssue(st)) {
            tips.add("khuyến khích bé ăn đủ bữa");
        }
        if (napIssue(st) > 0) {
            tips.add("cho bé ngủ đủ giấc buổi tối");
        }
        tips.add("khen ngợi khi bé cố gắng");
        lines.add("Gợi ý phối hợp với gia đình: " + String.join(", ", tips) + ".");
        return String.join("\n", lines);
    }

    private static List<String> detailLines(StructuredSummary st) {
        List<String> lines = new ArrayList<>();
        var h = st.health();
        if (h.days() > 0) {
            String signs = list(h.signDays(), SIGNS);
            lines.add(
                    h.normalDays() == h.days()
                            ? "Sức khỏe: " + h.days() + " ngày cô ghi nhận bé bình thường."
                            : "Sức khỏe: " + h.normalDays() + "/" + h.days() + " ngày bình thường"
                                    + (signs.isEmpty() ? "" : "; biểu hiện " + signs + " ngày")
                                    + ". Gia đình theo dõi thêm ở nhà.");
        }
        List<String> meals = new ArrayList<>();
        st.meals().forEach((session, c) -> {
            int little = c.getOrDefault(EatingLevel.AN_IT, 0);
            int none = c.getOrDefault(EatingLevel.KHONG_AN, 0);
            if (little + none > 0) {
                List<String> parts = new ArrayList<>();
                if (little > 0) {
                    parts.add("ăn ít " + little);
                }
                if (none > 0) {
                    parts.add("không ăn " + none);
                }
                meals.add(MEALS.get(session) + " " + String.join(", ", parts) + " ngày");
            }
        });
        lines.add(
                meals.isEmpty()
                        ? "Ăn: bé ăn hết suất ở các bữa đã ghi nhận."
                        : "Ăn: " + String.join("; ", meals) + ".");
        int nap = napIssue(st);
        if (nap > 0) {
            Map<NapLevel, Integer> issues = new EnumMap<>(NapLevel.class);
            st.nap().forEach((k, v) -> {
                if (k != NapLevel.NGU_TOT) {
                    issues.put(k, v);
                }
            });
            lines.add("Ngủ trưa: " + list(issues, NAP) + " ngày.");
        } else {
            lines.add("Ngủ trưa: bé ngủ tốt.");
        }
        Map<ObservedBehavior, Integer> good = new EnumMap<>(ObservedBehavior.class);
        Map<ObservedBehavior, Integer> concern = new EnumMap<>(ObservedBehavior.class);
        st.behaviors().forEach((b, n) -> (POSITIVE.contains(b) ? good : concern).put(b, n));
        List<String> behaviour = new ArrayList<>();
        if (!list(good, BEHAVIOR).isEmpty()) {
            behaviour.add("tích cực – " + list(good, BEHAVIOR));
        }
        if (!list(concern, BEHAVIOR).isEmpty()) {
            behaviour.add("cần lưu ý – " + list(concern, BEHAVIOR));
        }
        if (!behaviour.isEmpty()) {
            lines.add("Hành vi: " + String.join("; ", behaviour) + " lượt.");
        }
        String part = list(st.participation(), PARTICIPATION);
        if (!part.isEmpty()) {
            lines.add("Tham gia hoạt động: " + part + " ngày.");
        }
        return lines;
    }

    private static int support(GoalCount c) {
        return c.chuaDat() + c.canCoGang();
    }

    private static boolean mealIssue(StructuredSummary st) {
        return st.meals().values().stream()
                .anyMatch(c -> c.getOrDefault(EatingLevel.AN_IT, 0) + c.getOrDefault(EatingLevel.KHONG_AN, 0) > 0);
    }

    private static int napIssue(StructuredSummary st) {
        return st.nap().getOrDefault(NapLevel.NGU_IT, 0) + st.nap().getOrDefault(NapLevel.KHONG_NGU, 0);
    }

    private static <E extends Enum<E>> E top(Map<E, Integer> counts) {
        return counts.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static <E extends Enum<E>> String list(Map<E, Integer> counts, Map<E, String> labels) {
        return String.join(
                ", ",
                counts.entrySet().stream()
                        .filter(e -> e.getValue() > 0)
                        .sorted(Map.Entry.<E, Integer>comparingByValue().reversed())
                        .map(e -> labels.get(e.getKey()) + " " + e.getValue())
                        .toList());
    }

    /** Tên gọi: 2 chữ cuối của họ tên. */
    private static String givenName(String fullName) {
        String[] parts = (fullName == null ? "" : fullName.trim()).split("\\s+");
        return String.join(" ", Arrays.copyOfRange(parts, Math.max(0, parts.length - 2), parts.length));
    }
}
