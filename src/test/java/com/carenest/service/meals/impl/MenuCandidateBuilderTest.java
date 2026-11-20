package com.carenest.service.meals.impl;

import static com.carenest.service.meals.impl.MenuFixtures.AFTERNOON;
import static com.carenest.service.meals.impl.MenuFixtures.AG;
import static com.carenest.service.meals.impl.MenuFixtures.BANH;
import static com.carenest.service.meals.impl.MenuFixtures.CA;
import static com.carenest.service.meals.impl.MenuFixtures.CHUOI;
import static com.carenest.service.meals.impl.MenuFixtures.COM;
import static com.carenest.service.meals.impl.MenuFixtures.DAU;
import static com.carenest.service.meals.impl.MenuFixtures.DISHES;
import static com.carenest.service.meals.impl.MenuFixtures.GROUP;
import static com.carenest.service.meals.impl.MenuFixtures.LUNCH_CARB;
import static com.carenest.service.meals.impl.MenuFixtures.LUNCH_MAIN;
import static com.carenest.service.meals.impl.MenuFixtures.SLOTS;
import static com.carenest.service.meals.impl.MenuFixtures.SUA;
import static com.carenest.service.meals.impl.MenuFixtures.THIT;
import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.FoodInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bước 2: lọc cứng món ứng viên theo ô (NUT-05, NUT-09, NUT-24). */
class MenuCandidateBuilderTest {

    @Test
    @DisplayName("AC-1: mỗi ô chỉ nhận món đúng loại, đủ định lượng cho mọi nhóm tuổi của khối")
    void candidatesMatchSlotTypeAndCompleteData() {
        Map<String, List<Long>> candidates = MenuCandidateBuilder.build(
                DISHES.values(), List.of(GROUP), SLOTS, Set.of(), MenuContext.Signals.NONE, 15);

        assertThat(candidates.get(LUNCH_CARB.key())).containsExactly(COM);
        assertThat(candidates.get(LUNCH_MAIN.key())).containsExactly(THIT, CA, DAU);
        assertThat(candidates.get(AFTERNOON.key())).containsExactly(SUA, CHUOI, BANH);
    }

    @Test
    @DisplayName("Món thiếu định lượng cho một nhóm tuổi của khối hoặc thực phẩm thiếu số liệu ⇒ không là ứng viên")
    void incompleteDishesAreExcluded() {
        AgeGroupInfo other =
                new AgeGroupInfo(4L, "Mẫu giáo nhỡ (giả)", MenuBlock.KINDERGARTEN, List.of(MealSession.LUNCH));
        FoodInfo noKcal = new FoodInfo(
                9L,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                FoodOrigin.PLANT,
                null,
                null,
                null,
                List.of());
        DishInfo bothGroups = new DishInfo(
                20L,
                "Cơm hai nhóm (giả)",
                DishType.CARB,
                Map.of(
                        AG,
                        DISHES.get(COM).byAgeGroup().get(AG),
                        4L,
                        DISHES.get(COM).byAgeGroup().get(AG)));
        DishInfo missingData = new DishInfo(
                21L,
                "Xôi (giả)",
                DishType.CARB,
                Map.of(
                        AG,
                        List.of(new Portion(noKcal, BigDecimal.TEN)),
                        4L,
                        List.of(new Portion(noKcal, BigDecimal.TEN))));
        List<DishInfo> dishes = new ArrayList<>(DISHES.values());
        dishes.add(bothGroups);
        dishes.add(missingData);

        Map<String, List<Long>> candidates = MenuCandidateBuilder.build(
                dishes, List.of(GROUP, other), List.of(LUNCH_CARB), Set.of(), MenuContext.Signals.NONE, 15);

        // Cơm chỉ có định lượng nhóm 3 ⇒ loại; xôi thiếu kcal ⇒ loại
        assertThat(candidates.get(LUNCH_CARB.key())).containsExactly(20L);
    }

    @Test
    @DisplayName("Món đã dùng 2 tuần trước xếp sau; cắt còn tối đa candidate-limit món")
    void recentDishesLastAndLimited() {
        Map<String, List<Long>> candidates = MenuCandidateBuilder.build(
                DISHES.values(), List.of(GROUP), SLOTS, Set.of(THIT), MenuContext.Signals.NONE, 2);

        assertThat(candidates.get(LUNCH_MAIN.key())).containsExactly(CA, DAU);
    }

    @Test
    @DisplayName("Kho khô hết xếp sau; hợp mùa và trường đã dùng xếp trước (tín hiệu từ dữ liệu của trường)")
    void signalsOrderCandidates() {
        MenuContext.Signals signals = new MenuContext.Signals(Set.of(DAU), "Mùa đông", Set.of(CA), Set.of(THIT));

        Map<String, List<Long>> candidates =
                MenuCandidateBuilder.build(DISHES.values(), List.of(GROUP), SLOTS, Set.of(), signals, 15);

        assertThat(candidates.get(LUNCH_MAIN.key())).containsExactly(DAU, CA, THIT);
    }

    @Test
    @DisplayName("AC-17: món giống mong muốn hơn xếp trước; món không có điểm xếp sau mọi món có điểm; rỗng ⇒ như cũ")
    void similarityReordersCandidates() {
        Map<String, List<Long>> withoutVector = MenuCandidateBuilder.build(
                DISHES.values(), List.of(GROUP), SLOTS, Set.of(), MenuContext.Signals.NONE, 15);
        Map<String, List<Long>> withVector = MenuCandidateBuilder.build(
                DISHES.values(),
                List.of(GROUP),
                SLOTS,
                Set.of(),
                MenuContext.Signals.NONE,
                Map.of(DAU, 0.9, CA, 0.4),
                15);
        Map<String, List<Long>> emptyVector = MenuCandidateBuilder.build(
                DISHES.values(), List.of(GROUP), SLOTS, Set.of(), MenuContext.Signals.NONE, Map.of(), 15);

        assertThat(withoutVector.get(LUNCH_MAIN.key())).containsExactly(THIT, CA, DAU);
        assertThat(withVector.get(LUNCH_MAIN.key())).containsExactly(DAU, CA, THIT);
        assertThat(emptyVector).isEqualTo(withoutVector);
    }

    @Test
    @DisplayName(
            "AC-17: vector chỉ là khóa xếp sau tín hiệu cứng — món vừa dùng, hết kho vẫn xếp sau; không thêm món vào ô")
    void similarityNeverOverridesHardSignalsOrFilters() {
        MenuContext.Signals outOfStock = new MenuContext.Signals(Set.of(), null, Set.of(), Set.of(CA));
        Map<Long, Double> similarity = Map.of(THIT, 0.99, CA, 0.98, DAU, 0.1, COM, 0.99);

        Map<String, List<Long>> candidates = MenuCandidateBuilder.build(
                DISHES.values(), List.of(GROUP), SLOTS, Set.of(THIT), outOfStock, similarity, 15);

        // DAU (ít giống nhất) vẫn đứng trước THIT (vừa dùng) và CA (hết kho); cơm có điểm cao nhưng không vào ô mặn
        assertThat(candidates.get(LUNCH_MAIN.key())).containsExactly(DAU, CA, THIT);
        assertThat(candidates.get(LUNCH_MAIN.key())).doesNotContain(COM);
    }
}
