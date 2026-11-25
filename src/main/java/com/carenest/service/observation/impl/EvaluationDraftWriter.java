package com.carenest.service.observation.impl;

import com.carenest.dto.observation.EvaluationDtos.StructuredSummary;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Bản nháp ngắn để giáo viên rà và công bố cho phụ huynh; số liệu chi tiết ở phần căn cứ riêng. */
final class EvaluationDraftWriter {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("M/yyyy");

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
        String when =
                switch (type) {
                    case YEAR -> "năm học vừa qua";
                    case SEMESTER -> "học kỳ vừa qua";
                    case MONTH -> "tháng " + MONTH.format(start);
                };
        if (st.observedDays() == 0 || presentDays == 0) {
            return "Trong " + when + ", cô chưa có đủ ghi nhận ở lớp để nhận xét cụ thể về " + name
                    + ". Cô sẽ tiếp tục theo dõi và trao đổi với gia đình.";
        }

        List<String> strengths = new ArrayList<>();
        if (mostly(st.participation(), Participation.TICH_CUC, st.observedDays())) {
            strengths.add("tham gia các hoạt động ở lớp tích cực");
        }
        if (st.behaviors().getOrDefault(ObservedBehavior.HOP_TAC_TOT, 0) > 0) {
            strengths.add("biết hợp tác với cô và các bạn");
        } else if (st.behaviors().getOrDefault(ObservedBehavior.GIUP_DO_BAN, 0) > 0) {
            strengths.add("biết giúp đỡ bạn");
        }
        if (mostly(st.emotion(), Emotion.HAPPY, st.observedDays())) {
            strengths.add("đến lớp vui vẻ");
        }
        String first = strengths.isEmpty()
                ? "Trong " + when + ", cô ghi nhận các hoạt động của bé " + name + " ở lớp."
                : "Trong " + when + ", bé " + name + " "
                        + String.join(" và ", strengths.subList(0, Math.min(2, strengths.size()))) + ".";
        String second;
        if (mealIssue(st)) {
            second = "Một số bữa con còn ăn ít; cô và gia đình cùng động viên con ăn đủ bữa.";
        } else if (napIssue(st)) {
            second = "Có hôm con ngủ trưa chưa tốt; cô và gia đình cùng giúp con duy trì giờ nghỉ đều đặn.";
        } else {
            second = "Cô sẽ tiếp tục theo dõi và chia sẻ với gia đình về những điều con làm được ở lớp.";
        }
        return first + "\n\n" + second;
    }

    private static boolean mealIssue(StructuredSummary st) {
        Map<EatingLevel, Integer> lunch = st.meals().getOrDefault(MealSession.LUNCH, Map.of());
        return lunch.getOrDefault(EatingLevel.AN_IT, 0) + lunch.getOrDefault(EatingLevel.KHONG_AN, 0) > 0;
    }

    private static boolean napIssue(StructuredSummary st) {
        return st.nap().getOrDefault(NapLevel.NGU_IT, 0) + st.nap().getOrDefault(NapLevel.KHONG_NGU, 0) > 0;
    }

    private static <E> boolean mostly(Map<E, Integer> counts, E value, int observedDays) {
        return counts.getOrDefault(value, 0) * 2 > observedDays;
    }

    private static String givenName(String fullName) {
        String[] parts = (fullName == null ? "" : fullName.trim()).split("\\s+");
        return String.join(" ", Arrays.copyOfRange(parts, Math.max(0, parts.length - 2), parts.length));
    }
}
