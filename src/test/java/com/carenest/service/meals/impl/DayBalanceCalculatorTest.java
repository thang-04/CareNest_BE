package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.service.meals.impl.DayBalanceCalculator.FoodMeta;
import com.carenest.service.meals.impl.DayBalanceCalculator.Line;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DayBalanceCalculatorTest {

    private static final long BE = 1L;
    private static final long LON = 2L;
    private static final long PORK = 10L;
    private static final long RICE = 11L;
    private static final long EGG = 12L;
    private static final BigDecimal STEP = new BigDecimal("0.1");

    private static final Map<Long, FoodMeta> FOODS = Map.of(
            // Thịt: tươi, mua theo kg, thải bỏ 20%
            PORK, new FoodMeta(new BigDecimal("20"), "kg", new BigDecimal("1000"), FoodStorage.FRESH),
            RICE, new FoodMeta(BigDecimal.ZERO, "kg", new BigDecimal("1000"), FoodStorage.DRY),
            // Trứng mua theo quả 55 g
            EGG, new FoodMeta(BigDecimal.ZERO, "quả", new BigDecimal("55"), FoodStorage.FRESH));

    private static final Map<Long, Integer> CHILDREN = Map.of(BE, 73, LON, 62);

    @Test
    @DisplayName("Làm tròn lên trên tổng toàn trường theo từng thực phẩm rồi tính ngược gam mỗi trẻ")
    void roundsSchoolTotalAndBackCalculatesGrams() {
        List<Line> lines = List.of(line(BE, PORK, "31.7"), line(LON, PORK, "40.3"));
        // (73 × 31,7 + 62 × 40,3) ÷ 0,8 = 6015,875 g ⇒ làm tròn lên 6,1 kg
        List<Line> rounded = DayBalanceCalculator.round(lines, CHILDREN, FOODS, STEP);

        assertThat(DayBalanceCalculator.purchaseTotal(rounded, CHILDREN, PORK, FOODS.get(PORK), STEP))
                .isEqualByComparingTo("6100");
        assertThat(rounded.get(0).grams()).isGreaterThan(new BigDecimal("31.7"));
        assertThat(rounded.get(0).baseGrams()).isEqualByComparingTo("31.7");
        // Tỷ lệ giữa hai nhóm tuổi giữ nguyên
        double ratio =
                rounded.get(1).grams().doubleValue() / rounded.get(0).grams().doubleValue();
        assertThat(ratio).isCloseTo(40.3 / 31.7, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("Làm tròn lại từ gam gốc nên bấm nhiều lần không cộng dồn")
    void roundingTwiceDoesNotInflate() {
        List<Line> lines = List.of(line(BE, PORK, "31.7"), line(LON, PORK, "40.3"));

        List<Line> once = DayBalanceCalculator.round(lines, CHILDREN, FOODS, STEP);
        List<Line> twice = DayBalanceCalculator.round(once, CHILDREN, FOODS, STEP);

        assertThat(twice).isEqualTo(once);
    }

    @Test
    @DisplayName("Hàng khô lấy từ kho không làm tròn; đơn vị đếm làm tròn lên nguyên đơn vị")
    void dryStaysAndCountableUsesWholeUnits() {
        List<Line> lines = List.of(line(BE, RICE, "33.3"), line(BE, EGG, "20"));

        List<Line> rounded = DayBalanceCalculator.round(lines, CHILDREN, FOODS, STEP);

        assertThat(rounded.get(0).grams()).isEqualByComparingTo("33.3");
        // 73 × 20 = 1460 g ⇒ 27 quả = 1485 g
        assertThat(DayBalanceCalculator.purchaseTotal(rounded, CHILDREN, EGG, FOODS.get(EGG), STEP))
                .isEqualByComparingTo("1485");
    }

    @Test
    @DisplayName("Không có trẻ hoặc gam bằng 0 thì giữ nguyên")
    void nothingToRoundKeepsGrams() {
        List<Line> lines = List.of(line(BE, PORK, "0"));

        assertThat(DayBalanceCalculator.round(lines, CHILDREN, FOODS, STEP)).isEqualTo(lines);
        assertThat(DayBalanceCalculator.round(List.of(line(BE, PORK, "30")), Map.of(), FOODS, STEP)
                        .getFirst()
                        .grams())
                .isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("Chia cho điểm trường theo phần dư lớn nhất: từng phần là bội của bước, cộng lại đúng tổng")
    void allocationAddsUpToTotal() {
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        weights.put(1L, new BigDecimal("3511.2"));
        weights.put(2L, new BigDecimal("2504.675"));

        Map<Long, BigDecimal> shares =
                DayBalanceCalculator.allocate(new BigDecimal("6100"), weights, new BigDecimal("100"));

        assertThat(shares.get(1L)).isEqualByComparingTo("3600");
        assertThat(shares.get(2L)).isEqualByComparingTo("2500");
        assertThat(shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("6100");
    }

    @Test
    @DisplayName("Tổng chưa làm tròn vẫn chia khớp tới 0,01; không có trọng số thì mọi phần bằng 0")
    void allocationWithoutStep() {
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        weights.put(1L, BigDecimal.ONE);
        weights.put(2L, BigDecimal.ONE);
        weights.put(3L, BigDecimal.ONE);

        Map<Long, BigDecimal> shares = DayBalanceCalculator.allocate(new BigDecimal("100"), weights, null);

        assertThat(shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
        assertThat(DayBalanceCalculator.allocate(BigDecimal.TEN, Map.of(1L, BigDecimal.ZERO), null)
                        .get(1L))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Sửa gam một thực phẩm của nhóm tuổi chia theo tỷ lệ giữa các bữa; sửa lượng mua tính ngược gam")
    void editsDistributeOverMeals() {
        List<Line> lines = List.of(
                new Line(BE, MealSession.LUNCH, 100L, PORK, new BigDecimal("30"), new BigDecimal("30")),
                new Line(BE, MealSession.AFTERNOON, 101L, PORK, new BigDecimal("10"), new BigDecimal("10")),
                line(LON, PORK, "40"));

        List<Line> edited = DayBalanceCalculator.setGrams(lines, BE, PORK, new BigDecimal("20"));

        assertThat(edited.get(0).grams()).isEqualByComparingTo("15");
        assertThat(edited.get(1).grams()).isEqualByComparingTo("5");
        assertThat(edited.get(0).baseGrams()).isEqualByComparingTo("15");
        assertThat(edited.get(2).grams()).isEqualByComparingTo("40");
        // Mua 2 kg cho 73 trẻ, thải bỏ 20% ⇒ mỗi trẻ ăn 2000 × 0,8 ÷ 73
        assertThat(DayBalanceCalculator.gramsForPurchase(new BigDecimal("2000"), 73, new BigDecimal("20")))
                .isEqualByComparingTo("21.917808");
    }

    @Test
    @DisplayName("Dòng đang 0 gam mà sửa thì chia đều giữa các bữa")
    void editFromZeroSplitsEvenly() {
        List<Line> lines = List.of(
                new Line(BE, MealSession.LUNCH, 100L, PORK, BigDecimal.ZERO, BigDecimal.ZERO),
                new Line(BE, MealSession.AFTERNOON, 101L, PORK, BigDecimal.ZERO, BigDecimal.ZERO));

        List<Line> edited = DayBalanceCalculator.setGrams(lines, BE, PORK, new BigDecimal("10"));

        assertThat(edited).allSatisfy(l -> assertThat(l.grams()).isEqualByComparingTo("5"));
    }

    private static Line line(long ageGroupId, long foodId, String grams) {
        return new Line(ageGroupId, MealSession.LUNCH, 100L, foodId, new BigDecimal(grams), new BigDecimal(grams));
    }
}
