package com.carenest.service.meals.impl;

import static com.carenest.service.meals.impl.MenuFixtures.BANH;
import static com.carenest.service.meals.impl.MenuFixtures.CA;
import static com.carenest.service.meals.impl.MenuFixtures.CHUOI;
import static com.carenest.service.meals.impl.MenuFixtures.COM;
import static com.carenest.service.meals.impl.MenuFixtures.DAU;
import static com.carenest.service.meals.impl.MenuFixtures.DISHES;
import static com.carenest.service.meals.impl.MenuFixtures.SUA;
import static com.carenest.service.meals.impl.MenuFixtures.THIT;
import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.config.MenuAiProperties;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Điểm phương án 0–100 tất định: định mức 45, giá 20, đa dạng 25, cảnh báo 10. */
class MenuScoreTest {

    private static final MenuAiProperties.Weights WEIGHTS =
            MenuAiProperties.defaults().weights();

    @Test
    @DisplayName("Mọi ngày trong định mức, trong giá suất ⇒ đủ điểm định mức, giá; 2 món chính lặp ⇒ trừ 2 cảnh báo")
    void balancedWeekGetsFullNormCostWarnings() {
        MenuContext ctx = MenuFixtures.ctx();
        MenuGrid grid = rotating(ctx);

        MenuScore.Breakdown score = MenuScore.score(grid, grid.evaluate(ctx), ctx, WEIGHTS);

        assertThat(score.norm()).isEqualTo(45);
        assertThat(score.cost()).isEqualTo(20);
        assertThat(score.warnings()).isEqualTo(6);
    }

    @Test
    @DisplayName("Lệch định mức và lặp một bộ món cả tuần bị trừ điểm; cùng dữ liệu luôn cùng điểm")
    void offNormAndRepeatsLoseDeterministically() {
        MenuContext ctx = MenuFixtures.ctx();
        MenuGrid good = rotating(ctx);
        MenuGrid repeatedOff = MenuFixtures.sameEveryDay(ctx, COM, THIT, BANH);

        MenuScore.Breakdown goodScore = MenuScore.score(good, good.evaluate(ctx), ctx, WEIGHTS);
        MenuScore.Breakdown badScore = MenuScore.score(repeatedOff, repeatedOff.evaluate(ctx), ctx, WEIGHTS);

        assertThat(badScore.norm()).isLessThan(goodScore.norm());
        assertThat(badScore.variety()).isLessThan(goodScore.variety());
        assertThat(MenuScore.score(repeatedOff, repeatedOff.evaluate(ctx), ctx, WEIGHTS))
                .isEqualTo(badScore);
    }

    @Test
    @DisplayName("Dùng lại món của 2 tuần trước bị trừ điểm đa dạng")
    void recentDishesLowerVariety() {
        MenuContext fresh = MenuFixtures.ctx();
        MenuContext withRecent =
                MenuFixtures.ctx(DISHES, MenuFixtures.norm("300", "600"), Set.of(), Set.of(THIT, CA, DAU), Map.of());
        MenuGrid grid = rotating(fresh);

        int freshVariety =
                MenuScore.score(grid, grid.evaluate(fresh), fresh, WEIGHTS).variety();
        int recentVariety = MenuScore.score(grid, grid.evaluate(withRecent), withRecent, WEIGHTS)
                .variety();

        assertThat(recentVariety).isLessThan(freshVariety);
    }

    private static MenuGrid rotating(MenuContext ctx) {
        long[] mains = {THIT, CA, DAU, THIT, CA};
        long[] snacks = {SUA, SUA, SUA, CHUOI, SUA};
        MenuGrid grid = MenuGrid.empty();
        for (int i = 0; i < ctx.servedDates().size(); i++) {
            grid = grid.with(ctx.servedDates().get(i), MenuFixtures.LUNCH_CARB.key(), COM)
                    .with(ctx.servedDates().get(i), MenuFixtures.LUNCH_MAIN.key(), mains[i])
                    .with(ctx.servedDates().get(i), MenuFixtures.AFTERNOON.key(), snacks[i]);
        }
        return grid;
    }
}
