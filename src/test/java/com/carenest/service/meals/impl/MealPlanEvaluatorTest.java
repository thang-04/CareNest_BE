package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.meals.MealPlanResponse.Evaluation;
import com.carenest.dto.meals.MealPlanResponse.Issue;
import com.carenest.dto.meals.MealPlanResponse.NormRow;
import com.carenest.dto.meals.MealPlanResponse.SessionRow;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DayPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.FoodInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.ItemPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.Norm;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import com.carenest.service.meals.impl.MealPlanEvaluator.Range;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MealPlanEvaluatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final long AG3 = 3L;
    private static final AgeGroupInfo GROUP =
            new AgeGroupInfo(AG3, "Mẫu giáo bé", MenuBlock.KINDERGARTEN, List.of(MealSession.LUNCH));

    // Gạo: 350 kcal, P 7, L 1, G 77 /100 g; thịt: 250 kcal, P 20, L 18 /100 g
    private static final FoodInfo RICE = food(1L, "350", "7", "1", "77", FoodOrigin.PLANT, "20000", List.of());
    private static final FoodInfo PORK = food(2L, "250", "20", "18", "0", FoodOrigin.ANIMAL, "120000", List.of("Thịt"));
    // Một trẻ ag-3: cơm 60 g + thịt 40 g ⇒ 310 kcal, P 12.2 g (15.7%), L 7.8 g (22.6%), G 46.2 g (59.6%)
    private static final DishInfo COM =
            new DishInfo(10L, "Cơm", DishType.CARB, Map.of(AG3, List.of(new Portion(RICE, new BigDecimal("60")))));
    private static final DishInfo THIT =
            new DishInfo(11L, "Thịt rim", DishType.MAIN, Map.of(AG3, List.of(new Portion(PORK, new BigDecimal("40")))));
    private static final Map<Long, DishInfo> DISHES = Map.of(10L, COM, 11L, THIT);

    @Test
    @DisplayName("NUT-24: ngày đạt định mức ⇒ OK, 6 chỉ số và % năng lượng như FE, công bố được")
    void balancedDayIsPublishable() {
        MealPlanEvaluator.Result result = MealPlanEvaluator.evaluate(
                List.of(lunch(MONDAY)), List.of(GROUP), DISHES, (g, d) -> norm("280", "340"));

        Evaluation evaluation = result.evaluations().getFirst();
        assertThat(evaluation.status()).isEqualTo("OK");
        assertThat(evaluation.totals().kcal()).isEqualByComparingTo("310");
        assertThat(evaluation.totals().protein()).isEqualByComparingTo("12.2");
        assertThat(evaluation.totals().animalProtein()).isEqualByComparingTo("8");
        assertThat(evaluation.totals().plantProtein()).isEqualByComparingTo("4.2");
        assertThat(row(evaluation, "protein").percent()).isEqualByComparingTo("15.7");
        assertThat(row(evaluation, "glucid").percent()).isEqualByComparingTo("59.6");
        assertThat(row(evaluation, "kcal").attainment()).isEqualTo(100);
        assertThat(row(evaluation, "animalProteinG").status()).isEqualTo("NA");
        assertThat(evaluation.sessions()).singleElement().satisfies(r -> {
            assertThat(r.kcal()).isEqualByComparingTo("310");
            assertThat(r.percent()).isNull();
            assertThat(r.status()).isEqualTo("NA");
        });
        assertThat(result.publishable()).isTrue();
    }

    @Test
    @DisplayName("NUT-24: định mức gam ĐV-TV ⇒ dòng gam có tỷ lệ ĐV/TV, % đạt; dưới khoảng gam ⇒ chặn nêu gam")
    void gramNormsAreEvaluatedWhenSet() {
        Norm norm = withGrams(norm("280", "340"), range("6", "10"), range("6", "10"), range("40", "50"));

        MealPlanEvaluator.Result result =
                MealPlanEvaluator.evaluate(List.of(lunch(MONDAY)), List.of(GROUP), DISHES, (g, d) -> norm);

        Evaluation evaluation = result.evaluations().getFirst();
        NormRow animal = row(evaluation, "animalProteinG");
        assertThat(animal.value()).isEqualByComparingTo("8");
        assertThat(animal.percent()).isEqualByComparingTo("65.6");
        assertThat(animal.target()).isEqualByComparingTo("8");
        assertThat(animal.attainment()).isEqualTo(100);
        assertThat(animal.status()).isEqualTo("OK");
        assertThat(row(evaluation, "plantProteinG").status()).isEqualTo("LOW");
        assertThat(row(evaluation, "glucidG").status()).isEqualTo("OK");
        assertThat(row(evaluation, "animalLipidG").status()).isEqualTo("NA");
        assertThat(evaluation.status()).isEqualTo("LOW");
        assertThat(result.issues())
                .filteredOn(i -> "NOT_BALANCED".equals(i.code()))
                .singleElement()
                .satisfies(
                        i -> assertThat(i.message()).contains("Đạm thực vật thấp hơn định mức (4.2 g so với 6–10 g)"));
        assertThat(result.publishable()).isFalse();
    }

    @Test
    @DisplayName("NUT-25: % năng lượng từng bữa lệch định mức ⇒ cảnh báo theo bữa, không chặn công bố")
    void sessionShareOutOfRangeWarns() {
        AgeGroupInfo twoMeals = new AgeGroupInfo(
                AG3, "Mẫu giáo bé", MenuBlock.KINDERGARTEN, List.of(MealSession.MORNING, MealSession.LUNCH));
        DayPlan day = new DayPlan(
                MONDAY,
                false,
                List.of(
                        new ItemPlan(MealSession.MORNING, MenuBlock.KINDERGARTEN, 10L),
                        new ItemPlan(MealSession.LUNCH, MenuBlock.KINDERGARTEN, 11L)));
        Norm base = norm("280", "340");
        Norm norm = new Norm(
                base.mealPrice(),
                base.kcal(),
                base.proteinPct(),
                base.lipidPct(),
                base.glucidPct(),
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Map.of(MealSession.MORNING, range("20", "25"), MealSession.LUNCH, range("10", "20")),
                new BigDecimal("700"));

        MealPlanEvaluator.Result result =
                MealPlanEvaluator.evaluate(List.of(day), List.of(twoMeals), DISHES, (g, d) -> norm);

        Evaluation evaluation = result.evaluations().getFirst();
        // Mẫu số là nhu cầu cả ngày 700 kcal, không phải 310 kcal trường phục vụ: sáng 210 = 30%, trưa 100 = 14.3%
        assertThat(evaluation.sessions())
                .extracting(SessionRow::session, SessionRow::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MealSession.MORNING, "HIGH"),
                        org.assertj.core.groups.Tuple.tuple(MealSession.LUNCH, "OK"));
        assertThat(evaluation.sessions().getFirst().percent()).isEqualByComparingTo("30");
        assertThat(evaluation.sessions().get(1).percent()).isEqualByComparingTo("14.3");
        Norm noNeed = new Norm(
                norm.mealPrice(),
                norm.kcal(),
                norm.proteinPct(),
                norm.lipidPct(),
                norm.glucidPct(),
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                norm.sessionKcalPct(),
                null);
        assertThat(MealPlanEvaluator.evaluate(List.of(day), List.of(twoMeals), DISHES, (g, d) -> noNeed)
                        .evaluations()
                        .getFirst()
                        .sessions())
                .extracting(SessionRow::status)
                .containsOnly("NA");
        assertThat(evaluation.status()).isEqualTo("OK");
        assertThat(result.issues())
                .filteredOn(i -> "SESSION_KCAL_OFF".equals(i.code()))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.severity()).isEqualTo("WARNING");
                    assertThat(i.message()).contains("bữa sáng 30% nhu cầu năng lượng cả ngày (định mức 20–25%)");
                });
        assertThat(result.publishable()).isTrue();
    }

    @Test
    @DisplayName("NUT-08: vượt định mức năng lượng ⇒ lỗi chặn nêu ngày, nhóm tuổi, chỉ số")
    void kcalAboveNormBlocksWithDayAndGroup() {
        MealPlanEvaluator.Result result = MealPlanEvaluator.evaluate(
                List.of(lunch(MONDAY)), List.of(GROUP), DISHES, (g, d) -> norm("200", "300"));

        assertThat(result.evaluations().getFirst().status()).isEqualTo("HIGH");
        assertThat(result.publishable()).isFalse();
        Issue issue = result.issues().getFirst();
        assertThat(issue.severity()).isEqualTo("BLOCKING");
        assertThat(issue.code()).isEqualTo("NOT_BALANCED");
        assertThat(issue.date()).isEqualTo(MONDAY);
        assertThat(issue.ageGroupId()).isEqualTo(AG3);
        assertThat(issue.message()).contains("09/11", "Mẫu giáo bé", "Năng lượng cao hơn định mức", "310");
    }

    @Test
    @DisplayName("NUT-08 + NUT-24: chưa đặt định mức ⇒ chặn một lần cho nhóm tuổi")
    void missingNormBlocksOncePerGroup() {
        MealPlanEvaluator.Result result = MealPlanEvaluator.evaluate(
                List.of(lunch(MONDAY), lunch(MONDAY.plusDays(1))), List.of(GROUP), DISHES, (g, d) -> null);

        assertThat(result.evaluations()).extracting(Evaluation::status).containsOnly("NA");
        assertThat(result.issues())
                .filteredOn(i -> "NORM_NOT_SET".equals(i.code()))
                .singleElement()
                .extracting(Issue::severity)
                .isEqualTo("BLOCKING");
    }

    @Test
    @DisplayName("NUT-24: món thiếu định lượng cho nhóm tuổi ⇒ chưa đủ dữ liệu, chặn công bố")
    void missingQuantityIsIncomplete() {
        DishInfo noQuantity = new DishInfo(12L, "Canh", DishType.SOUP, Map.of());
        DayPlan day = new DayPlan(
                MONDAY,
                false,
                List.of(
                        new ItemPlan(MealSession.LUNCH, MenuBlock.KINDERGARTEN, 10L),
                        new ItemPlan(MealSession.LUNCH, MenuBlock.KINDERGARTEN, 12L)));

        MealPlanEvaluator.Result result = MealPlanEvaluator.evaluate(
                List.of(day), List.of(GROUP), Map.of(10L, COM, 12L, noQuantity), (g, d) -> norm("280", "340"));

        assertThat(result.evaluations().getFirst().status()).isEqualTo("INCOMPLETE");
        assertThat(result.issues()).extracting(Issue::code).contains("INCOMPLETE_DATA");
        assertThat(result.publishable()).isFalse();
    }

    @Test
    @DisplayName("NUT-08: bữa khối phục vụ chưa có món ⇒ chặn; ngày nghỉ không kiểm")
    void emptySessionBlocksAndOffDayIsSkipped() {
        DayPlan empty = new DayPlan(MONDAY, false, List.of());
        DayPlan off = new DayPlan(MONDAY.plusDays(1), true, List.of());

        MealPlanEvaluator.Result result =
                MealPlanEvaluator.evaluate(List.of(empty, off), List.of(GROUP), DISHES, (g, d) -> norm("280", "340"));

        assertThat(result.evaluations())
                .singleElement()
                .extracting(Evaluation::status)
                .isEqualTo("NO_DISHES");
        assertThat(result.issues())
                .filteredOn(i -> "NO_DISHES".equals(i.code()))
                .singleElement()
                .satisfies(i -> assertThat(i.message()).contains("bữa trưa chưa có món"));
        assertThat(result.publishable()).isFalse();
    }

    @Test
    @DisplayName("NUT-19, NUT-20: vượt giá suất và món chính lặp chỉ cảnh báo, vẫn công bố được")
    void overBudgetAndRepeatedMainDishAreWarnings() {
        MealPlanEvaluator.Result result = MealPlanEvaluator.evaluate(
                List.of(lunch(MONDAY), lunch(MONDAY.plusDays(2))),
                List.of(GROUP),
                DISHES,
                (g, d) -> norm("280", "340", "1000"));

        // Giá/trẻ = 20000/1000 × 60 + 120000/1000 × 40 = 6000đ
        assertThat(result.evaluations().getFirst().cost()).isEqualByComparingTo("6000");
        assertThat(result.issues())
                .extracting(Issue::severity, Issue::code)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("WARNING", "OVER_BUDGET"),
                        org.assertj.core.groups.Tuple.tuple("WARNING", "DISH_REPEATED"),
                        org.assertj.core.groups.Tuple.tuple("INFO", "ALLERGENS"));
        assertThat(result.publishable()).isTrue();
    }

    private static DayPlan lunch(LocalDate date) {
        return new DayPlan(
                date,
                false,
                List.of(
                        new ItemPlan(MealSession.LUNCH, MenuBlock.KINDERGARTEN, 10L),
                        new ItemPlan(MealSession.LUNCH, MenuBlock.KINDERGARTEN, 11L)));
    }

    private static Norm norm(String kcalMin, String kcalMax) {
        return norm(kcalMin, kcalMax, "50000");
    }

    private static Norm norm(String kcalMin, String kcalMax, String mealPrice) {
        return new Norm(
                new BigDecimal(mealPrice),
                range(kcalMin, kcalMax),
                range("13", "20"),
                range("20", "30"),
                range("50", "65"),
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Map.of(),
                null);
    }

    private static Norm withGrams(Norm base, Range animalProtein, Range plantProtein, Range glucid) {
        return new Norm(
                base.mealPrice(),
                base.kcal(),
                base.proteinPct(),
                base.lipidPct(),
                base.glucidPct(),
                animalProtein,
                plantProtein,
                Range.NONE,
                Range.NONE,
                glucid,
                base.sessionKcalPct(),
                base.dailyKcalNeed());
    }

    private static Range range(String min, String max) {
        return new Range(new BigDecimal(min), new BigDecimal(max));
    }

    private static FoodInfo food(
            Long id,
            String kcal,
            String protein,
            String lipid,
            String glucid,
            FoodOrigin origin,
            String unitPrice,
            List<String> allergens) {
        return new FoodInfo(
                id,
                new BigDecimal(kcal),
                new BigDecimal(protein),
                new BigDecimal(lipid),
                new BigDecimal(glucid),
                origin,
                new BigDecimal(unitPrice),
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                allergens);
    }

    private static NormRow row(Evaluation evaluation, String key) {
        return evaluation.rows().stream()
                .filter(r -> r.key().equals(key))
                .findFirst()
                .orElseThrow();
    }
}
