package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.service.meals.impl.DryStockOutlook.Line;
import com.carenest.service.meals.impl.DryStockOutlook.Lot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DryStockOutlookTest {
    private static final long RICE = 1L;
    private static final LocalDate MON = LocalDate.of(2026, 11, 9);

    private static BigDecimal qty(String value) {
        return new BigDecimal(value);
    }

    /** Thứ 2–6 mỗi ngày cần {@code perDay} gạo. */
    private static SortedMap<LocalDate, Map<Long, BigDecimal>> week(String perDay) {
        SortedMap<LocalDate, Map<Long, BigDecimal>> demand = new TreeMap<>();
        for (int i = 0; i < 5; i++) {
            demand.put(MON.plusDays(i), Map.of(RICE, qty(perDay)));
        }
        return demand;
    }

    private static Line rice(List<Line> lines) {
        return lines.stream().filter(l -> l.foodId() == RICE).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("NUT-11: tồn 25, mỗi ngày cần 10 ⇒ đủ thứ 2–3, thiếu 25 từ thứ 4")
    void cumulativeShortFromFirstMissingDay() {
        Line line = rice(DryStockOutlook.project(
                MON, List.of(new Lot(RICE, MON.plusMonths(6), qty("25"))), Map.of(), week("10")));

        assertThat(line.requiredQty()).isEqualByComparingTo("50");
        assertThat(line.availableQty()).isEqualByComparingTo("25");
        assertThat(line.shortQty()).isEqualByComparingTo("25");
        assertThat(line.shortFrom()).isEqualTo(MON.plusDays(2));
    }

    @Test
    @DisplayName("NUT-11: đủ kho ⇒ không thiếu, không có ngày bắt đầu thiếu")
    void enoughStock() {
        Line line = rice(DryStockOutlook.project(
                MON, List.of(new Lot(RICE, MON.plusMonths(6), qty("60"))), Map.of(), week("10")));

        assertThat(line.isShort()).isFalse();
        assertThat(line.shortFrom()).isNull();
    }

    @Test
    @DisplayName("NUT-11: lô hết hạn trước ngày ăn không dùng cho ngày đó và các ngày sau")
    void expiredLotSkippedFromItsExpiry() {
        List<Lot> lots = List.of(
                new Lot(RICE, MON.plusDays(1), qty("30")), // chỉ dùng được tới thứ 3
                new Lot(RICE, MON.plusMonths(6), qty("10")));

        Line line = rice(DryStockOutlook.project(MON, lots, Map.of(), week("10")));

        // Thứ 2, thứ 3 lấy lô sắp hết hạn (còn 10 bỏ phí); thứ 4 lấy lô dài hạn; thứ 5 thiếu
        assertThat(line.shortFrom()).isEqualTo(MON.plusDays(3));
        assertThat(line.shortQty()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("NUT-11, P-38: phần thiếu đang mở được bù trước, phần chưa bù tính là thiếu từ hôm nay")
    void openShortfallPaidFirst() {
        Line partly = rice(DryStockOutlook.project(
                MON, List.of(new Lot(RICE, MON.plusMonths(6), qty("25"))), Map.of(RICE, qty("5")), week("10")));
        assertThat(partly.availableQty()).isEqualByComparingTo("20");
        assertThat(partly.shortFrom()).isEqualTo(MON.plusDays(2));
        assertThat(partly.shortQty()).isEqualByComparingTo("30");

        Line unpaid = rice(DryStockOutlook.project(MON, List.of(), Map.of(RICE, qty("5")), new TreeMap<>()));
        assertThat(unpaid.shortQty()).isEqualByComparingTo("5");
        assertThat(unpaid.shortFrom()).isEqualTo(MON);
    }
}
