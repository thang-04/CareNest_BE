package com.carenest.service.meals.impl;

import com.carenest.dto.meals.MealPlanResponse.Issue;
import com.carenest.entity.meals.MealPlanWeek;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * NUT-08: điều kiện công bố theo bản cân đối. Tuần nháp: mỗi ngày học từ hôm nay phải có bản cân đối lần 1 còn khớp thực
 * đơn và đạt lượng, chất, tiền. Lỗi dinh dưỡng tính theo công thức món được thay bằng kết quả cân đối.
 */
@Component
@RequiredArgsConstructor
class DayBalanceGate {

    private static final int SCHOOL_DAYS = 5;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    // Các mã đánh giá theo công thức món: số thật của ngày nằm ở bản cân đối
    private static final Set<String> RECIPE_NUTRITION_CODES = Set.of(
            "NOT_BALANCED",
            "INCOMPLETE_DATA",
            "OVER_BUDGET",
            "UNDER_BUDGET",
            "NUTRIENT_GRAM_OFF",
            "MICRONUTRIENT_OFF",
            "MICRONUTRIENT_INCOMPLETE",
            "SESSION_KCAL_OFF");

    private final DayBalanceReader reader;
    private final Clock clock;

    /** Lỗi, cảnh báo của tuần: phần không thuộc dinh dưỡng giữ nguyên, cộng trạng thái cân đối từng ngày. */
    List<Issue> weekIssues(MealPlanWeek week, List<Issue> recipeIssues) {
        if (week == null) {
            return recipeIssues;
        }
        List<Issue> issues = new ArrayList<>(recipeIssues.stream()
                .filter(i -> !RECIPE_NUTRITION_CODES.contains(i.code()))
                .toList());
        LocalDate today = LocalDate.now(clock);
        for (int i = 0; i < SCHOOL_DAYS; i++) {
            LocalDate date = week.getWeekStart().plusDays(i);
            // Ngày đã qua không cân đối được nữa nên không đòi
            if (!date.isBefore(today)) {
                issues.addAll(dayIssues(date, week.isPublished()));
            }
        }
        issues.sort(Comparator.comparingInt((Issue i) -> severityOrder(i.severity()))
                .thenComparing(Issue::date, Comparator.nullsFirst(Comparator.naturalOrder())));
        return issues;
    }

    private List<Issue> dayIssues(LocalDate date, boolean published) {
        DayBalanceReader.Source source = reader.load(date, DayBalanceReader.ROUND_EXPECTED);
        // Ngày nghỉ không cân đối; ngày chưa có món đã có lỗi "chưa có món"
        if (source.items().isEmpty()) {
            return List.of();
        }
        boolean stale = reader.stale(source);
        if (published) {
            // Sửa ngoại lệ sau công bố chỉ cảnh báo (P-39); tuần công bố không có cân đối thì tính theo công thức
            return stale
                    ? List.of(
                            issue(MealPlanEvaluator.WARNING, "BALANCE_STALE", date, "thực đơn đã đổi, cần cân đối lại"))
                    : List.of();
        }
        if (source.saved() == null) {
            return List.of(issue(MealPlanEvaluator.BLOCKING, "BALANCE_MISSING", date, "chưa cân đối khẩu phần"));
        }
        if (stale) {
            return List.of(
                    issue(MealPlanEvaluator.BLOCKING, "BALANCE_STALE", date, "thực đơn đã đổi, cần cân đối lại"));
        }
        MealPlanEvaluator.Result result = reader.evaluate(source, reader.compose(source, true));
        if (DayBalanceReader.passed(source, result)) {
            return List.of();
        }
        return DayBalanceReader.problems(result).stream()
                .map(problem -> issue(MealPlanEvaluator.BLOCKING, "BALANCE_NOT_PASSED", date, problem))
                .toList();
    }

    private static Issue issue(String severity, String code, LocalDate date, String message) {
        return new Issue(severity, code, date, null, null, null, date.format(DAY) + " · " + message);
    }

    private static int severityOrder(String severity) {
        return switch (severity) {
            case MealPlanEvaluator.BLOCKING -> 0;
            case MealPlanEvaluator.WARNING -> 1;
            default -> 2;
        };
    }
}
