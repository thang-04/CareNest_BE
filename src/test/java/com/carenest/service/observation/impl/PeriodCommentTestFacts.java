package com.carenest.service.observation.impl;

import com.carenest.dto.observation.EvaluationDtos.GoalCount;
import com.carenest.dto.observation.EvaluationDtos.GoalSummary;
import com.carenest.dto.observation.EvaluationDtos.HealthSummary;
import com.carenest.dto.observation.EvaluationDtos.StructuredSummary;
import com.carenest.service.observation.impl.PeriodCommentFacts.Note;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Dữ liệu giả dựng sẵn cho test nhận xét kỳ: tên và ngày sinh giả, không có dữ liệu trẻ thật. */
final class PeriodCommentTestFacts {

    static final String FULL_NAME = "Nguyễn Minh Khôi";
    static final LocalDate DOB = LocalDate.of(2022, 3, 14);

    private PeriodCommentTestFacts() {}

    static PeriodCommentFacts month(List<Note> daily) {
        return new PeriodCommentFacts(
                EvaluationPeriodType.MONTH,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                FULL_NAME,
                DOB,
                18,
                4,
                structured(18),
                List.of(),
                List.of(),
                daily);
    }

    static StructuredSummary structured(int observed) {
        Map<MealSession, Map<EatingLevel, Integer>> meals = new EnumMap<>(MealSession.class);
        meals.put(MealSession.LUNCH, Map.of(EatingLevel.AN_HET, 15, EatingLevel.AN_IT, 3));
        return new StructuredSummary(
                observed,
                new HealthSummary(0, 0, Map.of(), Map.of(), Map.of()),
                meals,
                Map.of(NapLevel.NGU_TOT, 14, NapLevel.NGU_IT, 4),
                Map.of(Emotion.HAPPY, 12, Emotion.CALM, 6),
                Map.of(ObservedBehavior.GIUP_DO_BAN, 3, ObservedBehavior.RUT_RE, 2),
                Map.of(Participation.TICH_CUC, 11, Participation.BINH_THUONG, 7),
                new GoalSummary(Map.of(
                        "MT3 – Trẻ nhảy bật xa", new GoalCount(5, 0, 0),
                        "MT7 – Trẻ kể chuyện theo tranh", new GoalCount(1, 2, 3))));
    }

    static PeriodCommentFacts empty() {
        return new PeriodCommentFacts(
                EvaluationPeriodType.MONTH,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                FULL_NAME,
                DOB,
                0,
                0,
                new StructuredSummary(
                        0,
                        new HealthSummary(0, 0, Map.of(), Map.of(), Map.of()),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        new GoalSummary(Map.of())),
                List.of(),
                List.of(),
                List.of());
    }
}
