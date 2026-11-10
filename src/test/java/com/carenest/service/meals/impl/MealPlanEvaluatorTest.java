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
import com.carenest.utils.enums.Micronutrient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
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

    // Gạo 60 g + thịt 40 g: canxi 18 + 2,8 = 20,8; sắt 0,78 + 0,38 = 1,16; B1 0,06 + 0,36 = 0,42; thịt thiếu photpho
    private static final FoodInfo RICE_MICRO = microFood(
            RICE,
            Map.of(
                    Micronutrient.CALCIUM, "30",
                    Micronutrient.PHOSPHORUS, "104",
                    Micronutrient.IRON, "1.3",
                    Micronutrient.VITAMIN_A, "0",
                    Micronutrient.VITAMIN_B1, "0.10",
                    Micronutrient.VITAMIN_B2, "0.03",
                    Micronutrient.VITAMIN_PP, "1.6",
                    Micronutrient.VITAMIN_C, "0"));
    private static final FoodInfo PORK_MICRO = microFood(
            PORK,
            Map.of(
                    Micronutrient.CALCIUM, "7",
                    Micronutrient.IRON, "0.96",
                    Micronutrient.VITAMIN_A, "2",
                    Micronutrient.VITAMIN_B1, "0.90",
                    Micronutrient.VITAMIN_B2, "0.18",
                    Micronutrient.VITAMIN_PP, "4.4",
                    Micronutrient.VITAMIN_C, "1"));
    private static final Map<Long, DishInfo> DISHES_MICRO = Map.of(
            10L,
            new DishInfo(
                    10L, "Cơm", DishType.CARB, Map.of(AG3, List.of(new Portion(RICE_MICRO, new BigDecimal("60"))))),
            11L,
            new DishInfo(
                    11L,
                    "Thịt rim",
                    DishType.MAIN,
                    Map.of(AG3, List.of(new Portion(PORK_MICRO, new BigDecimal("40"))))));

    @Test
    @DisplayName(
            "NUT-26: vi chất OK/LOW/HIGH/NA/INCOMPLETE từng dòng; thực phẩm thiếu số liệu ⇒ tổng null, chưa đủ dữ liệu")
    void micronutrientRowsCoverEveryStatus() {
        Norm norm = withMicro(
                norm("280", "340"),
                Map.of(
                        Micronutrient.CALCIUM, range("15", "25"),
                        Micronutrient.IRON, new Range(new BigDecimal("2"), null),
                        Micronutrient.VITAMIN_B1, range("0.1", "0.3"),
                        Micronutrient.PHOSPHORUS, range("50", "100")));

        MealPlanEvaluator.Result result =
                MealPlanEvaluator.evaluate(List.of(lunch(MONDAY)), List.of(GROUP), DISHES_MICRO, (g, d) -> norm);

        Evaluation evaluation = result.evaluations().getFirst();
        assertThat(evaluation.totals().calcium()).isEqualByComparingTo("20.8");
        assertThat(evaluation.totals().iron()).isEqualByComparingTo("1.16");
        assertThat(evaluation.totals().vitaminB1()).isEqualByComparingTo("0.42");
        assertThat(evaluation.totals().phosphorus()).isNull();
        NormRow calcium = row(evaluation, "calcium");
        assertThat(calcium.status()).isEqualTo("OK");
        assertThat(calcium.percent()).isNull();
        assertThat(calcium.optional()).isTrue();
        assertThat(calcium.min()).isEqualByComparingTo("15");
        assertThat(calcium.max()).isEqualByComparingTo("25");
        assertThat(calcium.target()).isEqualByComparingTo("20");
        assertThat(calcium.attainment()).isEqualTo(104);
        NormRow iron = row(evaluation, "iron");
        assertThat(iron.status()).isEqualTo("LOW");
        assertThat(iron.max()).isNull();
        assertThat(iron.target()).isEqualByComparingTo("2");
        assertThat(iron.attainment()).isEqualTo(58);
        NormRow b1 = row(evaluation, "vitaminB1");
        assertThat(b1.status()).isEqualTo("HIGH");
        assertThat(b1.target()).isEqualByComparingTo("0.2");
        assertThat(b1.attainment()).isEqualTo(210);
        NormRow phosphorus = row(evaluation, "phosphorus");
        assertThat(phosphorus.status()).isEqualTo("INCOMPLETE");
        assertThat(phosphorus.value()).isNull();
        assertThat(phosphorus.attainment()).isNull();
        NormRow vitaminC = row(evaluation, "vitaminC");
        assertThat(vitaminC.status()).isEqualTo("NA");
        assertThat(vitaminC.min()).isNull();
        assertThat(vitaminC.target()).isNull();
        assertThat(vitaminC.value()).isEqualByComparingTo("0.4");
        assertThat(evaluation.rows())
                .extracting(NormRow::key)
                .containsSubsequence(
                        "glucidG",
                        "calcium",
                        "phosphorus",
                        "iron",
                        "vitaminA",
                        "vitaminB1",
                        "vitaminB2",
                        "vitaminPp",
                        "vitaminC");
        // Vi chất lệch hoặc thiếu số liệu chỉ báo, không đổi trạng thái ngày, không chặn công bố
        assertThat(evaluation.status()).isEqualTo("OK");
        assertThat(result.issues())
                .filteredOn(i -> "MICRONUTRIENT_INCOMPLETE".equals(i.code()))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.severity()).isEqualTo("INFO");
                    assertThat(i.message()).contains("Photpho");
                });
        assertThat(result.issues()).extracting(Issue::code).doesNotContain("INCOMPLETE_DATA", "NOT_BALANCED");
        assertThat(result.issues())
                .filteredOn(i -> "BLOCKING".equals(i.severity()))
                .isEmpty();
        assertThat(result.publishable()).isTrue();
    }

    @Test
    @DisplayName(
            "NUT-26: vi chất thấp / cao hơn định mức ⇒ một cảnh báo nêu đơn vị, không chặn; max trống thì không có HIGH")
    void micronutrientOutOfRangeBlocksAndMissingMaxNeverHigh() {
        Norm norm = withMicro(
                norm("280", "340"),
                Map.of(
                        Micronutrient.IRON, new Range(new BigDecimal("2"), null),
                        Micronutrient.VITAMIN_B1, range("0.1", "0.3"),
                        Micronutrient.CALCIUM, new Range(new BigDecimal("5"), null)));

        MealPlanEvaluator.Result result =
                MealPlanEvaluator.evaluate(List.of(lunch(MONDAY)), List.of(GROUP), DISHES_MICRO, (g, d) -> norm);

        Evaluation evaluation = result.evaluations().getFirst();
        // Canxi 20,8 gấp hơn 4 lần mức tối thiểu 5 vẫn đạt vì không có trần
        assertThat(row(evaluation, "calcium").status()).isEqualTo("OK");
        assertThat(row(evaluation, "calcium").attainment()).isEqualTo(416);
        assertThat(row(evaluation, "phosphorus").status()).isEqualTo("NA");
        assertThat(row(evaluation, "iron").status()).isEqualTo("LOW");
        assertThat(row(evaluation, "vitaminB1").status()).isEqualTo("HIGH");
        assertThat(evaluation.status()).isEqualTo("OK");
        assertThat(result.issues())
                .filteredOn(i -> "MICRONUTRIENT_OFF".equals(i.code()))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.severity()).isEqualTo("WARNING");
                    assertThat(i.message())
                            .contains(
                                    "Sắt thấp hơn định mức (1.16 mg so với tối thiểu 2 mg)",
                                    "Vitamin B1 cao hơn định mức (0.42 mg so với 0.1–0.3 mg)");
                });
        assertThat(result.issues()).extracting(Issue::code).doesNotContain("NOT_BALANCED");
        assertThat(result.publishable()).isTrue();
    }

    @Test
    @DisplayName(
            "NUT-26: chưa đặt định mức vi chất ⇒ dòng NA tùy chọn, không đổi trạng thái ngày dù thực phẩm thiếu số liệu")
    void unsetMicronutrientNormsDoNotChangeDayStatus() {
        MealPlanEvaluator.Result withData = MealPlanEvaluator.evaluate(
                List.of(lunch(MONDAY)), List.of(GROUP), DISHES_MICRO, (g, d) -> norm("280", "340"));
        MealPlanEvaluator.Result noData = MealPlanEvaluator.evaluate(
                List.of(lunch(MONDAY)), List.of(GROUP), DISHES, (g, d) -> norm("280", "340"));

        for (MealPlanEvaluator.Result result : List.of(withData, noData)) {
            Evaluation evaluation = result.evaluations().getFirst();
            assertThat(evaluation.status()).isEqualTo("OK");
            assertThat(result.publishable()).isTrue();
            assertThat(evaluation.rows())
                    .filteredOn(r -> r.key().equals("calcium") || r.key().equals("vitaminC"))
                    .hasSize(2)
                    .allSatisfy(r -> {
                        assertThat(r.status()).isEqualTo("NA");
                        assertThat(r.optional()).isTrue();
                    });
        }
        assertThat(noData.evaluations().getFirst().totals().calcium()).isNull();
        assertThat(withData.evaluations().getFirst().totals().calcium()).isEqualByComparingTo("20.8");
    }

    private static Norm withMicro(Norm base, Map<Micronutrient, Range> micro) {
        return new Norm(
                base.mealPrice(),
                base.kcal(),
                base.proteinPct(),
                base.lipidPct(),
                base.glucidPct(),
                base.animalProteinG(),
                base.plantProteinG(),
                base.animalLipidG(),
                base.plantLipidG(),
                base.glucidG(),
                base.sessionKcalPct(),
                base.dailyKcalNeed(),
                micro);
    }

    private static FoodInfo microFood(FoodInfo base, Map<Micronutrient, String> values) {
        Map<Micronutrient, BigDecimal> micro = new EnumMap<>(Micronutrient.class);
        values.forEach((nutrient, value) -> micro.put(nutrient, new BigDecimal(value)));
        return new FoodInfo(
                base.id(),
                base.kcal(),
                base.protein(),
                base.lipid(),
                base.glucid(),
                base.origin(),
                base.unitPrice(),
                base.purchaseUnitSize(),
                base.wastePct(),
                base.allergens(),
                micro);
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
