package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Bảng đi chợ một ngày của một điểm trường: thực phẩm cần dùng kèm tiền, chia theo nhóm tuổi, và thu–chi trong ngày.
 *
 * @param status READY | DAY_OFF (thứ Bảy, Chủ nhật, ngày nghỉ của tuần) | NO_MENU (ngày chưa có thực đơn đã công bố)
 * @param basis nguồn số suất: CONFIRMED (mọi sổ báo ăn của ngày đã xác nhận, NUT-03) | FORECAST (sĩ số đang học × bữa
 *     được phục vụ, NUT-07); null khi status khác READY
 * @param confirmedAt lúc xác nhận sổ báo ăn gần nhất; chỉ có khi basis = CONFIRMED
 * @param groups nhóm tuổi có suất trong ngày, theo thứ tự hiển thị; {@code lines[].groups} và {@code totals.groups}
 *     cùng thứ tự này
 */
public record DayMarketResponse(
        Long campusId,
        LocalDate date,
        String status,
        String basis,
        Instant confirmedAt,
        List<DayMarketGroup> groups,
        List<DayMarketLine> lines,
        DayMarketTotals totals,
        List<DayMarketSessionMenu> menu) {

    /**
     * @param servings số suất tính tiền ăn của nhóm trong ngày
     * @param mealPrice tiền ăn một trẻ một ngày đang hiệu lực (NUT-17); null khi nhóm chưa có cấu hình
     * @param revenue servings × mealPrice; null khi chưa có mealPrice
     */
    public record DayMarketGroup(
            Long ageGroupId, String name, int servings, BigDecimal mealPrice, BigDecimal revenue) {}

    /**
     * @param storage FRESH (mua chợ, NUT-10) | DRY (xuất kho khô, NUT-11)
     * @param unitPrice đơn giá một đơn vị mua
     * @param quantity lượng thực dùng theo đơn vị gốc (NUT-07) = tổng các nhóm tuổi
     * @param orderQuantity lượng cần mua/xuất theo đơn vị gốc, làm tròn lên nguyên đơn vị mua khi đếm được (NUT-12)
     * @param cost tiền theo lượng thực dùng, làm tròn đồng = tổng các nhóm tuổi
     */
    public record DayMarketLine(
            StockFoodRef food,
            String storage,
            BigDecimal unitPrice,
            BigDecimal quantity,
            BigDecimal orderQuantity,
            BigDecimal cost,
            List<DayMarketGroupShare> groups) {}

    /** Phần của một nhóm tuổi trong một dòng thực phẩm; nhóm không dùng thực phẩm này có lượng và tiền bằng 0. */
    public record DayMarketGroupShare(Long ageGroupId, BigDecimal quantity, BigDecimal cost) {}

    /**
     * @param revenue tổng tiền ăn thu của các nhóm đã có giá; null khi chưa nhóm nào có giá
     * @param freshCost tiền hàng tươi mua chợ
     * @param dryCost tiền hàng khô xuất kho
     * @param balance revenue − cost; null khi còn nhóm có suất chưa có giá tiền ăn
     */
    public record DayMarketTotals(
            int servings,
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal freshCost,
            BigDecimal dryCost,
            BigDecimal balance,
            List<DayMarketGroupCost> groups) {}

    public record DayMarketGroupCost(Long ageGroupId, BigDecimal cost) {}

    /** Món của một bữa theo từng nhóm tuổi có suất bữa đó (NUT-13: món lấy theo khối của nhóm tuổi). */
    public record DayMarketSessionMenu(MealSession session, List<DayMarketGroupDishes> groups) {}

    public record DayMarketGroupDishes(Long ageGroupId, List<String> dishes) {}
}
